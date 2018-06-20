package puppet_queues;

/**
 * Main interface of this library. Defines the expected behaviors of FIFO, thread-safe bounded queues.
 *
 * All operations should be thread-safe.
 *
 * @param <T> The generic type of objects contained in the queues
 */
public interface ProducerConsumerQueue<T> {
    /**
     * Enqueues a new item into the queue, waiting indefinitely if the queue is full
     * until it can successfully carry out the operation.
     *
     * It is the caller's responsibility to handle InterruptedException exceptions
     * if the current thread is interrupted while waiting.
     *
     * Equivalent to enqueue(item, -1)
     *
     * @param item           The item to enqueue (can't be null)
     * @throws InterruptedException
     */
    public void enqueue(T item) throws InterruptedException;

    /**
     * Enqueues a new item into the queue, with a timeout.
     * Accepts a timeout (in nanoseconds) to limit the time spent waiting
     * for the operation to succeed (in case the queue is full).
     *
     * In particular, calling enqueue(item, 0) allows to return immediately
     * if the queue is full.
     *
     * @param item           The item to enqueue (can't be null)
     * @param nanosTimeout The timeout, in nanoseconds. A strictly negative timeout means no timeout.
     * @return             true iff the item was successfully enqueued
     * @throws InterruptedException
     */
    public boolean enqueue(T item, long nanosTimeout) throws InterruptedException;

    /**
     * Dequeues the oldest item from the queue, waiting indefinitely if the
     * queue is empty.
     *
     * Same as for enqueuing operations, it is the caller's responsibility
     * to handle InterruptedException exceptions if the current thread is
     * interrupted while waiting.
     *
     * Equivalent to dequeue(-1)
     *
     * @return The oldest item from the queue
     * @throws InterruptedException
     */
    public T dequeue() throws InterruptedException;

    /**
     * Dequeues the oldest item from the queue, with a timeout.
     * Accepts a timeout (in nanoseconds) to limit the time spent waiting
     * for the operation to succeed (in case the queue is empty).
     *
     * In particular, calling dequeue(0) allows to return immediately
     * if the queue is empty.
     *
     * @param nanosTimeout The timeout, in nanoseconds. A strictly negative timeout means no timeout.
     * @return                The oldest item from the queue; null iff the queue is empty
     * @throws InterruptedException
     */
    public T dequeue(long nanosTimeout) throws InterruptedException;

    /**
     * Returns the number of items currently in the queue.
     *
     * @return The current size of the queue.
     */
    public int size();
}
