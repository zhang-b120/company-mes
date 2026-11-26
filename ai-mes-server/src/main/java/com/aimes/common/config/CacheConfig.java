package com.aimes.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存配置（Caffeine）
 * <p>
 * 缓存空间清单（仅缓存"小表 + 慢变化"的名称数据,保证写后强一致需配合 @CacheEvict）：
 *  - supplierName    供应商 id→名称
 *  - categoryName    物料类别 id→名称
 *  - userName        用户 id→名称
 * <p>
 * 不缓存列表分页结果(避免多实例下写操作不能立即在其他节点生效)。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_SUPPLIER_NAME = "supplierName";
    public static final String CACHE_CATEGORY_NAME = "categoryName";
    public static final String CACHE_USER_NAME = "userName";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(Arrays.asList(
                CACHE_SUPPLIER_NAME, CACHE_CATEGORY_NAME, CACHE_USER_NAME
        ));
        // 名称缓存：10 分钟过期、最多 5000 项
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(5000));
        return manager;
    }
}
