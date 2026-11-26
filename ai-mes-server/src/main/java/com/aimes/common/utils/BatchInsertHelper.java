package com.aimes.common.utils;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * MyBatis 批量 insert 工具
 *
 * 解决: 单条 mapper.insert(entity) 每条一个 SQL round trip,数据模拟导入几万行时
 * 单线程也要打几万次 DB 调用,即便 rewriteBatchedStatements=true 也无效(那是 JDBC
 * 层 PreparedStatement.addBatch 才能用上的优化)。
 *
 * 用法:
 *   batchInsert.run(rows, BizFooMapper.class, mapper -> {
 *       for (Foo f : myEntities) mapper.insert(f);   // 内部走 BATCH 模式
 *   });
 *
 * 或者更直接的便捷方法:
 *   batchInsert.insertAll(myEntities, BizFooMapper.class);
 */
@Component
public class BatchInsertHelper {

    private final SqlSessionFactory sqlSessionFactory;

    @Autowired
    public BatchInsertHelper(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    /** 单批大小:超过这个数会自动 flush 一次,防止单事务过大 */
    private static final int FLUSH_BATCH_SIZE = 1000;

    /**
     * 批量 insert: 用 BATCH 模式,自动每 1000 条 flush 一次。
     * 接受 List<T> 和对应 Mapper 类,内部循环调 insert。
     * 适合"已经有完整 entity 列表"的场景。
     */
    public <T, M extends BaseMapper<T>> int insertAll(List<T> entities, Class<M> mapperClass) {
        if (entities == null || entities.isEmpty()) return 0;
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            M mapper = session.getMapper(mapperClass);
            int count = 0;
            for (T e : entities) {
                if (e == null) continue;
                mapper.insert(e);
                count++;
                if (count % FLUSH_BATCH_SIZE == 0) {
                    session.flushStatements();
                }
            }
            session.commit();
            session.flushStatements();
            return count;
        }
    }

    /**
     * BATCH 模式提供 mapper,允许调用方在内部做更复杂的循环
     * (比如混合 insert+update,或要在循环里读 generated id 再做联动)
     */
    public <M> void run(Class<M> mapperClass, Consumer<M> action) {
        try (SqlSession session = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            M mapper = session.getMapper(mapperClass);
            action.accept(mapper);
            session.commit();
            session.flushStatements();
        }
    }
}
