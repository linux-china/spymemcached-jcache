package net.spy.memcached.jcache;

import net.spy.memcached.MemcachedClient;
import net.spy.memcached.jcache.spi.SpyCachingProvider;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;

/**
 * spy cache manager
 *
 * @author linux_china
 */
@SuppressWarnings("unchecked")
public class SpyCacheManager implements CacheManager {
    private CachingProvider cachingProvider;
    private URI uri;
    private Properties properties;
    private boolean isClosed;
    private MemcachedClient mClient;
    private String namespaceSeperator = "$";
    private final Map<String, SpyCache<?, ?>> caches = new HashMap<String, SpyCache<?, ?>>();
    private final WeakReference<ClassLoader> classLoaderReference;

    public SpyCacheManager(CachingProvider cachingProvider, URI uri, ClassLoader classLoader, Properties properties) throws Exception {
        this.cachingProvider = cachingProvider;
        this.uri = uri;
        this.properties = properties;
        List<InetSocketAddress> servers = new ArrayList<InetSocketAddress>();
        servers.add(new InetSocketAddress(uri.getHost(), uri.getPort()));
        Map<String, String> params = parseQuery(uri.getQuery());
        if (params.containsKey("peer")) {
            String peer = params.get("peer");
            String[] parts = peer.split(";");
            for (String part : parts) {
                String[] temp = part.split(":");
                servers.add(new InetSocketAddress(temp[0], Integer.valueOf(temp[1])));
            }
        }
        if (params.containsKey("seperator")) {
            this.namespaceSeperator = params.get("seperator");
        }
        mClient = new MemcachedClient(servers);
        this.classLoaderReference = new WeakReference<ClassLoader>(classLoader);
        this.isClosed = false;
    }

    public CachingProvider getCachingProvider() {
        return this.cachingProvider;
    }

    public URI getURI() {
        return this.uri;
    }

    public Properties getProperties() {
        return this.properties;
    }

    public ClassLoader getClassLoader() {
        return this.getClass().getClassLoader();
    }

    public <K, V, C extends Configuration<K, V>> Cache<K, V> createCache(String cacheName, C configuration) throws IllegalArgumentException {
        if (isClosed()) {
            throw new IllegalStateException();
        }
        if (!validateCacheName(cacheName)) {
            throw new IllegalArgumentException("Cache name:" + cacheName + " is illegal, please use \\w+ as cache name.");
        }
        SpyCache cache = caches.get(cacheName);
        if (cache == null) {
            cache = new SpyCache(this, this.mClient, cacheName, namespaceSeperator, (CompleteConfiguration) configuration);
            caches.put(cacheName, cache);
        }
        return cache;
    }

    public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType) {
        if (this.caches.containsKey(cacheName)) {
            SpyCache<?, ?> cache = caches.get(cacheName);
            Configuration<?, ?> configuration = cache.getConfiguration(Configuration.class);
            if (configuration.getKeyType() != null && configuration.getKeyType().equals(keyType)) {
                if (configuration.getValueType() != null && configuration.getValueType().equals(valueType)) {
                    return (Cache<K, V>) cache;
                } else {
                    throw new ClassCastException("Incompatible cache value types specified, expected " + configuration.getValueType() + " but " + valueType + " was specified");
                }
            } else {
                throw new ClassCastException("Incompatible cache key types specified, expected " + configuration.getKeyType() + " but " + keyType + " was specified");
            }
        }
        MutableConfiguration<K, V> configuration = new MutableConfiguration<K, V>();
        if (keyType != null && valueType != null) {
            configuration.setTypes(keyType, valueType);
        }
        return createCache(cacheName, configuration);
    }

    public <K, V> Cache<K, V> getCache(String cacheName) {
        return getCache(cacheName, null, null);
    }

    public Iterable<String> getCacheNames() {
        return caches.keySet();
    }

    public void destroyCache(String cacheName) {
        synchronized (caches) {
            SpyCache<?, ?> cache = caches.remove(cacheName);
            if (cache != null) {
                cache.close();
            }
        }
    }

    public void enableManagement(String cacheName, boolean enabled) {
        if (isClosed()) {
            throw new IllegalStateException();
        }
        ((SpyCache) caches.get(cacheName)).setManagementEnabled(enabled);
    }

    public void enableStatistics(String cacheName, boolean enabled) {
        if (isClosed()) {
            throw new IllegalStateException();
        }
        if (caches.containsKey(cacheName)) {
            ((SpyCache) caches.get(cacheName)).setStatisticsEnabled(enabled);
        }
    }

    public void close() {
        if (!isClosed()) {
            //first releaseCacheManager the CacheManager from the CacheProvider so that
            //future requests for this CacheManager won't return this one
            ((SpyCachingProvider) cachingProvider).releaseCacheManager(getURI(), classLoaderReference.get());
            ArrayList<Cache<?, ?>> cacheList;
            synchronized (caches) {
                cacheList = new ArrayList<Cache<?, ?>>(caches.values());
                caches.clear();
            }
            for (Cache<?, ?> cache : cacheList) {
                try {
                    cache.close();
                } catch (Exception ignore) {
                }
            }
            //release memcached connection
            mClient.shutdown();
            this.isClosed = true;
        }

    }

    public boolean isClosed() {
        return this.isClosed;
    }

    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(this.getClass())) {
            return clazz.cast(this);
        }
        if (clazz.equals(MemcachedClient.class)) {
            return (T) this.mClient;
        }
        throw new IllegalArgumentException("Unwapping to " + clazz + " is not a supported by this implementation");
    }

    /**
     * Releases the Cache with the specified name from being managed by
     * this CacheManager.
     *
     * @param cacheName the name of the Cache to releaseCacheManager
     */
    public void releaseCache(String cacheName) {
        synchronized (caches) {
            if (caches.containsKey(cacheName)) {
                caches.remove(cacheName);
            }
        }
    }

    /**
     * parse query string
     *
     * @param query query
     * @return query map
     */
    public static Map<String, String> parseQuery(String query) {
        Map<String, String> parameters = new HashMap<String, String>();
        if (query != null && query.contains("=")) {
            String[] parts1 = query.split("&");
            for (String part : parts1) {
                if (part.contains("=")) {
                    String[] parts2 = part.split("=", 2);
                    parameters.put(parts2[0], parts2[1]);
                }
            }
        }
        return parameters;
    }

    /**
     * validate cache names
     *
     * @param cacheName cache name
     * @return legal mark
     */
    public boolean validateCacheName(String cacheName) {
        return cacheName.matches("\\w+");
    }
}
