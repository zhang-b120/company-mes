package com.aimes.common.interceptor;

import com.aimes.common.annotation.DataScope;
import com.aimes.common.utils.SecurityUtil;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据权限拦截器 — 基于 MyBatis-Plus InnerInterceptor
 * 通过在 ThreadLocal 中获取当前用户的数据权限范围(DataScopeContext),
 * 在 SQL 执行前动态追加 WHERE 条件以实现行级数据隔离。
 *
 * 权限类型:
 * - all: 不追加条件（可查看所有数据）
 * - self: 追加 create_by = {userId}
 * - dept: 追加 create_by IN (SELECT id FROM sys_user WHERE workshop = '{workshop}')
 * - custom: 追加 create_by IN ({customUserIds})
 *
 * 触发方式: 在 Controller 方法标注 @DataScope 注解，AuthInterceptor 设置上下文后，
 *           DataScopeAspect 激活标记，本拦截器自动应用过滤。
 */
@Slf4j
public class DataPermissionInterceptor implements InnerInterceptor {

    /** ThreadLocal 标记: 当前请求是否需要数据权限过滤 */
    private static final ThreadLocal<String> TABLE_ALIAS = new ThreadLocal<>();
    /** ThreadLocal: 限定只对该 Mapper 的语句生效，空则对所有生效 */
    private static final ThreadLocal<String> MAPPER_CLASS = new ThreadLocal<>();

    public static void activate(String tableAlias, String mapperClass) {
        TABLE_ALIAS.set(tableAlias != null ? tableAlias : "");
        MAPPER_CLASS.set(mapperClass != null ? mapperClass : "");
    }

    public static void deactivate() {
        TABLE_ALIAS.remove();
        MAPPER_CLASS.remove();
    }

    public static boolean isActive() {
        return TABLE_ALIAS.get() != null;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (!isActive()) return;

        // 若指定了 mapperClass，只对该 Mapper 的 SQL 生效，其他子查询跳过
        String mapperClass = MAPPER_CLASS.get();
        if (mapperClass != null && !mapperClass.isEmpty() && !ms.getId().startsWith(mapperClass)) {
            return;
        }

        SecurityUtil.DataScopeContext ctx = SecurityUtil.getDataScopeContext();
        if (ctx == null || "all".equals(ctx.getScope())) return;

        String alias = TABLE_ALIAS.get();
        String prefix = (alias != null && !alias.isEmpty()) ? alias + "." : "";
        String originalSql = boundSql.getSql();
        String condition = buildCondition(ctx, prefix);

        if (condition != null && !condition.isEmpty()) {
            // 直接把 condition 注入到原 SQL 的最外层 WHERE,避免 SELECT * FROM (count) 形式破坏聚合查询
            String newSql = injectWhere(originalSql, condition);
            try {
                java.lang.reflect.Field field = BoundSql.class.getDeclaredField("sql");
                field.setAccessible(true);
                field.set(boundSql, newSql);
            } catch (Exception e) {
                throw new SQLException("Failed to apply data scope filter", e);
            }
        }
    }

    /**
     * 用 jsqlparser 把 condition 注入到顶层 SELECT 的 WHERE 子句, 兼容 COUNT/聚合等场景。
     * 解析失败时退回旧的"包一层"方案。
     */
    private String injectWhere(String originalSql, String condition) {
        try {
            net.sf.jsqlparser.statement.Statement stmt = net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(originalSql);
            if (stmt instanceof net.sf.jsqlparser.statement.select.Select sel) {
                net.sf.jsqlparser.statement.select.PlainSelect plain = sel.getPlainSelect();
                if (plain != null) {
                    net.sf.jsqlparser.expression.Expression extra =
                            net.sf.jsqlparser.parser.CCJSqlParserUtil.parseCondExpression(condition);
                    net.sf.jsqlparser.expression.Expression existing = plain.getWhere();
                    if (existing == null) {
                        plain.setWhere(extra);
                    } else {
                        plain.setWhere(new net.sf.jsqlparser.expression.operators.conditional.AndExpression(existing, extra));
                    }
                    return sel.toString();
                }
            }
        } catch (Exception e) {
            log.warn("[DataScope] jsqlparser 注入失败, 退回包装方案: {}", e.getMessage());
        }
        return "SELECT * FROM (" + originalSql + ") _data_scope WHERE " + condition;
    }

    private String buildCondition(SecurityUtil.DataScopeContext ctx, String prefix) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) return null;

        // 业务模块的"业主列": 销售订单走 (create_by OR salesman_id)
        // 其它默认只用 create_by
        String mapperClass = MAPPER_CLASS.get();
        java.util.List<String> ownerCols = ownerColumnsFor(mapperClass);

        switch (ctx.getScope()) {
            case "self":
                return joinEq(prefix, ownerCols, userId);

            case "dept":
                List<Long> deptIds = ctx.getDeptIds();
                if (deptIds != null && !deptIds.isEmpty()) {
                    String ids = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    String inClause = "(SELECT id FROM sys_user WHERE dept_id IN (" + ids + ") AND deleted = 0)";
                    return joinIn(prefix, ownerCols, inClause);
                }
                return joinEq(prefix, ownerCols, userId);

            case "custom":
                List<Long> customIds = ctx.getCustomUserIds();
                if (customIds != null && !customIds.isEmpty()) {
                    String ids = customIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    return joinIn(prefix, ownerCols, "(" + ids + ")");
                }
                return joinEq(prefix, ownerCols, userId);

            default:
                return null;
        }
    }

    /**
     * 根据 mapper 类名返回该实体的"业主列"列表。
     * 销售订单同时按 create_by / salesman_id 任一匹配, 其它默认只看 create_by。
     */
    private java.util.List<String> ownerColumnsFor(String mapperClass) {
        if (mapperClass != null && mapperClass.endsWith("BizSalesOrderMapper")) {
            return java.util.List.of("create_by", "salesman_id");
        }
        return java.util.List.of("create_by");
    }

    private String joinEq(String prefix, java.util.List<String> cols, Long userId) {
        if (cols.size() == 1) return prefix + cols.get(0) + " = " + userId;
        return "(" + cols.stream().map(c -> prefix + c + " = " + userId).collect(Collectors.joining(" OR ")) + ")";
    }

    private String joinIn(String prefix, java.util.List<String> cols, String inClause) {
        if (cols.size() == 1) return prefix + cols.get(0) + " IN " + inClause;
        return "(" + cols.stream().map(c -> prefix + c + " IN " + inClause).collect(Collectors.joining(" OR ")) + ")";
    }
}
