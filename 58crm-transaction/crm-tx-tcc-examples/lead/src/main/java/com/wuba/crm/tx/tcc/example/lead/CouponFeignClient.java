package com.wuba.crm.tx.tcc.example.lead;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 业务方通过 OpenFeign 调用 coupon-service 的 Try 接口。
 */
@FeignClient(name = "coupon-service")
public interface CouponFeignClient {

    /**
     * 调用 coupon 的 Try。
     *
     * @param request Try 请求
     * @return Try 结果
     */
    @PostMapping("/tcc/try")
    TryCouponResponse tryCoupon(@RequestBody TryCouponRequest request);
}
