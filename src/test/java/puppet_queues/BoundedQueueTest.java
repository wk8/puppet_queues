package puppet_queues;

import org.junit.Test;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

public class BoundedQueueTest {
    private static final long ONE_MILLISECOND = (long) 1e6; // in nanoseconds

    // tests the behavior of bounded queues with just one thread
    @Test public void basicSingleThreadedTestWithBoundedCapacity() throws InterruptedException {
        BoundedQueue<Integer> queue = new BoundedQueue<Integer>(3);

        // the queue should be empty
        assertNull(queue.dequeue(0));
        assertEquals(0, queue.size());

        // let's add and remove a single value
        Integer twelve = Integer.valueOf(12);
        assertTrue(queue.enqueue(twelve, 0));
        assertEquals(1, queue.size());
        assertEquals(twelve, queue.dequeue(0));
        assertNull(queue.dequeue(0));
        assertEquals(0, queue.size());

        // now let's fill the queue
        Integer ten = Integer.valueOf(10), eight = Integer.valueOf(12), one = Integer.valueOf(1);
        Integer[] values = {eight, ten, twelve};
        for (Integer v : values) {
            queue.enqueue(v);
        }
        assertEquals(3, queue.size());
        // enqueuing another value should fail
        assertFalse(queue.enqueue(one, 0));
        assertEquals(3, queue.size());
        assertOperationTakesAtLeast(() -> !queue.enqueue(one, 10 * ONE_MILLISECOND), 5 * ONE_MILLISECOND);
        assertEquals(3, queue.size());

        // now let's unroll the queue
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], queue.dequeue(0));
            assertEquals(2 - i, queue.size());
        }
        assertOperationTakesAtLeast(() -> queue.dequeue(10 * ONE_MILLISECOND) == null, 5 * ONE_MILLISECOND);
        assertEquals(0, queue.size());
    }

    @Test public void basicSingleThreadedTestWithUnboundedCapacity() throws InterruptedException {
        BoundedQueue<Integer> queue = new BoundedQueue<Integer>();

        for (int i = 0; i < 10000; i++) {
            assertTrue(queue.enqueue(Integer.valueOf(i), 0));
        }
        for (int i = 0; i < 10000; i++) {
            assertEquals(i, queue.dequeue().intValue());
        }
    }

    @Test public void cannotEnqueueANullObject() throws InterruptedException {
        BoundedQueue<Integer> queue = new BoundedQueue<Integer>();

        int caught = 0;

        try {
            queue.enqueue(null);
        } catch(IllegalArgumentException e) {
            caught++;
        }

        try {
            queue.enqueue(null, 0);
        } catch(IllegalArgumentException e) {
            caught++;
        }

        assertEquals(2, caught);
    }

    // tests the behavior of bounded queues being used by multiple producers and consumers
    @Test public void basicMultiThreadedTest() throws InterruptedException {
        int[] queueSizes = {0, 1, 2, 5, 10, 100};
        int[] threadCounts = {1, 2, 5, 10};

        for (int queueSize : queueSizes) {
            for (int producerCount : threadCounts) {
                for (int consumerCount : threadCounts) {
                    this.runBasicMultiThreadedTest(queueSize, producerCount, consumerCount);
                }
            }
        }
    }

    // how many items do we push & consume in the multi-threaded test below?
    // see runBasicMultiThreadedTest below
    private static final int MULTI_THREADED_TEST_TOTAL_SIZE = 10000;

    // also used in runBasicMultiThreadedTest below
    private class ConsumerThread extends Thread {
        private int threadId;
        private BoundedQueue<Integer> queue;
        private int[] consumed;
        private AtomicInteger consumedCount;
        private BoundedQueueTest test;

        public ConsumerThread(int threadId, BoundedQueue<Integer> queue, int[] consumed, AtomicInteger consumedCount, BoundedQueueTest test) {
            super();

            this.threadId = threadId;
            this.queue = queue;
            this.consumed = consumed;
            this.consumedCount = consumedCount;
            this.test = test;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int value = this.queue.dequeue().intValue();

                    // this value should not have been seen by any other consumer before
                    assertEquals(0, this.consumed[value]);
                    // concurrent access of the 'consumed' array is fine here since
                    // we only never write more than once to any location in the array
                    this.consumed[value] = this.threadId;

                    if (this.consumedCount.incrementAndGet() == MULTI_THREADED_TEST_TOTAL_SIZE) {
                        // we're done, notify the test object
                        this.test.synchronizedNotify();
                    }
                }
            } catch (InterruptedException e) {
                // this should only happen when we're entirely done
                assertEquals(MULTI_THREADED_TEST_TOTAL_SIZE, consumedCount.get());
            }
        }
    }

    private void runBasicMultiThreadedTest(int queueSize, int producerCount, int consumerCount) throws InterruptedException {
        BoundedQueue<Integer> queue = new BoundedQueue<Integer>(queueSize);

        // keeps track of which consumer thread has consumed which item
        int[] consumed = new int[MULTI_THREADED_TEST_TOTAL_SIZE];
        AtomicInteger consumedCount = new AtomicInteger();

        // let's start the consumers first
        Thread[] consumers = new Thread[consumerCount];
        for (int i = 0; i < consumerCount; i++) {
            consumers[i] = new ConsumerThread(i + 1, queue, consumed, consumedCount, this);
            consumers[i].start();
        }

        // and then the producers
        Thread[] producers = new Thread[producerCount];
        for (int i = 0; i < producerCount; i++) {
            // each is responsible for a given range
            int start = i * MULTI_THREADED_TEST_TOTAL_SIZE / producerCount;
            int end = (i + 1) * MULTI_THREADED_TEST_TOTAL_SIZE / producerCount;

            producers[i] = new Thread() {
                public void run() {
                    try {
                        for (int j = start; j < end; j++) {
                            queue.enqueue(j);
                        }
                    } catch (InterruptedException e) {
                        // shouldn't happen
                        e.printStackTrace();
                        assertTrue("producer thread got interrupted", false);
                    }
                }
            };
            producers[i].start();
        }

        // and then wait for all items to be consumed
        while (consumedCount.get() != MULTI_THREADED_TEST_TOTAL_SIZE) {
            this.synchronizedWait();
        }

        // first let's shut down all the consumers
        for (Thread consumer : consumers) {
            consumer.interrupt();
            consumer.join();
        }

        // all the producers should have shut down by now
        for (Thread producer : producers) {
            assertFalse(producer.isAlive());
        }

        // and then real interesting part: let's check that all the items have
        // properly produced & consumed
        for (int i = 0; i < MULTI_THREADED_TEST_TOTAL_SIZE; i++) {
            int consumerId = consumed[i];
            assertNotEquals(0, consumerId);
        }
    }

    synchronized private void synchronizedWait() throws InterruptedException {
        this.wait();
    }

    synchronized private void synchronizedNotify() {
        this.notify();
    }

    // sadly can't use java.util.function.BooleanSupplier since InterruptedException is
    // a checked exception - so it wouldn't compile
    private interface BooleanSupplierWithInterruptedException {
        public boolean getAsBoolean() throws InterruptedException;
    }

    // asserts that calling operation takes at least nanos nanoseconds, and that the result is true
    private void assertOperationTakesAtLeast(BooleanSupplierWithInterruptedException operation, long nanos) throws InterruptedException {
        long startedAt = System.nanoTime();
        boolean result = operation.getAsBoolean();
        long endedAt = System.nanoTime();

        assertTrue(endedAt - startedAt >= nanos);
        assertTrue(result);
    }
}
