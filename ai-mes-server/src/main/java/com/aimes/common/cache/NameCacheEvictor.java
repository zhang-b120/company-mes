package com.aimes.common.cache;

import com.aimes.common.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 名称缓存失效工具
 * <p>
 * 业务模块在写操作(create/update/delete/import)成功后调用对应方法,
 * 主动让本地缓存失效,保证后续读取时拿到最新数据。
 */
@Component
@RequiredArgsConstructor
public class NameCacheEvictor {

    private final CacheManager cacheManager;

    public void evictSupplier(Long id) { evict(CacheConfig.CACHE_SUPPLIER_NAME, id); }
    public void evictAllSuppliers()    { clear(CacheConfig.CACHE_SUPPLIER_NAME); }

    public void evictCategory(Long id) { evict(CacheConfig.CACHE_CATEGORY_NAME, id); }
    public void evictAllCategories()   { clear(CacheConfig.CACHE_CATEGORY_NAME); }

    public void evictUser(Long id)     { evict(CacheConfig.CACHE_USER_NAME, id); }
    public void evictAllUsers()        { clear(CacheConfig.CACHE_USER_NAME); }

    private void evict(String name, Long id) {
        if (id == null) return;
        Cache cache = cacheManager.getCache(name);
        if (cache != null) cache.evict(id);
    }

    private void clear(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) cache.clear();
    }
}
