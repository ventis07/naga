package naga;

import java.net.Socket;

/**
 * Interface for the NIOSocket, which is
 * an asynchronous facade to an underlying Socket.
 * <p>
 * The NIOSocket executes callbacks to a Socket observer
 * to react to incoming packets and other events.
 *
 * @author Christoffer Lerno
 */
public interface NIOSocket extends NIOAbstractSocket
{
	/**
	 * Write a packet of bytes asynchronously on this socket.
	 * <p>
	 * The bytes will be sent to the PacketWriter belonging to this
	 * socket for dispatch. However, if the queue is full (i.e. the new
	 * queue size would exceed <code>getMaxQueueSize()</code>),
	 * the packet is discarded and the method returns false.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @param packet the packet to send.
	 * @return true if the packet was queued, false if the queue limit
	 * was reached and the packet was thrown away.
	 */
	boolean write(byte[] packet);

	/**
	 * Return the total number of bytes read on this socket since
	 * it was opened.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @return the total number of bytes read on this socket.
	 */
	long getBytesRead();

	/**
	 * Return the total number of bytes written on this socket
	 * since it was opened.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @return the total number of bytes written on this socket.
	 */
	long getBytesWritten();

	/**
	 * Return the time this socket has been open.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @return the time this socket has been open in ms.
	 */
	long getTimeOpen();

	/**
	 * This method returns the number of bytes queued for dispatch.
	 * This size is compared against the maximum queue size to determine if additional packets
	 * will be refused or not.
	 * <p>
	 * However, this number does not include the packet currently waiting to be written.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @return the total size of the packets waiting to be dispatched, exluding the currently
	 * dispatching packet.
	 */
	long getWriteQueueSize();

	/**
	 * The current maximum queue size in bytes.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @return the current maximum queue size.
	 */
	int getMaxQueueSize();

	/**
	 * Sets the maximum number of bytes allowed in the queue for this socket. If this
	 * number is less than 1, the queue is unbounded.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 *
	 * @param maxQueueSize the new max queue size. A value less than 1 is an unbounded queue.
	 */
	void setMaxQueueSize(int maxQueueSize);

	/**
	 * Sets the packet reader for this socket.
	 *
	 * @param packetReader the packet reader to interpret the incoming byte stream.
	 */
	void setPacketReader(PacketReader packetReader);

	/**
	 * Sets the packet writer for this socket.
	 *
	 * @param packetWriter the packet writer to interpret the incoming byte stream.
	 */
	void setPacketWriter(PacketWriter packetWriter);

	/**
	 * Opens the socket for reads.
	 * <p>
	 * The socket observer will receive connects, disconnects and packets.
	 * If the socket was opened or disconnected before the observer was attached,
	 * the socket observer will still receive those callbacks.
	 * <p>
	 * <em>This method is thread-safe, but may only be called once.</em>
	 *
	 * @param socketObserver the observer to receive packets and be notified of connects/disconnects.
	 * @throws IllegalStateException if the method already has been called.
	 */
	void listen(SocketObserver socketObserver);


	/**
	 * Causes the socket to close after writing the current entries in the queue
	 * (consequent entries will be thrown away).
	 * <p>
	 * Also see <code>close()</code> if you want to immediately close the socket.
	 * <p>
	 * <em>This method is thread-safe.</em>
	 */
	void closeAfterWrite();

	/**
	 * Allows access to the underlying socket.
	 * <p>
	 * <em>Note that accessing streams or closing the socket will
	 * put this NIOSocket in an undefined state</em>
	 *
	 * @return return the underlying socket.
	 */
	Socket socket();
}
