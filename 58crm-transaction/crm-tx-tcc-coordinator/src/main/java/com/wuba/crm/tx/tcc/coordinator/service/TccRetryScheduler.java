package com.wuba.crm.tx.tcc.coordinator.service;

import com.wuba.crm.tx.tcc.api.GlobalStatus;
import com.wuba.crm.tx.tcc.api.TccException;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccBranchRepository;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccGlobalEntity;
import com.wuba.crm.tx.tcc.coordinator.persistence.TccGlobalRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCC 二阶段补偿扫描器。
 *
 * <p>扫描到期的 branch retry 和 TRYING 超时；实际 owner 抢占、
 * 状态推进和 RM 回调仍由 {@link TccPhase2Coordinator} 负责。</p>
 */
@Service
@ConditionalOnProperty(
        prefix = "crm.tx.tcc.coordinator",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TccRetryScheduler {

    private static final Logger log = Logger.getLogger(TccRetryScheduler.class.getName());
    private static final int SCAN_LIMIT = 100;

    private final TccGlobalRepository globals;
    private final TccBranchRepository branches;
    private final TccPhase2Coordinator phase2Coordinator;
    private final Clock clock;

    /**
     * 创建扫描器。
     *
     * @param globals          全局仓储
     * @param branches         分支仓储
     * @param phase2Coordinator 二阶段协调器
     * @param clock            UTC 时钟
     */
    public TccRetryScheduler(
            TccGlobalRepository globals,
            TccBranchRepository branches,
            TccPhase2Coordinator phase2Coordinator,
            Clock clock) {
        if (globals == null || branches == null || phase2Coordinator == null || clock == null) {
            throw new IllegalArgumentException("scheduler dependencies must not be null");
        }
        this.globals = globals;
        this.branches = branches;
        this.phase2Coordinator = phase2Coordinator;
        this.clock = clock;
    }

    /**
     * 定时扫描入口。
     */
    @Scheduled(fixedDelayString = "${crm.tx.tcc.coordinator.retry-scan-ms:1000}")
    public void scheduledScan() {
        scanOnce();
    }

    /**
     * 执行一轮扫描。测试直接调用，不依赖调度线程。
     */
    public void scanOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long xid : safe(branches.findDueRetryXids(now, SCAN_LIMIT))) {
            resume(xid);
        }
        for (Long xid : safe(globals.findExpiredTryingXids(now, SCAN_LIMIT))) {
            try {
                phase2Coordinator.rollback(xid);
            } catch (TccException failure) {
                log.log(Level.WARNING, "TCC timeout rollback skipped for xid " + xid
                        + ": " + failure.getCode(), failure);
            } catch (RuntimeException failure) {
                log.log(Level.SEVERE, "TCC timeout rollback failed for xid " + xid, failure);
            }
        }
    }

    private void resume(long xid) {
        TccGlobalEntity global = globals.findByXid(xid);
        if (global == null) {
            return;
        }
        try {
            GlobalStatus status = GlobalStatus.valueOf(global.getStatus());
            if (status == GlobalStatus.CONFIRMING) {
                phase2Coordinator.commit(xid);
            } else if (status == GlobalStatus.CANCELLING) {
                phase2Coordinator.rollback(xid);
            }
        } catch (TccException failure) {
            log.log(Level.FINE, "TCC retry skipped for xid " + xid
                    + ": " + failure.getCode(), failure);
        } catch (RuntimeException failure) {
            log.log(Level.SEVERE, "TCC retry failed for xid " + xid, failure);
        }
    }

    private List<Long> safe(List<Long> xids) {
        return xids == null ? List.of() : xids;
    }
}
