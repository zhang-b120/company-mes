package com.aimes.common.aspect;

import com.aimes.common.annotation.DataScope;
import com.aimes.common.interceptor.DataPermissionInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面
 * 在标注了 @DataScope 的Controller方法执行期间，激活DataPermissionInterceptor
 * 方法执行结束后自动反激活，确保不影响其他查询
 */
@Aspect
@Component
public class DataScopeAspect {

    @Around("@annotation(com.aimes.common.annotation.DataScope)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        DataScope dataScope = signature.getMethod().getAnnotation(DataScope.class);
        try {
            DataPermissionInterceptor.activate(dataScope.tableAlias(), dataScope.mapperClass());
            return point.proceed();
        } finally {
            DataPermissionInterceptor.deactivate();
        }
    }
}
