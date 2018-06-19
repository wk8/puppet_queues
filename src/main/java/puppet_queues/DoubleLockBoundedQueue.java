package puppet_queues;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An implementation of the ProducerConsumerQueue interface. (ie a FIFO thread-safe bounded queue).
 *
 * Uses separate locks for add and remove operations, which makes it generally
 * more efficient than 'SingleLockBoundedQueue's.
 *
 * @param <T> The generic type of objects contained in the queues
 */
public class DoubleLockBoundedQueue<T> extends AbstractBoundedQueue<T> {

    // lock used when adding items to the queue
    private ReentrantLock addLock;
    // and everything it controls:
    private LinkedList<T> addedItems;
    private Condition notFull;

    // lock used when removing items from the queue
    private ReentrantLock removeLock;
    // and everything it controls
    private LinkedList<T> itemsToRemove;
    private Condition notEmpty;

    private AtomicInteger size;

    /**
     * Creates a new bounded queue.
     * A negative capacity means that the queue is unbounded.
     *
     * @param capacity The queue's capacity
     */
    public DoubleLockBoundedQueue(int capacity) {
        super(capacity);

        this.addLock = new ReentrantLock();
        this.addedItems = new LinkedList<T>();
        this.notFull = this.addLock.newCondition();

        this.removeLock = new ReentrantLock();
        this.itemsToRemove = new LinkedList<T>();
        this.notEmpty = this.removeLock.newCondition();

        this.size = new AtomicInteger();
    }

    /**
     * Equivalent to DoubleLockBoundedQueue(0)
     */
    public DoubleLockBoundedQueue() {
        this(0);
    }

    @Override
    public boolean enqueue(T item, long nanosTimeout) throws InterruptedException {
        super.enqueue(item, nanosTimeout);

        this.addLock.lockInterruptibly();

        int newSize;

        try {
            // note that 'waitWhileQueueSizeIs' calls 'maybeUnsafeSize'
            // that is okay here because the size can only be
            // over-estimated due to race conditions, never
            // under-estimated (since we do own the own the
            // lock needed to increment it)
            if (this.capacity > 0 && !this.waitWhileQueueSizeIs(this.notFull, this.capacity, nanosTimeout)) {
                return false;
            }

            newSize = this.size.incrementAndGet();

            this.addedItems.addLast(item);

            if (newSize < this.capacity) {
                this.notFull.signal();
            }
        } finally {
            this.addLock.unlock();
        }

        // same comment as above for the size
        // worst that can happen here is locking the consumers' lock
        // for no reason
        if (newSize == 1) {
            this.withLock(this.removeLock, () -> this.notEmpty.signal());
        }

        return true;
    }

    @Override
    public T dequeue(long nanosTimeout) throws InterruptedException {
        this.removeLock.lockInterruptibly();

        int newSize;
        T result;

        try {
            // same comment as above for the use of 'maybeUnsafeSize'
            if (!this.waitWhileQueueSizeIs(this.notEmpty, 0, nanosTimeout)) {
                return null;
            }

            if (this.itemsToRemove.isEmpty()) {
                this.withLock(this.addLock, () -> {
                    if (this.itemsToRemove.isEmpty()) {
                        this.itemsToRemove = this.addedItems;
                        this.addedItems = new LinkedList<T>();
                    }
                });
            }

            newSize = this.size.decrementAndGet();

            result = this.itemsToRemove.removeFirst();

            if (newSize > 0) {
                this.notEmpty.signal();
            }
        } finally {
            this.removeLock.unlock();
        }

        if (newSize == this.capacity - 1) {
            this.withLock(this.addLock, () -> this.notFull.signal());
        }

        return result;
    }

    @Override
    int maybeUnsafeSize() {
        return this.size.get();
    }

    @Override
    public int size() {
        return this.maybeUnsafeSize();
    }
}
