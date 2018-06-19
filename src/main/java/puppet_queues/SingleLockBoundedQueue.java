package puppet_queues;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SingleLockBoundedQueue<T> extends AbstractBoundedQueue<T> {

    private LinkedList<T> items;
    // FIXME: do we need a reentrant lock??
    private ReentrantLock lock;
    private Condition notFull;
    private Condition notEmpty;

    // FIXME: document that capacity < 0 means unbounded capacity
    public SingleLockBoundedQueue(int capacity) {
        super(capacity);

        this.items = new LinkedList<T>();
        this.lock = new ReentrantLock();
        this.notFull = this.lock.newCondition();
        this.notEmpty = this.lock.newCondition();
    }

    public SingleLockBoundedQueue() {
        this(0);
    }

    @Override
    public boolean enqueue(T item, long nanosTimeout) throws InterruptedException {
        super.enqueue(item, nanosTimeout);

        this.lock.lockInterruptibly();

        try {
            if (this.capacity > 0 && !this.waitWhileQueueSizeIs(this.notFull, this.capacity, nanosTimeout)) {
                return false;
            }

            this.items.addLast(item);
            this.notEmpty.signal();

            if (this.items.size() < this.capacity) {
                this.notFull.signal();
            }

            return true;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public T dequeue(long nanosTimeout) throws InterruptedException {
        this.lock.lockInterruptibly();

        try {
            if (!this.waitWhileQueueSizeIs(this.notEmpty, 0, nanosTimeout)) {
                return null;
            }

            T result = this.items.removeFirst();
            this.notFull.signal();

            if (this.items.size() > 0) {
                this.notEmpty.signal();
            }

            return result;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    protected int maybeUnsafeSize() {
        return this.items.size();
    }

    @Override
    public int size() {
        this.lock.lock();

        try {
            return this.maybeUnsafeSize();
        } finally {
            this.lock.unlock();
        }
    }
}
