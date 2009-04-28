package naga.eventmachine;

import naga.NIOService;

import java.io.IOException;
import java.util.Date;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * EventMachine is a simple event service for driving asynchronous and delayed tasks
 * together with the a Naga NIOService.
 * <p>
 * Creating and starting an event machine:
 * <pre>
 * EventMachine em = new EventMachine();
 * // Start our event machine thread:
 * em.start();
 * </pre>
 * Delayed execution:
 * <pre>
 * em.executeLater(new Runnable() {
 *   public void run()
 *   {
 *      // Code here will execute after 1 second on the nio thread.
 *   }
 * }, 1000);
 * </pre>
 * Asynchronous execution, i.e. putting a task from another thread
 * to be executed on the EventMachine thread.
 * <pre>
 * em.asyncExecute(new Runnable() {
 *   public void run()
 *   {
 *      // Code here will be executed on the nio thread.
 *   }
 * });
 * </pre>
 * It is possible to cancel scheduled tasks:
 * <pre>
 * // Schedule an event
 * DelayedEvent event = em.executeLater(new Runnable()
 *   public void run()
 *   {
 *      // Code to run in 1 minute.
 *   }
 * }, 60000);
 * // Cancel the event before it is executed.
 * event.cancel();
 * </pre>
 *
 * @author Christoffer Lerno
 */
public class EventMachine
{
	private final NIOService m_service;
	private final Queue<DelayedAction> m_queue;
	private Thread m_runThread;
	private volatile ExceptionObserver m_observer;

	/**
	 * Creates a new EventMachine with an embedded NIOService.
	 *
	 * @throws IOException if we fail to set up the internal NIOService.
	 */
	public EventMachine() throws IOException
	{
		m_service = new NIOService();
		m_queue = new PriorityBlockingQueue<DelayedAction>();
		m_observer = ExceptionObserver.DEFAULT;
		m_runThread = null;
	}

	/**
	 * Execute a runnable on the Event/NIO thread.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @param runnable the runnable to execute on the server thread as soon as possible,
	 */
	public void asyncExecute(Runnable runnable)
	{
		executeLater(runnable, 0);
	}

	/**
	 * Execute a runnable on the Event/NIO thread after a delay.
	 * <p>
	 * This is the primary way to execute delayed events, typically time-outs and similar
	 * behaviour.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @param runnable the runnable to execute after the given delay.
	 * @param msDelay the delay until executing this runnable.
	 * @return the delayed event created to execute later. This can be used
	 * to cancel the event.
	 */
	public DelayedEvent executeLater(Runnable runnable, long msDelay)
	{
		return queueAction(runnable, msDelay + System.currentTimeMillis());
	}

	/**
	 * Creates and queuest a delayed action for execution at a certain time.
	 *
	 * @param runnable the runnable to execute at the given time.
	 * @param time the time date when this runnable should execute.
	 * @return the delayed action created and queued.
	 */
	private DelayedAction queueAction(Runnable runnable, long time)
	{
		DelayedAction action = new DelayedAction(runnable, time);
		m_queue.add(action);
		m_service.wakeup();
		return action;
	}
	/**
	 * Execute a runnable on the Event/NIO thread after at a certain time.
	 * <p/>
	 * This is the primary way to execute scheduled events.
	 * <p/>
	 * <em>This method is thread-safe.</em>
	 *
	 * @param runnable the runnable to execute at the given time.
	 * @param date the time date when this runnable should execute.
	 * @return the delayed event created to execute later. This can be used
	 * to cancel the event.
	 */
	public DelayedEvent executeAt(Runnable runnable, Date date)
	{
		return queueAction(runnable, date.getTime());
	}

	/**
	 * Sets the ExceptionObserver for this service.
	 * <p>
	 * The observer will receive all exceptions thrown by the underlying NIOService
	 * and by queued events.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @param observer the observer to use, may not be null.
	 * @throws NullPointerException if the observer is null.
	 */
	public void setObserver(ExceptionObserver observer)
	{
		if (observer == null) throw new NullPointerException();
		m_observer = observer;
	}

	/**
	 * Returns the time when the next scheduled event will execute.
	 *
	 * @return a long representing the date of the next event, or Long.MAX_VALUE if
	 * no event is scheduled.
	 */
	public long timeOfNextEvent()
	{
		DelayedAction action = m_queue.peek();
		return action == null ? Long.MAX_VALUE : action.getTime();
	}

	/**
	 * Causes the event machine to start running on a separate thread together with the
	 * NIOService.
	 * <p/>
	 * Note that the NIOService should not be called (using {@link naga.NIOService#selectNonBlocking()} and related
	 * functions) on another thread if the EventMachine is used.
	 */
	public synchronized void start()
	{
		if (m_runThread != null) throw new IllegalStateException("Service already running.");
		m_runThread = new Thread()
		{
			@Override
			public void run()
			{
				while (m_runThread == this)
				{
					try
					{
						select();
					}
					catch (Throwable e)
					{
						m_observer.notifyExceptionThrown(e);
					}
				}
			}
		};
		m_runThread.start();
	}

	/**
	 * Stops the event machine thread.
	 */
	public synchronized void stop()
	{
		if (m_runThread == null) throw new IllegalStateException("Service is not running.");
		m_runThread = null;
		m_service.wakeup();
	}

	/**
	 * Run all delayed events, then run select on the NIOService.
	 *
	 * @throws Throwable if any exception is thrown while executing events or handling IO.
	 */
	private void select() throws Throwable
	{
		// Run queued actions to be called
		while (timeOfNextEvent() <= System.currentTimeMillis())
		{
			runNextAction();
		}
		if (timeOfNextEvent() == Long.MAX_VALUE)
		{
			m_service.selectBlocking();
		}
		else
		{
			long delay = timeOfNextEvent() - System.currentTimeMillis();
			m_service.selectBlocking(Math.max(1, delay));
		}
	}

	/**
	 * Runs the next action in the queue.
	 */
	private void runNextAction()
	{
		m_queue.poll().run();
	}

	/**
	 * The current ExceptionObserver used by this service.
	 * <p>
	 * Will default to ExceptionObserver.DEFAULT if no observer was set.
	 *
	 * @return the current ExceptionObserver for this service.
	 */
	public ExceptionObserver getObserver()
	{
		return m_observer;
	}

	/**
	 * Returns the NIOService used by this event service.
	 *
	 * @return the NIOService that this event service uses.
	 */
	public NIOService getNIOService()
	{
		return m_service;
	}

	/**
	 * Return the current event service queue.
	 *
	 * @return a copy of the current queue.
	 */
	public Queue<DelayedAction> getQueue()
	{
		return new PriorityQueue<DelayedAction>(m_queue);
	}

	/**
	 * Return the current queue size.
	 *
	 * @return the number events in the event queue.
	 */
	public int getQueueSize()
	{
		return m_queue.size();
	}

}
