package net.spy.memcached.jcache.spi;

import net.spy.memcached.jcache.SpyCacheManager;

import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.util.HashMap;
import java.util.Properties;
import java.util.WeakHashMap;

/**
 * spy caching provider
 *
 * @author linux_china
 */
public class SpyCachingProvider implements CachingProvider {
    private URI defaultUri;
    private Properties defaultProperties;

    /**
     * The CacheManagers scoped by ClassLoader and URI.
     */
    private WeakHashMap<ClassLoader, HashMap<URI, CacheManager>> cacheManagersByClassLoader;

    public SpyCachingProvider() {
        try {
            this.defaultUri = new URI("cache:memcached:localhost:11211");
            this.defaultProperties = new Properties();
            this.cacheManagersByClassLoader = new WeakHashMap<ClassLoader, HashMap<URI, CacheManager>>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start spy memcached");
        }
    }

    public CacheManager getCacheManager(URI uri, ClassLoader classLoader, Properties properties) {
        try {
            URI managerURI = uri == null ? getDefaultURI() : uri;
            ClassLoader managerClassLoader = classLoader == null ? getDefaultClassLoader() : classLoader;
            Properties managerProperties = properties == null ? new Properties() : properties;
            HashMap<URI, CacheManager> cacheManagersByURI = cacheManagersByClassLoader.get(managerClassLoader);
            if (cacheManagersByURI == null) {
                cacheManagersByURI = new HashMap<URI, CacheManager>();
            }
            CacheManager cacheManager = cacheManagersByURI.get(managerURI);
            if (cacheManager == null) {
                cacheManager = new SpyCacheManager(this, managerURI, managerClassLoader, managerProperties);
                cacheManagersByURI.put(managerURI, cacheManager);
            }
            if (!cacheManagersByClassLoader.containsKey(managerClassLoader)) {
                cacheManagersByClassLoader.put(managerClassLoader, cacheManagersByURI);
            }
            return cacheManager;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create cache manager");
        }
    }

    public ClassLoader getDefaultClassLoader() {
        return this.getClass().getClassLoader();
    }

    public URI getDefaultURI() {
        return this.defaultUri;
    }

    public Properties getDefaultProperties() {
        return this.defaultProperties;
    }

    public CacheManager getCacheManager(URI uri, ClassLoader classLoader) {
        return getCacheManager(uri, classLoader, getDefaultProperties());
    }

    public CacheManager getCacheManager() {
        return getCacheManager(getDefaultURI(), getDefaultClassLoader());
    }

    public void close() {
        WeakHashMap<ClassLoader, HashMap<URI, CacheManager>> managersByClassLoader = this.cacheManagersByClassLoader;
        this.cacheManagersByClassLoader = new WeakHashMap<ClassLoader, HashMap<URI, CacheManager>>();
        for (ClassLoader classLoader : managersByClassLoader.keySet()) {
            for (CacheManager cacheManager : managersByClassLoader.get(classLoader).values()) {
                cacheManager.close();
            }
        }
    }

    public void close(ClassLoader classLoader) {
        ClassLoader managerClassLoader = classLoader == null ? getDefaultClassLoader() : classLoader;
        HashMap<URI, CacheManager> cacheManagersByURI = cacheManagersByClassLoader.remove(managerClassLoader);
        if (cacheManagersByURI != null) {
            for (CacheManager cacheManager : cacheManagersByURI.values()) {
                cacheManager.close();
            }
        }
    }

    public void close(URI uri, ClassLoader classLoader) {
        URI managerURI = uri == null ? getDefaultURI() : uri;
        ClassLoader managerClassLoader = classLoader == null ? getDefaultClassLoader() : classLoader;
        HashMap<URI, CacheManager> cacheManagersByURI = cacheManagersByClassLoader.get(managerClassLoader);
        if (cacheManagersByURI != null) {
            CacheManager cacheManager = cacheManagersByURI.remove(managerURI);
            if (cacheManager != null) {
                cacheManager.close();
            }
            if (cacheManagersByURI.size() == 0) {
                cacheManagersByClassLoader.remove(managerClassLoader);
            }
        }
    }

    public boolean isSupported(OptionalFeature optionalFeature) {
        switch (optionalFeature) {
            case STORE_BY_REFERENCE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Releases the CacheManager with the specified URI and ClassLoader
     * from this CachingProvider.  This does not close the CacheManager.  It
     * simply releases it from being tracked by the CachingProvider.
     * <p/>
     * This method does nothing if a CacheManager matching the specified
     * parameters is not being tracked.
     *
     * @param uri         the URI of the CacheManager
     * @param classLoader the ClassLoader of the CacheManager
     */
    public synchronized void releaseCacheManager(URI uri, ClassLoader classLoader) {
        URI managerURI = uri == null ? getDefaultURI() : uri;
        ClassLoader managerClassLoader = classLoader == null ? getDefaultClassLoader() : classLoader;
        HashMap<URI, CacheManager> cacheManagersByURI = cacheManagersByClassLoader.get(managerClassLoader);
        if (cacheManagersByURI != null) {
            cacheManagersByURI.remove(managerURI);
            if (cacheManagersByURI.size() == 0) {
                cacheManagersByClassLoader.remove(managerClassLoader);
            }
        }
    }
}
