package com.wuba.crm.tx.demo;

/**
 * 线索业务表的最小仓储接口。
 *
 * <p>真实项目里会换成 MyBatis/MyBatis-Plus Mapper；这里只保留业务方需要写的那一步。</p>
 */
public interface LeadRepository {

    void assign(long leadId, long salesId);
}
