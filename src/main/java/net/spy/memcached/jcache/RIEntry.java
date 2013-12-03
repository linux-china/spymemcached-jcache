package net.spy.memcached.jcache;

import net.spy.memcached.MemcachedClient;

import javax.cache.Cache;
import javax.cache.processor.MutableEntry;
import java.util.Map;

/**
 * A cache entry implementation.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values*
 * @author Brian Oliver
 * @author Greg Luck
 */
public class RIEntry<K, V> implements MutableEntry<K, V> {
    private final Cache<K, V> cache;
    private final K key;
    private final V value;
    private final V oldValue;

    /**
     * Constructor
     */
    public RIEntry(Cache<K, V> cache, K key, V value) {
        this.cache = cache;
        this.key = key;
        this.value = value;
        this.oldValue = null;
    }

    public RIEntry(Cache<K, V> cache, K key, V value, V oldValue) {
        this.cache = cache;
        this.key = key;
        this.value = value;
        this.oldValue = oldValue;
    }

    public boolean exists() {
        return oldValue != null;
    }

    public void remove() {
        cache.remove(key);
    }

    public void setValue(V value) {
        cache.put(key, value);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public K getKey() {
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V getValue() {
        return value;
    }

    /**
     * @return the old value, if any
     */
    public V getOldValue() {
        return oldValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(this.getClass())) {
            return clazz.cast(this);
        } else {
            throw new IllegalArgumentException("Class " + clazz + " is unknown to this implementation");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || ((Object) this).getClass() != o.getClass()) return false;
        RIEntry<?, ?> e2 = (RIEntry<?, ?>) o;
        return this.getKey().equals(e2.getKey()) &&
                this.getValue().equals(e2.getValue()) &&
                (this.oldValue == null && e2.oldValue == null ||
                        this.getOldValue().equals(e2.getOldValue()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return getKey().hashCode();
    }
}

