package com.wuba.crm.tx.tcc.id;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code id_segment} 表的 MyBatis-Plus 映射实体。
 *
 * <p>每一行代表一个业务 key 的号段水位。TCC 的 XID 使用独立业务 key
 * {@code tcc-xid}，不和业务订单号共用水位。</p>
 */
@TableName("id_segment")
public class IdSegmentEntity {

    /** 业务 key，例如 {@code tcc-xid}。 */
    @TableId(value = "biz_type", type = IdType.INPUT)
    private String bizType;

    /** 已经分配出去的最大 ID，也即下一个号段的右边界。 */
    @TableField("max_id")
    private Long maxId;

    /** 配置步长，同时作为自适应步长的下限。 */
    @TableField("step")
    private Integer step;

    /**
     * 获取业务 key。
     *
     * @return 业务 key
     */
    public String getBizType() {
        return bizType;
    }

    /**
     * 设置业务 key。
     *
     * @param bizType 业务 key
     */
    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    /**
     * 获取当前最大水位。
     *
     * @return 最大水位
     */
    public Long getMaxId() {
        return maxId;
    }

    /**
     * 设置当前最大水位。
     *
     * @param maxId 最大水位
     */
    public void setMaxId(Long maxId) {
        this.maxId = maxId;
    }

    /**
     * 获取配置步长。
     *
     * @return 配置步长
     */
    public Integer getStep() {
        return step;
    }

    /**
     * 设置配置步长。
     *
     * @param step 配置步长
     */
    public void setStep(Integer step) {
        this.step = step;
    }
}
