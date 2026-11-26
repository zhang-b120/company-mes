package com.aimes.modules.system.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 单据号序列 Mapper
 * 使用 MySQL LAST_INSERT_ID() 技巧实现每次调用原子性自增并返回最新值。
 */
@Mapper
public interface SysCodeSequenceMapper {

    /**
     * 原子性自增：若当天记录不存在则插入(seq=1)，否则 current_seq +1。
     * LAST_INSERT_ID(expr) 将 expr 的值记录到当前连接的 last_insert_id，
     * 后续 SELECT LAST_INSERT_ID() 即可取到本次自增后的值。
     */
    @Insert("INSERT INTO sys_code_sequence (prefix, seq_date, current_seq) " +
            "VALUES (#{prefix}, #{seqDate}, LAST_INSERT_ID(1)) " +
            "ON DUPLICATE KEY UPDATE current_seq = LAST_INSERT_ID(current_seq + 1)")
    void upsertSeq(@Param("prefix") String prefix, @Param("seqDate") String seqDate);

    /**
     * 批量预留 N 个序号: 一次 SQL 把 current_seq +N, LAST_INSERT_ID 返回新的 max 值;
     * 本地分发 [max-N+1, max] 这一段, 避免高并发场景下每号一次 UPDATE 引起行锁排队
     */
    @Insert("INSERT INTO sys_code_sequence (prefix, seq_date, current_seq) " +
            "VALUES (#{prefix}, #{seqDate}, LAST_INSERT_ID(#{count})) " +
            "ON DUPLICATE KEY UPDATE current_seq = LAST_INSERT_ID(current_seq + #{count})")
    void upsertSeqBatch(@Param("prefix") String prefix, @Param("seqDate") String seqDate,
                         @Param("count") int count);

    /** 读取本连接上次 upsertSeq 写入的序号值（连接级别，线程安全） */
    @Select("SELECT LAST_INSERT_ID()")
    int getLastInsertId();
}
