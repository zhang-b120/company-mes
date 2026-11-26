package com.aimes.common.config;

import com.aimes.common.interceptor.DataPermissionInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 数据权限拦截器（优先于分页，确保先过滤再分页）
        interceptor.addInnerInterceptor(new DataPermissionInterceptor());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大记录数（避免默认 500 意外截断）
        pagination.setMaxLimit(10000L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
