package puppet_queues;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Package-private class with the logic common to all implementations
 * of ProducerConsumerQueue.
 */
abstract class AbstractBoundedQueue<T> implements ProducerConsumerQueue<T> {
    final int capacity;

    /**
     * Creates a new bounded queue.
     * A negative capacity means that the queue is unbounded.
     *
     * @param capacity The queue's capacity
     */
    public AbstractBoundedQueue(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void enqueue(T item) throws InterruptedException {
        this.enqueue(item, -1);
    }

    @Override
    public T dequeue() throws InterruptedException {
        return this.dequeue(-1);
    }

    @Override
    public boolean enqueue(T item, long nanosTimeout) throws InterruptedException {
        if (item == null) {
            throw new IllegalArgumentException("cannot enqueue a null object");
        }

        return false;
    }

    /**
     * Should return the current size of the queue, in a potentially unsafe way
     * (i.e. doesn't need to be thread safe)
     *
     * @return The current size of the queue.
     */
    abstract int maybeUnsafeSize();

    /**
     * Waits at most nanosTimeout nanoseconds for the condition to trigger and for the
     * queue's size to not be equal to queueSize any more
     * If the timeout is strictly negative, it never times out
      returns
     * Assumes the queue is appropriately locked by the current thread
     *
     * @param condition
     * @param queueSize
     * @param nanosTimeout
     * @return true iff the size condition is satisfied before the timeout
     * @throws InterruptedException
     */
    boolean waitWhileQueueSizeIs(Condition condition, int queueSize, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout > 0) {
            long remainingTime = nanosTimeout;

            while (remainingTime > 0 && this.maybeUnsafeSize() == queueSize) {
                remainingTime = condition.awaitNanos(remainingTime);
            }

            return remainingTime > 0;
        } else if (nanosTimeout < 0) {
            // another good enough implementation could have been to just replace
            // the timeout with Long.MAX_VALUE, which is 292+ years, and thus a good
            // enough approximation of forever
            while (this.maybeUnsafeSize() == queueSize) {
                condition.await();
            }

            return true;
        } else {
            // timeout is 0
            return this.maybeUnsafeSize() != queueSize;
        }
    }

    interface VoidFunction {
        public void call();
    }

    void withLock(ReentrantLock lock, VoidFunction lambda) throws InterruptedException {
        lock.lockInterruptibly();

        try {
            lambda.call();
        } finally {
            lock.unlock();
        }
    }
}
