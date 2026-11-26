package com.aimes.common.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * 标注在Controller方法上，标识该方法需要进行数据权限过滤
 * 运行时通过DataPermissionInterceptor自动在SQL中追加 create_by 条件
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {
    /** 表别名，用于拼接 alias.create_by 条件，默认空（不加别名） */
    String tableAlias() default "";
    /**
     * 目标 Mapper 全类名前缀，拦截器只对该 Mapper 的 SQL 追加数据权限条件。
     * 为空时对所有 Mapper 生效（兼容旧行为）。
     * 示例: "com.aimes.modules.order.mapper.BizSalesOrderMapper"
     */
    String mapperClass() default "";
}
