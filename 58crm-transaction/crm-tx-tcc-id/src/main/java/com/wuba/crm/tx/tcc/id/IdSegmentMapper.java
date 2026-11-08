package com.wuba.crm.tx.tcc.id;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * {@code id_segment} 的 MyBatis-Plus Mapper。
 *
 * <p>原子性不依赖单条 SQL，而依赖 {@link MybatisSegmentAllocator} 上的
 * Spring 事务：更新水位和读取新水位必须在同一个连接、同一个事务中完成。</p>
 */
@Mapper
public interface IdSegmentMapper extends BaseMapper<IdSegmentEntity> {

    /**
     * 原子推进号段水位。
     *
     * <p>{@code WHERE biz_type} 会锁住这一行；事务提交前，其他实例的
     * 同 key 更新会等待，因此不会读到旧水位。</p>
     *
     * @param bizType 业务 key
     * @param step    本次领取数量
     * @return 影响行数，正常为 1
     */
    @Update("""
            UPDATE id_segment
            SET max_id = max_id + #{step}
            WHERE biz_type = #{bizType}
            """)
    int increaseMaxId(@Param("bizType") String bizType, @Param("step") int step);

    /**
     * 读取当前水位。
     *
     * @param bizType 业务 key
     * @return 当前最大水位；不存在时返回 null
     */
    @Select("SELECT max_id FROM id_segment WHERE biz_type = #{bizType}")
    Long selectMaxId(@Param("bizType") String bizType);

    /**
     * 读取配置步长。
     *
     * @param bizType 业务 key
     * @return 配置步长；不存在时返回 null
     */
    @Select("SELECT step FROM id_segment WHERE biz_type = #{bizType}")
    Integer selectStep(@Param("bizType") String bizType);
}
