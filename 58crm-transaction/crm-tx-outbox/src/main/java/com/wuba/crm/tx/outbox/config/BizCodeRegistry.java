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
        // 1. 没有配置就不允许启动：否则所有发布都会被当成未知 bizCode。
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("bizCode configs must not be empty");
        }
        // 2. 用 LinkedHashMap 保留配置顺序，方便测试和排障。
        Map<String, BizCodeConfig> byCode = new LinkedHashMap<>();
        for (BizCodeConfig config : configs) {
            // 3. 同一个 bizCode 出现两次是配置错误，不是覆盖。
            BizCodeConfig previous = byCode.putIfAbsent(config.bizCode(), config);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate bizCode: " + config.bizCode());
            }
        }
        // 4. 复制成不可变 Map：启动后外部修改配置集合不会影响注册表。
        this.configs = Map.copyOf(byCode);
    }

    /**
     * 从外部配置构建；Map 的 key 是 bizCode，value 是该业务线配置。
     */
    public static BizCodeRegistry from(Map<String, BizCodeConfig> configs) {
        for (Map.Entry<String, BizCodeConfig> entry : configs.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().bizCode())) {
                throw new IllegalArgumentException(
                        "bizCode key mismatch: " + entry.getKey()
                                + " != " + entry.getValue().bizCode());
            }
        }
        return new BizCodeRegistry(configs.values());
    }

    /**
     * 仅用于测试/最小示例；生产配置应从 Nacos/YAML 加载。
     */
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
