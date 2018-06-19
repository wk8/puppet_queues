package puppet_queues;

import java.lang.reflect.InvocationTargetException;

public class BoundedQueueFactory<Q extends ProducerConsumerQueue<Integer>> {
    private Class<Q> klass;

    public BoundedQueueFactory(Class<Q> klass) {
        this.klass = klass;
    }

    public Q newQueue() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        return this.klass.getDeclaredConstructor().newInstance();
    }

    public Q newQueue(int queueSize) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        return this.klass.getDeclaredConstructor(int.class).newInstance(queueSize);
    }

    public String type() {
        return this.klass.getName();
    }
}
