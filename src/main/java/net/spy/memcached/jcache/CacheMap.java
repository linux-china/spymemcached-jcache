package net.spy.memcached.jcache;

import javax.cache.Cache;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * cache map
 *
 * @author linux_china
 */
@SuppressWarnings({"unchecked", "NullableProblems"})
public class CacheMap<K, V> implements Map<K, V> {
    private Cache<K, V> cache;

    public CacheMap(Cache<K, V> cache) {
        this.cache = cache;
    }

    public int size() {
        return Integer.MAX_VALUE;
    }

    public boolean isEmpty() {
        return false;
    }

    public boolean containsKey(Object o) {
        return cache.containsKey((K) o);
    }

    public boolean containsValue(Object o) {
        throw new UnsupportedOperationException("containsValue not supported by Memcached");
    }

    public V get(Object o) {
        return cache.get((K) o);
    }

    public V put(K k, V v) {
        cache.put(k, v);
        return v;
    }

    public V remove(Object o) {
        return cache.getAndRemove((K) o);
    }

    public void putAll(Map<? extends K, ? extends V> map) {
        for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
    }

    public void clear() {
        cache.clear();
    }

    public Set<K> keySet() {
        throw new UnsupportedOperationException("iterator not supported by Memcached");
    }

    public Collection<V> values() {
        throw new UnsupportedOperationException("iterator not supported by Memcached");
    }

    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("iterator not supported by Memcached");
    }
}
