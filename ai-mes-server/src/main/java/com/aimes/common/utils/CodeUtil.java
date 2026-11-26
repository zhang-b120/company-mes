package com.aimes.common.utils;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 编号生成工具
 * 生成规则: 前缀 + yyyyMMdd日期 + 4位序号（每天从0001重置）
 * 示例: CodeUtil.generate("SO") → "SO202603230001"
 * 序号基于数据库 sys_code_sequence 表，服务重启后不会产生冲突。
 */
@Component
public class CodeUtil implements ApplicationContextAware {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    /**
     * 生成业务编号（静态调用入口，调用方式不变）。
     * Spring 启动后委托给 CodeGeneratorService（DB 序号）；
     * Spring 未就绪时（如单元测试）返回带时间戳的备用编号。
     */
    public static String generate(String prefix) {
        if (applicationContext != null) {
            return applicationContext.getBean(CodeGeneratorService.class).generate(prefix);
        }
        // 备用（仅在非 Spring 环境下触发，正常运行不会到达这里）
        String today = LocalDate.now().format(DATE_FMT);
        return prefix + today + "0001";
    }
}
