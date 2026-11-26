package com.aimes.common.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局日期时间序列化配置
 * 将 LocalDateTime 和 LocalDate 序列化为标准中文格式（空格分隔而非 T）
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_PATTERN = "yyyy-MM-dd";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            JavaTimeModule module = new JavaTimeModule();
            // LocalDateTime: yyyy-MM-dd HH:mm:ss (序列化输出用空格)
            module.addSerializer(LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)));
            // 反序列化兼容 "yyyy-MM-dd HH:mm:ss" 和 ISO "yyyy-MM-ddTHH:mm:ss"
            module.addDeserializer(LocalDateTime.class,
                    new LocalDateTimeDeserializer(new java.time.format.DateTimeFormatterBuilder()
                            .appendPattern("yyyy-MM-dd")
                            .appendOptional(DateTimeFormatter.ofPattern("'T'HH:mm:ss"))
                            .appendOptional(DateTimeFormatter.ofPattern(" HH:mm:ss"))
                            .appendOptional(DateTimeFormatter.ofPattern("'T'HH:mm"))
                            .appendOptional(DateTimeFormatter.ofPattern(" HH:mm"))
                            .parseDefaulting(java.time.temporal.ChronoField.HOUR_OF_DAY, 0)
                            .parseDefaulting(java.time.temporal.ChronoField.MINUTE_OF_HOUR, 0)
                            .parseDefaulting(java.time.temporal.ChronoField.SECOND_OF_MINUTE, 0)
                            .toFormatter()));
            // LocalDate: yyyy-MM-dd
            module.addSerializer(LocalDate.class,
                    new LocalDateSerializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
            module.addDeserializer(LocalDate.class,
                    new LocalDateDeserializer(DateTimeFormatter.ofPattern(DATE_PATTERN)));
            builder.modulesToInstall(module);
        };
    }
}
