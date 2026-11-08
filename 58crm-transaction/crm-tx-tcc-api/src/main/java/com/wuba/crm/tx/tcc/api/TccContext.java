package com.wuba.crm.tx.tcc.api;

/**
 * 当前线程的 TCC 上下文。
 *
 * <p>业务在分支方法中可通过 {@link #current()} 获取 xid/branchId，
 * 用于调用下游 Try API。</p>
 */
public final class TccContext {

    private static final ThreadLocal<TccContext> CURRENT = new ThreadLocal<>();

    private final long xid;
    private final long branchId;

    private TccContext(long xid, long branchId) {
        this.xid = TccValidation.requirePositive(xid, "xid");
        if (branchId < 0) {
            throw new IllegalArgumentException("branchId must not be negative");
        }
        this.branchId = branchId;
    }

    /**
     * 当前上下文；不存在时抛出异常。
     *
     * @return context
     */
    public static TccContext current() {
        TccContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("no active TCC transaction");
        }
        return context;
    }

    /**
     * 绑定全局事务上下文。
     *
     * @param xid 全局事务号
     */
    public static void bindTransaction(long xid) {
        CURRENT.set(new TccContext(xid, 0));
    }

    /**
     * 绑定分支上下文。
     *
     * @param xid      全局事务号
     * @param branchId 分支号
     */
    public static void bindBranch(long xid, long branchId) {
        CURRENT.set(new TccContext(xid, branchId));
    }

    /**
     * 清理上下文。
     */
    public static void clear() {
        CURRENT.remove();
    }

    /** @return 全局事务号 */
    public long xid() {
        return xid;
    }

    /** @return 分支号；事务级上下文为 0 */
    public long branchId() {
        return branchId;
    }
}
