package puppet_queues;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedQueue<T> implements ProducerConsumerQueue<T> {

    private int capacity;
    private LinkedList<T> items;
    // FIXME: do we need a reentrant lock??
    private ReentrantLock lock;
    private Condition notFull;
    private Condition notEmpty;

    // FIXME: kinda silly to still call it a bounded queue in that case, I guess...
    public BoundedQueue() {
        this(0);
    }

    // FIXME: document that capacity < 0 means unbounded capacity
    public BoundedQueue(int capacity) {
        this.capacity = capacity;
        this.items = new LinkedList<T>();
        this.lock = new ReentrantLock();
        this.notFull = this.lock.newCondition();
        this.notEmpty = this.lock.newCondition();
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

        this.lock.lock();

        try {
            if (this.capacity > 0 && !this.waitWhileQueueSizeIs(this.notFull, this.capacity, nanosTimeout)) {
                return false;
            }

            this.items.addLast(item);
            this.notEmpty.signal();
            return true;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public T dequeue(long nanosTimeout) throws InterruptedException {
        this.lock.lock();

        try {
            if (!this.waitWhileQueueSizeIs(this.notEmpty, 0, nanosTimeout)) {
                return null;
            }

            T result = this.items.removeFirst();
            this.notFull.signal();
            return result;
        } finally {
            this.lock.unlock();
        }
    }

    // FIXME: javadoc?
    // waits at most nanosTimeout nanoseconds for the condition to trigger and for the
    // queue's size to not be equal to queueSize any more
    // if the timeout is strictly negative, it never times out
    // returns true iff that happens before the timeout
    private boolean waitWhileQueueSizeIs(Condition condition, int queueSize, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout > 0) {
            long remainingTime = nanosTimeout;

            while (remainingTime > 0 && this.items.size() == queueSize) {
                remainingTime = condition.awaitNanos(remainingTime);
            }

            return remainingTime > 0;
        } else if (nanosTimeout < 0) {
            // another good enough implementation could have been to just call replace
            // the timeout with Long.MAX_VALUE, which is 292+ years, and thus a good
            // enough approximation of forever
            while (this.items.size() == queueSize) {
                condition.await();
            }

            return true;
        } else {
            // timeout is 0
            return this.items.size() != queueSize;
        }
    }

    @Override
    public int size() {
        return this.items.size();
    }
}
