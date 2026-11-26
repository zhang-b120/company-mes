package com.aimes.common.utils;

import com.aimes.modules.system.mapper.SysCodeSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单据号生成 Service（Spring 托管，支持数据库级序号）。
 * 高并发场景(数据模拟生成 50 线程并行)走批量预留 + 内存池, 避免远程 MySQL 行锁排队;
 * 普通业务调用走 generate(prefix) 仍是单号原子写。
 */
@Service
@RequiredArgsConstructor
public class CodeGeneratorService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 每次预留多少个号 — 越大越省 DB 调用, 但服务重启会浪费未用完的部分 */
    private static final int BATCH_SIZE = 500;

    private final SysCodeSequenceMapper sequenceMapper;

    /**
     * key = prefix + "_" + yyyyMMdd; value = 当前指针 + 上界 (闭区间)
     * 用 ConcurrentHashMap + 内置锁保证多线程下原子分发
     */
    private static class Pool {
        long next;     // 下一个可分发号
        long maxIncl;  // 已预留的最大号(含)
        Pool(long next, long maxIncl) { this.next = next; this.maxIncl = maxIncl; }
    }
    private final Map<String, Pool> pools = new ConcurrentHashMap<>();

    /**
     * 生成唯一业务编号：prefix + yyyyMMdd + 四位序号（每天从 0001 重置）。
     * 优先从内存池取号; 池空了一次性向 DB 预留 BATCH_SIZE 个号。
     * 例：SO202603230001
     */
    public String generate(String prefix) {
        String today = LocalDate.now().format(DATE_FMT);
        String key = prefix + "_" + today;
        long seq = nextSeq(key, prefix, today);
        return prefix + today + String.format("%04d", seq);
    }

    /** 池操作: 加 synchronized 防多线程同时争同一个 key 的 pool */
    private long nextSeq(String key, String prefix, String today) {
        Pool pool = pools.computeIfAbsent(key, k -> new Pool(1, 0));
        synchronized (pool) {
            if (pool.next > pool.maxIncl) {
                long endIncl = reserveBatch(prefix, today, BATCH_SIZE);
                pool.next = endIncl - BATCH_SIZE + 1;
                pool.maxIncl = endIncl;
            }
            return pool.next++;
        }
    }

    /** 一次 SQL 在 DB 上预留 count 个号, 返回区间末尾 (闭区间) */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int reserveBatch(String prefix, String seqDate, int count) {
        sequenceMapper.upsertSeqBatch(prefix, seqDate, count);
        return sequenceMapper.getLastInsertId();
    }
}
