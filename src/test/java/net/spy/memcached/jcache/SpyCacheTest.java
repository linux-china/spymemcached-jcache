package net.spy.memcached.jcache;

import com.thimbleware.jmemcached.CacheImpl;
import com.thimbleware.jmemcached.Key;
import com.thimbleware.jmemcached.LocalCacheElement;
import com.thimbleware.jmemcached.MemCacheDaemon;
import com.thimbleware.jmemcached.storage.CacheStorage;
import com.thimbleware.jmemcached.storage.hash.ConcurrentLinkedHashMap;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.jcache.spi.SpyCachingProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Factory;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;

/**
 * Spy Cache test
 *
 * @author linux_china
 */
public class SpyCacheTest {
    private static MemCacheDaemon<LocalCacheElement> daemon;
    private static CacheManager cacheManager;

    @BeforeClass
    public static void setUp() throws Exception {
        int port = 11211;
        // create daemon and start it
        daemon = new MemCacheDaemon<LocalCacheElement>();
        CacheStorage<Key, LocalCacheElement> storage = ConcurrentLinkedHashMap.create(ConcurrentLinkedHashMap.EvictionPolicy.FIFO, 10000, 10000);
        daemon.setCache(new CacheImpl(storage));
        daemon.setAddr(new InetSocketAddress("localhost", port));
        daemon.start();
        SpyCachingProvider provider = new SpyCachingProvider();
        cacheManager = provider.getCacheManager(URI.create("cache:memcached:localhost:11211"), null);
    }

    @Test
    public void testJCacheOperations() throws Exception {
        Cache<Integer, Object> cache = cacheManager.getCache("user");
        cache.put(1, "linux_china");
        System.out.println("Cache:" + cache.get(1));
        MemcachedClient memcacheClient = cache.unwrap(MemcachedClient.class);
        Object object = memcacheClient.get("user$1");
        System.out.println("Client:" + object);
        Map<Integer, Object> store = cache.unwrap(Map.class);
        System.out.println("Map:" + store.get(1));
    }

    @Test
    public void testReadThrough() throws Exception {
        MutableConfiguration<String, String> configuration = new MutableConfiguration<String, String>();
        configuration.setReadThrough(true);
        configuration.setCacheLoaderFactory(new Factory<CacheLoader<String, String>>() {
            @Override
            public CacheLoader<String, String> create() {
                return new CacheLoader<String, String>() {
                    @Override
                    public String load(String key) throws CacheLoaderException {
                        return "Jacky";
                    }

                    @Override
                    public Map<String, String> loadAll(Iterable keys) throws CacheLoaderException {
                        return null;
                    }
                };
            }
        });
        Cache<String, String> cache = cacheManager.createCache("user", configuration);
        System.out.println(cache.get("1"));
    }

    @Test
    public void testOperation() throws Exception {
        MemcachedClient c = new MemcachedClient(new InetSocketAddress("localhost", 11211));
        // Store a value (async) for one hour
        User user = new User();
        user.setId(1);
        user.setName("jacky");
        c.set("nick", 3600, user);
        // Retrieve a value (synchronously).
        User myObject = (User) c.get("nick");
        System.out.println(myObject.getName());
    }

    @AfterClass
    public static void tearDown() throws Exception {
        daemon.stop();
    }

}
