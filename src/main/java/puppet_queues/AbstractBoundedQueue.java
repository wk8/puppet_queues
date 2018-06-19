package puppet_queues;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractBoundedQueue<T> implements ProducerConsumerQueue<T> {
    protected int capacity;

    // FIXME: document that capacity < 0 means unbounded capacity
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
            // FIXME: document that nulls are not acceptable
            throw new IllegalArgumentException("cannot enqueue a null object");
        }

        return false;
    }

    // FIXME: doc?
    protected abstract int maybeUnsafeSize();

    // FIXME: javadoc?
    // waits at most nanosTimeout nanoseconds for the condition to trigger and for the
    // queue's size to not be equal to queueSize any more
    // if the timeout is strictly negative, it never times out
    // returns true iff that happens before the timeout
    // assumes the queue is fully locked by the current thread
    protected boolean waitWhileQueueSizeIs(Condition condition, int queueSize, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout > 0) {
            long remainingTime = nanosTimeout;

            while (remainingTime > 0 && this.maybeUnsafeSize() == queueSize) {
                remainingTime = condition.awaitNanos(remainingTime);
            }

            return remainingTime > 0;
        } else if (nanosTimeout < 0) {
            // another good enough implementation could have been to just call replace
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

    protected interface VoidFunction {
        public void call();
    }

    protected void withLock(ReentrantLock lock, VoidFunction lambda) throws InterruptedException {
        lock.lockInterruptibly();

        try {
            lambda.call();
        } finally {
            lock.unlock();
        }
    }
}
