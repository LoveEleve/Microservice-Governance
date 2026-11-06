package com.wuba.crm.tx.outbox.config;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 业务线白名单和配置注册表。
 *
 * <p>Stream key 是 {@code prefix + bizCode}，但 bizCode 不能由业务方随意传；
 * 未注册的 bizCode 会创建没人消费的孤儿 Stream，因此发布前必须校验。</p>
 */
public final class BizCodeRegistry {

    private final Map<String, BizCodeConfig> configs;

    public BizCodeRegistry(Collection<BizCodeConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("bizCode configs must not be empty");
        }
        Map<String, BizCodeConfig> byCode = new LinkedHashMap<>();
        for (BizCodeConfig config : configs) {
            BizCodeConfig previous = byCode.putIfAbsent(config.bizCode(), config);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate bizCode: " + config.bizCode());
            }
        }
        this.configs = Map.copyOf(byCode);
    }

    public static BizCodeRegistry of(String... bizCodes) {
        java.util.List<BizCodeConfig> configs = new java.util.ArrayList<>();
        for (String bizCode : bizCodes) {
            configs.add(new BizCodeConfig(bizCode));
        }
        return new BizCodeRegistry(configs);
    }

    /**
     * 校验 bizCode；不存在直接抛异常，禁止创建孤儿 Stream。
     */
    public BizCodeConfig require(String bizCode) {
        BizCodeConfig config = configs.get(bizCode);
        if (config == null) {
            throw new UnknownBizCodeException(bizCode);
        }
        return config;
    }

    public boolean contains(String bizCode) {
        return configs.containsKey(bizCode);
    }

    public Set<String> bizCodes() {
        return configs.keySet();
    }

    public String streamKey(String prefix, String bizCode) {
        return prefix + require(bizCode).bizCode();
    }
}
