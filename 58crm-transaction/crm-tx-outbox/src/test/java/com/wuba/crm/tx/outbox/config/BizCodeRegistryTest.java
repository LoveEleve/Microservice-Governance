package com.wuba.crm.tx.outbox.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 业务线白名单必须拒绝未知 bizCode，不能动态创建孤儿 Stream。
 */
class BizCodeRegistryTest {

    @Test
    void require_acceptsRegisteredBizCode() {
        BizCodeRegistry registry = new BizCodeRegistry(List.of(
                new BizCodeConfig("lead", 8, 500),
                new BizCodeConfig("quota", 4, 200)));

        assertEquals("tx:stream:lead", registry.streamKey("tx:stream:", "lead"));
        assertEquals(2, registry.bizCodes().size());
    }

    @Test
    void require_rejectsUnknownBizCode() {
        BizCodeRegistry registry = BizCodeRegistry.of("lead");

        assertThrows(UnknownBizCodeException.class,
                () -> registry.require("lead-typo"));
    }

    @Test
    void duplicateBizCode_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BizCodeRegistry(List.of(
                        new BizCodeConfig("lead"),
                        new BizCodeConfig("lead"))));
    }
}
