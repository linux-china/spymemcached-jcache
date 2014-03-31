package net.spy.memcached.jcache;

import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.jcache.event.RICacheEntryEvent;
import net.spy.memcached.jcache.event.RICacheEntryListenerRegistration;
import net.spy.memcached.jcache.event.RICacheEventDispatcher;
import net.spy.memcached.jcache.management.MBeanServerRegistrationUtility;
import net.spy.memcached.jcache.management.RICacheMXBean;
import net.spy.memcached.jcache.management.RICacheStatisticsMXBean;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.*;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static javax.cache.event.EventType.CREATED;
import static javax.cache.event.EventType.REMOVED;


/**
 * Spy Cache
 *
 * @author linux_china
 */
@SuppressWarnings("unchecked")
public class SpyCache<K, V> implements Cache<K, V> {
    private CacheManager cacheManager;
    private MemcachedClient mClient;
    private String cacheName;
    private String seperator;
    private SpyMutableConfiguration<K, V> configuration;
    private final CopyOnWriteArrayList<RICacheEntryListenerRegistration<K, V>> listenerRegistrations;
    private final RICacheMXBean cacheMXBean;
    private final RICacheStatisticsMXBean statistics;
    private boolean isClosed;

    public SpyCache(CacheManager cacheManager, MemcachedClient mClient, String cacheName, String seperator, CompleteConfiguration<K, V> configuration) {
        this.cacheManager = cacheManager;
        this.mClient = mClient;
        this.cacheName = cacheName;
        this.seperator = seperator;
        this.configuration = new SpyMutableConfiguration<K, V>(configuration);
        this.cacheMXBean = new RICacheMXBean(this);
        this.statistics = new RICacheStatisticsMXBean(this);
        this.listenerRegistrations = new CopyOnWriteArrayList<RICacheEntryListenerRegistration<K, V>>();
        //establish all of the listeners
        for (CacheEntryListenerConfiguration<K, V> listenerConfiguration :
                this.configuration.getCacheEntryListenerConfigurations()) {
            createAndAddListener(listenerConfiguration);
        }
    }

    public void setManagementEnabled(boolean enabled) {
        if (enabled) {
            MBeanServerRegistrationUtility.registerCacheObject(this, MBeanServerRegistrationUtility.ObjectNameType.Configuration);
        } else {
            MBeanServerRegistrationUtility.unregisterCacheObject(this, MBeanServerRegistrationUtility.ObjectNameType.Configuration);
        }
        this.configuration.setManagementEnabled(enabled);
    }

    public void setStatisticsEnabled(boolean enabled) {
        if (enabled) {
            MBeanServerRegistrationUtility.registerCacheObject(this, MBeanServerRegistrationUtility.ObjectNameType.Statistics);
        } else {
            MBeanServerRegistrationUtility.unregisterCacheObject(this, MBeanServerRegistrationUtility.ObjectNameType.Statistics);
        }
        configuration.setStatisticsEnabled(enabled);
    }

    public RICacheMXBean getCacheMXBean() {
        return cacheMXBean;
    }

    public RICacheStatisticsMXBean getCacheStatisticsMXBean() {
        return statistics;
    }

    public V get(K key) {
        long start = configuration.isStatisticsEnabled() ? System.nanoTime() : 0;
        V value = (V) mClient.get(getCompositeKey(key));
        if (configuration.isStatisticsEnabled()) {
            statistics.addGetTimeNano(System.nanoTime() - start);
            if (value != null) {
                statistics.increaseCacheHits(1);
            } else {
                statistics.increaseCacheMisses(1);
            }
        }
        //load value from cache loader
        if (configuration.isReadThroughSupport()) {
            value = configuration.getCacheLoader().load(key);
            if (value != null) {
                put(key, value);
            }
        }
        return value;
    }

    public Map<K, V> getAll(Set<? extends K> keys) {
        Map<K, V> map = new HashMap<K, V>();
        for (K key : keys) {
            V v = get(key);
            if (v != null) {
                map.put(key, v);
            }
        }
        return map;
    }

    public boolean containsKey(K key) {
        return get(key) != null;
    }

    public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {
        if (configuration.getCacheLoader() == null) {
            if (completionListener != null) {
                completionListener.onCompletion();
            }
        } else {
            for (K key : keys) {
                if (key == null) {
                    throw new NullPointerException("keys contains a null");
                }
            }
            try {
                List<K> keysToLoad = new ArrayList<K>();
                for (K key : keys) {
                    if (replaceExistingValues || !containsKey(key)) {
                        keysToLoad.add(key);
                    }
                }
                Map<? extends K, ? extends V> loaded = configuration.getCacheLoader().loadAll(keysToLoad);
                for (Map.Entry<? extends K, ? extends V> entry : loaded.entrySet()) {
                    put(entry.getKey(), entry.getValue());
                }
                if (completionListener != null) {
                    completionListener.onCompletion();
                }
            } catch (Exception e) {
                if (completionListener != null) {
                    completionListener.onException(e);
                }
            }
        }
    }

    public void put(K key, V value) {
        //number of seconds since January 1, 1970
        long start = configuration.isStatisticsEnabled() ? System.nanoTime() : 0;
        mClient.set(getCompositeKey(key), getExpiredTimeStamp(), value);
        if (configuration.isStatisticsEnabled()) {
            statistics.addPutTimeNano(System.nanoTime() - start);
            statistics.increaseCachePuts(1);
        }
        //write through
        if (configuration.isWriteThroughSupport()) {
            configuration.getCacheWriter().write(new RIEntry<K, V>(this, key, value));
        }
        //fire updated event
        if (!listenerRegistrations.isEmpty()) {
            RICacheEventDispatcher<K, V> dispatcher = new RICacheEventDispatcher<K, V>();
            dispatcher.addEvent(CacheEntryUpdatedListener.class, new RICacheEntryEvent<K, V>(this, key, value, null, EventType.UPDATED));
            dispatcher.dispatch(listenerRegistrations);
        }
    }

    public V getAndPut(K key, V value) {
        V oldValue = get(key);
        put(key, value);
        return oldValue;
    }

    public void putAll(Map<? extends K, ? extends V> map) {
        for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public boolean putIfAbsent(K key, V value) {
        if (!containsKey(key)) {
            put(key, value);
            //raise "created" event
            if (!listenerRegistrations.isEmpty()) {
                RICacheEventDispatcher<K, V> dispatcher = new RICacheEventDispatcher<K, V>();
                dispatcher.addEvent(CacheEntryRemovedListener.class, new RICacheEntryEvent<K, V>(this, key, value, CREATED));
                dispatcher.dispatch(listenerRegistrations);
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean remove(K key) {
        long start = configuration.isStatisticsEnabled() ? System.nanoTime() : 0;
        mClient.delete(getCompositeKey(key));
        if (configuration.isStatisticsEnabled()) {
            statistics.addRemoveTimeNano(System.nanoTime() - start);
            statistics.increaseCacheRemovals(1);
        }
        //delete cache entry
        if (configuration.isWriteThrough() && this.configuration.getCacheWriter() != null) {
            configuration.getCacheWriter().delete(key);
        }
        //raise "remove" event
        if (!listenerRegistrations.isEmpty()) {
            RICacheEventDispatcher<K, V> dispatcher = new RICacheEventDispatcher<K, V>();
            dispatcher.addEvent(CacheEntryRemovedListener.class, new RICacheEntryEvent<K, V>(this, key, null, REMOVED));
            dispatcher.dispatch(listenerRegistrations);
        }
        return true;
    }

    public boolean remove(K key, V oldValue) {
        V value = get(key);
        if (value != null && value.equals(oldValue)) {
            remove(key);
            return true;
        } else {
            return false;
        }
    }

    public V getAndRemove(K key) {
        V value = get(key);
        if (value != null) {
            remove(key);
            return value;
        } else {
            return null;
        }
    }

    public boolean replace(K key, V oldValue, V newValue) {
        V value = get(key);
        return value != null && value.equals(oldValue) && replace(key, newValue);
    }

    public boolean replace(K key, V value) {
        //number of seconds since January 1, 1970
        long start = configuration.isStatisticsEnabled() ? System.nanoTime() : 0;
        OperationFuture<Boolean> future = mClient.replace(getCompositeKey(key), getExpiredTimeStamp(), value);
        boolean result = false;
        try {
            result = future.get();
            //write through
            if (configuration.isWriteThrough() && configuration.getCacheWriter() != null) {
                configuration.getCacheWriter().write(new RIEntry<K, V>(this, key, value));
            }
            //fire updated event
            if (!listenerRegistrations.isEmpty()) {
                RICacheEventDispatcher<K, V> dispatcher = new RICacheEventDispatcher<K, V>();
                dispatcher.addEvent(CacheEntryUpdatedListener.class, new RICacheEntryEvent<K, V>(this, key, value, null, EventType.UPDATED));
                dispatcher.dispatch(listenerRegistrations);
            }
        } catch (Exception ignore) {
        } finally {
            if (configuration.isStatisticsEnabled()) {
                statistics.addGetTimeNano(System.nanoTime() - start);
                if (result) {
                    statistics.increaseCachePuts(1);
                    statistics.increaseCacheHits(1);
                    statistics.addPutTimeNano(System.nanoTime() - start);
                } else {
                    statistics.increaseCacheMisses(1);
                }
            }
        }
        return result;
    }

    public V getAndReplace(K key, V value) {
        V oldValue = get(key);
        replace(key, value);
        return oldValue;
    }

    public void removeAll(Set<? extends K> keys) {
        for (K key : keys) {
            remove(key);
        }
    }

    public void removeAll() {
        throw new UnsupportedOperationException("remove all not supported by Memcached");
    }

    public void clear() {
        throw new UnsupportedOperationException("clear not supported by Memcached");
    }

    public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
        if (clazz.isAssignableFrom(this.getClass())) {
            return (C) this.configuration;
        }
        return null;
    }

    public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments) throws EntryProcessorException {
        V value = get(key);
        RIEntry<K, V> entry = new RIEntry<K, V>(this, key, value);
        return entryProcessor.process(entry, arguments);
    }

    public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
        Map<K, EntryProcessorResult<T>> map = new HashMap<K, EntryProcessorResult<T>>();
        for (K key : keys) {
            final T result = invoke(key, entryProcessor, arguments);
            if (result != null) {
                map.put(key, new EntryProcessorResult<T>() {
                    @Override
                    public T get() throws EntryProcessorException {
                        return result;
                    }
                });
            }
        }
        return map;
    }

    public String getName() {
        return this.cacheName;
    }

    public CacheManager getCacheManager() {
        return this.cacheManager;
    }

    public void close() {
        ((SpyCacheManager) cacheManager).releaseCache(this.cacheName);
        //disable statistics and management
        setStatisticsEnabled(false);
        setManagementEnabled(false);
        this.isClosed = true;
    }

    public boolean isClosed() {
        return this.isClosed;
    }

    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(this.getClass())) {
            return clazz.cast(this);
        } else if (clazz.equals(Map.class)) {
            return (T) new CacheMap<K, V>(this);
        } else if (clazz.equals(MemcachedClient.class)) {
            return (T) this.mClient;
        }
        throw new IllegalArgumentException("Unwapping to " + clazz + " is not a supported by this implementation");
    }

    public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        configuration.addCacheEntryListenerConfiguration(cacheEntryListenerConfiguration);
        createAndAddListener(cacheEntryListenerConfiguration);
    }

    public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        removeListener(cacheEntryListenerConfiguration);
    }

    public Iterator<Entry<K, V>> iterator() {
        throw new UnsupportedOperationException("iterator not supported by Memcached");
    }

    public String getCompositeKey(Object key) {
        return this.cacheName + this.seperator + key.toString();
    }

    public int getExpiredTimeStamp() {
        Duration expiryForCreation = configuration.getExpiryPolicy().getExpiryForCreation();
        if (expiryForCreation.isEternal()) {
            return 0;
        } else {
            return (int) (expiryForCreation.getAdjustedTime(System.currentTimeMillis()) / 1000);
        }
    }

    private void ensureOpen() {
        if (isClosed()) {
            throw new IllegalStateException("Cache operations can not be performed. The cache closed");
        }
    }

    //todo concurrency
    private void createAndAddListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration) {
        RICacheEntryListenerRegistration<K, V> registration = new RICacheEntryListenerRegistration<K, V>(listenerConfiguration);
        listenerRegistrations.add(registration);
    }

    //todo concurrency
    private void removeListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
        if (cacheEntryListenerConfiguration == null) {
            throw new NullPointerException("CacheEntryListenerConfiguration can't be null");
        }
        for (RICacheEntryListenerRegistration<K, V> listenerRegistration : listenerRegistrations) {
            if (cacheEntryListenerConfiguration.equals(listenerRegistration.getConfiguration())) {
                listenerRegistrations.remove(listenerRegistration);
                configuration.removeCacheEntryListenerConfiguration(cacheEntryListenerConfiguration);
            }
        }
    }
}
