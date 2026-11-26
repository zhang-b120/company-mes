package com.aimes.common.cache;

import com.aimes.common.config.CacheConfig;
import com.aimes.modules.master.entity.BizMaterialCategory;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialCategoryMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 通用名称缓存服务
 * <p>
 * 三类小表(供应商/物料类别/用户)的 id→名称 映射做本地缓存,供各列表接口回填用。
 * 写操作处通过 {@link NameCacheEvictor} 主动失效。
 * <p>
 * 设计要点：
 *  - {@code getXxxName(id)} 单条接口加 @Cacheable 注解,适合详情/编辑等单条场景
 *  - {@code batchGetXxxName(ids)} 批量接口直接通过 CacheManager 读缓存,缺失部分一次 IN 查询补齐并回写,
 *    避免"同类内调用 Spring AOP 不生效"的问题
 */
@Service
@RequiredArgsConstructor
public class NameCacheService {

    private final BizSupplierMapper supplierMapper;
    private final BizMaterialCategoryMapper categoryMapper;
    private final SysUserMapper userMapper;
    private final CacheManager cacheManager;

    @Cacheable(value = CacheConfig.CACHE_SUPPLIER_NAME, key = "#id", unless = "#result == null")
    public String getSupplierName(Long id) {
        if (id == null) return null;
        BizSupplier sup = supplierMapper.selectOne(new LambdaQueryWrapper<BizSupplier>()
                .select(BizSupplier::getId, BizSupplier::getSupplierName)
                .eq(BizSupplier::getId, id));
        return sup == null ? null : sup.getSupplierName();
    }

    @Cacheable(value = CacheConfig.CACHE_CATEGORY_NAME, key = "#id", unless = "#result == null")
    public String getCategoryName(Long id) {
        if (id == null) return null;
        BizMaterialCategory cat = categoryMapper.selectOne(new LambdaQueryWrapper<BizMaterialCategory>()
                .select(BizMaterialCategory::getId, BizMaterialCategory::getCategoryName)
                .eq(BizMaterialCategory::getId, id));
        return cat == null ? null : cat.getCategoryName();
    }

    @Cacheable(value = CacheConfig.CACHE_USER_NAME, key = "#id", unless = "#result == null")
    public String getUserName(Long id) {
        if (id == null) return null;
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getRealName, SysUser::getUsername)
                .eq(SysUser::getId, id));
        if (u == null) return null;
        return u.getRealName() != null && !u.getRealName().isEmpty() ? u.getRealName() : u.getUsername();
    }

    public Map<Long, String> batchGetSupplierName(Set<Long> ids) {
        return batchGet(ids, CacheConfig.CACHE_SUPPLIER_NAME, missing -> {
            Map<Long, String> map = new HashMap<>();
            supplierMapper.selectList(new LambdaQueryWrapper<BizSupplier>()
                            .select(BizSupplier::getId, BizSupplier::getSupplierName)
                            .in(BizSupplier::getId, missing))
                    .forEach(s -> map.put(s.getId(), s.getSupplierName()));
            return map;
        });
    }

    public Map<Long, String> batchGetCategoryName(Set<Long> ids) {
        return batchGet(ids, CacheConfig.CACHE_CATEGORY_NAME, missing -> {
            Map<Long, String> map = new HashMap<>();
            categoryMapper.selectList(new LambdaQueryWrapper<BizMaterialCategory>()
                            .select(BizMaterialCategory::getId, BizMaterialCategory::getCategoryName)
                            .in(BizMaterialCategory::getId, missing))
                    .forEach(c -> map.put(c.getId(), c.getCategoryName()));
            return map;
        });
    }

    public Map<Long, String> batchGetUserName(Set<Long> ids) {
        return batchGet(ids, CacheConfig.CACHE_USER_NAME, missing -> {
            Map<Long, String> map = new HashMap<>();
            userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .select(SysUser::getId, SysUser::getRealName, SysUser::getUsername)
                            .in(SysUser::getId, missing))
                    .forEach(u -> map.put(u.getId(),
                            u.getRealName() != null && !u.getRealName().isEmpty() ? u.getRealName() : u.getUsername()));
            return map;
        });
    }

    /**
     * 批量取名通用模板：缓存优先,缺失部分一次 IN 查询补齐并回写缓存
     */
    private Map<Long, String> batchGet(Set<Long> ids, String cacheName,
                                       java.util.function.Function<Set<Long>, Map<Long, String>> dbLoader) {
        Map<Long, String> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) return result;
        Cache cache = cacheManager.getCache(cacheName);
        Set<Long> missing = new HashSet<>();
        for (Long id : ids) {
            if (id == null) continue;
            String hit = cache != null ? cache.get(id, String.class) : null;
            if (hit != null) {
                result.put(id, hit);
            } else {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            Map<Long, String> dbResult = dbLoader.apply(missing);
            dbResult.forEach((id, name) -> {
                if (cache != null && name != null) cache.put(id, name);
            });
            result.putAll(dbResult);
        }
        return result;
    }
}
