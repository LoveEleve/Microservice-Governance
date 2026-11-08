package com.wuba.crm.tx.tcc.id;

/**
 * TCC 全局事务号生成器。
 *
 * <p>它把 {@link SegmentIdGenerator} 的负返回码转换成异常，避免负 XID
 * 进入 coordinator 状态表。XID 使用独立业务 key {@link #BIZ_TYPE}，
 * 不与业务订单号共享水位。</p>
 */
public final class TccXidGenerator {

    /** id_segment 中 TCC XID 使用的业务 key。 */
    public static final String BIZ_TYPE = "tcc-xid";

    private final SegmentIdGenerator segmentIdGenerator;

    /**
     * 创建 XID 生成器。
     *
     * @param segmentIdGenerator 底层号段发号器
     */
    public TccXidGenerator(SegmentIdGenerator segmentIdGenerator) {
        if (segmentIdGenerator == null) {
            throw new IllegalArgumentException("segmentIdGenerator must not be null");
        }
        this.segmentIdGenerator = segmentIdGenerator;
        this.segmentIdGenerator.register(BIZ_TYPE);
    }

    /**
     * 生成下一个 XID。
     *
     * @return 正整数 XID
     */
    public long nextXid() {
        while (true) {
            long xid = segmentIdGenerator.nextId(BIZ_TYPE);
            if (xid < 0) {
                throw new XidGenerationException("Cannot generate TCC xid, code=" + xid);
            }
            if (xid > 0) {
                return xid;
            }
            // 0 是非法 XID；只跳过号段起始的 0，不改变正常递增。
        }
    }

    /**
     * XID 生成失败异常。
     */
    public static final class XidGenerationException extends IllegalStateException {

        /**
         * 创建异常。
         *
         * @param message 失败原因
         */
        public XidGenerationException(String message) {
            super(message);
        }
    }
}
