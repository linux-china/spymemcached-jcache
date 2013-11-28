/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle America Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.spy.memcached.jcache.management;

import javax.cache.Cache;
import javax.cache.management.CacheMXBean;

/**
 * RI Cache mxbean
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values*
 * @author Yannis Cosmadopoulos
 * @since 1.0
 */
public class RICacheMXBean<K, V> implements CacheMXBean {

    private final Cache<K, V> cache;


    /**
     * Constructor
     *
     * @param cache the cache
     */
    public RICacheMXBean(Cache<K, V> cache) {
        this.cache = cache;
    }

    public String getKeyType() {
        return cache.getConfiguration().getKeyType().getName();
    }

    public String getValueType() {
        return cache.getConfiguration().getValueType().getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadThrough() {
        return cache.getConfiguration().isReadThrough();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWriteThrough() {
        return cache.getConfiguration().isWriteThrough();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isStoreByValue() {
        return cache.getConfiguration().isStoreByValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isStatisticsEnabled() {
        return cache.getConfiguration().isStatisticsEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isManagementEnabled() {
        return cache.getConfiguration().isManagementEnabled();
    }
}
