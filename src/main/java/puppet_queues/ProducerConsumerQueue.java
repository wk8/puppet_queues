package puppet_queues;

public interface ProducerConsumerQueue<T> {
    public void enqueue(T item) throws InterruptedException;
    // FIXME: document new functions, as well as why I chose to keep "throws" statement
    public boolean enqueue(T item, long nanosTimeout) throws InterruptedException;

    public T dequeue() throws InterruptedException;
    public T dequeue(long nanosTimeout) throws InterruptedException;

    public int size();
}
