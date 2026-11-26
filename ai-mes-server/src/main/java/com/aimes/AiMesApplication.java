package com.aimes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({"com.aimes.modules.*.mapper", "com.aimes.common.extfield", "com.aimes.common.columnconfig"})
public class AiMesApplication {
    static {
        // 数据模拟生成 / 大批量并行处理时需要更大的 ForkJoin 公共池;
        // 必须在任何 ForkJoinPool.commonPool() 被引用前 set, 所以放静态块/主类 Loader 触发时机
        // 默认 = CPU 核数 - 1, 8 核机器 ≈ 7. 设到 20 平衡吞吐和 DB 连接压力(80 连接池/20 线程 = 4 倍冗余).
        // 用 setProperty 是因为 -D JVM 参数不一定每个启动方式都能传(IDE / docker / fatjar 略有差异).
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "20");
    }

    public static void main(String[] args) {
        SpringApplication.run(AiMesApplication.class, args);
    }
}
