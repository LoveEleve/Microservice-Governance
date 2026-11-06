package com.wuba.crm.tx.outbox.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地消息表的 MyBatis-Plus Mapper。
 *
 * <p>后续 Relay 和 Consumer 会在这个 Mapper 上增加条件更新方法；
 * C-001.1 只要求能插入 INIT 消息，因此先保持最小接口。</p>
 */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessageEntity> {
}
