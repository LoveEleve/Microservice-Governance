package com.wuba.crm.tx.tcc.example.coupon.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * coupon 本地业务表 Mapper。
 */
@Mapper
public interface CouponMapper {

    /**
     * Try：锁定优惠券。
     *
     * @param couponId 优惠券
     * @return 影响行数
     */
    @Update("""
            UPDATE t_coupon
            SET status = 'LOCKED'
            WHERE coupon_id = #{couponId} AND status = 'AVAILABLE'
            """)
    int lock(@Param("couponId") long couponId);

    /**
     * Confirm：核销优惠券。
     *
     * @param couponId 优惠券
     * @return 影响行数
     */
    @Update("""
            UPDATE t_coupon
            SET status = 'USED'
            WHERE coupon_id = #{couponId} AND status = 'LOCKED'
            """)
    int confirm(@Param("couponId") long couponId);

    /**
     * Cancel：释放优惠券。
     *
     * @param couponId 优惠券
     * @return 影响行数
     */
    @Update("""
            UPDATE t_coupon
            SET status = 'AVAILABLE'
            WHERE coupon_id = #{couponId} AND status = 'LOCKED'
            """)
    int cancel(@Param("couponId") long couponId);
}
