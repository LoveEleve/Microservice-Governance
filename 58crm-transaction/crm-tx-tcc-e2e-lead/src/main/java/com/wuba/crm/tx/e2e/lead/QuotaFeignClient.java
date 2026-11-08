package com.wuba.crm.tx.e2e.lead;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 业务方通过 OpenFeign 调用 quota-service 的 Try 接口。
 */
@FeignClient(name = "quota-service")
public interface QuotaFeignClient {

    /**
     * 调用 quota 的 Try。
     *
     * @param request Try 请求
     * @return Try 结果
     */
    @PostMapping("/tcc/try")
    TryQuotaResponse tryQuota(@RequestBody TryQuotaRequest request);
}
