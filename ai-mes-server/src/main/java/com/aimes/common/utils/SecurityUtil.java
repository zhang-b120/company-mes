package com.aimes.common.utils;

import java.util.List;

public class SecurityUtil {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<DataScopeContext> DATA_SCOPE_CTX = new ThreadLocal<>();

    public static void setCurrentUserId(Long id) {
        CURRENT_USER_ID.set(id);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void setDataScopeContext(DataScopeContext ctx) {
        DATA_SCOPE_CTX.set(ctx);
    }

    public static DataScopeContext getDataScopeContext() {
        return DATA_SCOPE_CTX.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
        DATA_SCOPE_CTX.remove();
    }

    /**
     * 数据权限上下文
     */
    public static class DataScopeContext {
        /** 数据权限范围: all / dept / self / custom */
        private String scope;
        /**
         * dept 模式下：当前用户所在部门及所有子孙部门的 ID 列表（含自身）
         * 为空时退化为 self 权限
         */
        private List<Long> deptIds;
        /** 自定义可见用户ID列表 */
        private List<Long> customUserIds;

        public DataScopeContext() {}

        public DataScopeContext(String scope, List<Long> deptIds, List<Long> customUserIds) {
            this.scope = scope;
            this.deptIds = deptIds;
            this.customUserIds = customUserIds;
        }

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public List<Long> getDeptIds() { return deptIds; }
        public void setDeptIds(List<Long> deptIds) { this.deptIds = deptIds; }
        public List<Long> getCustomUserIds() { return customUserIds; }
        public void setCustomUserIds(List<Long> customUserIds) { this.customUserIds = customUserIds; }
    }
}
