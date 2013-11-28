spymemcached-jcache
================================
spymemcached-jcache is a JSR107 JCache provider using spymemcached as memcache client.

### Features

* namespace support by cache name, cache name should be \w+
* add seperator support, default is $. You can set the seperator in the url, such as memcached://localhost:11211?seperator=$$
* expose native cache api by unwrap
* CacheLoader support to auto load data from backend

### Usage
First we should find the caching provider, then create cache manager from the provider, finally we create the cache to operate cache entries.

       SpyCachingProvider provider = new SpyCachingProvider();
       CacheManager cacheManager = provider.getCacheManager(URI.create("memcached://localhost:11211"), null);
       Cache<Integer, Object> cache = cacheManager.getCache("user");
       cache.put(1, "linux_china");
       System.out.println(cache.get(1));
       MemcachedClient memcacheClient = cache.unwrap(MemcachedClient.class);
       Object object = memcacheClient.get("user$1");
       System.out.println(object);

### FAQ

##### set custom seperator
You can set the seperator in the url, such as memcached://localhost:11211?seperator=$$

##### add multi memcached hosts
Please add peer param in the url, such as memcached://localhost:11211?peer=localhost:11212;localhost:11213  Hosts splitted by ";".

#### How to get MemcachedClient object to execute some commands?
Two way:

        MemcachedClient memcacheClient = cacheManager.unwrap(MemcachedClient.class);
or

        MemcachedClient memcacheClient = cache.unwrap(MemcachedClient.class);

