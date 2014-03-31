package net.spy.memcached.jcache;

import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;

/**
 * spy mutable configuration
 *
 * @author linux_china
 */
public class SpyMutableConfiguration<K, V> extends MutableConfiguration<K, V> {
    private CacheLoader<K, V> cacheLoader;
    private CacheWriter<? super K, ? super V> cacheWriter;
    private ExpiryPolicy expiryPolicy;

    public SpyMutableConfiguration(CompleteConfiguration<K, V> configuration) {
        super(configuration);
        if (configuration.getCacheLoaderFactory() != null) {
            cacheLoader = configuration.getCacheLoaderFactory().create();
        }
        if (configuration.getCacheWriterFactory() != null) {
            this.cacheWriter = configuration.getCacheWriterFactory().create();
        }
        this.expiryPolicy = configuration.getExpiryPolicyFactory().create();
    }

    public CacheLoader<K, V> getCacheLoader() {
        return cacheLoader;
    }

    public boolean isReadThroughSupport() {
        return this.isReadThrough && cacheLoader != null;
    }

    public CacheWriter<? super K, ? super V> getCacheWriter() {
        return cacheWriter;
    }

    public boolean isWriteThroughSupport() {
        return this.isWriteThrough && cacheWriter != null;
    }

    public ExpiryPolicy getExpiryPolicy() {
        return expiryPolicy;
    }

    @Override
    public MutableConfiguration<K, V> setCacheLoaderFactory(Factory<? extends CacheLoader<K, V>> factory) {
        this.cacheLoader = factory.create();
        return super.setCacheLoaderFactory(factory);
    }

    @Override
    public MutableConfiguration<K, V> setCacheWriterFactory(Factory<? extends CacheWriter<? super K, ? super V>> factory) {
        this.cacheWriter = factory.create();
        return super.setCacheWriterFactory(factory);
    }
}
