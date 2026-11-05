# Redisson Lab

Redisson Book 的配套用法工程。**只展示怎么接线/怎么用，不复制 Redisson 源码；不要求默认启动。**

对应 Book：`/data/workspace/source-code/xhs-demo/docs/Java Core Interview/Redisson/book/README.md`。

## 代码地图

| 类 | 对应章节 | 展示点 |
|---|---|---|
| `BasicLockLab` | 02 定位/03 API | `getLock`、`lock`、`try/finally/unlock` |
| `TryLockLab` | 03 RLock API | `tryLock(waitTime, leaseTime, unit)`、失败降级 |
| `WaitLockLab` | 06 解锁/等待 | 两个线程：持锁者 + 有界等待者 |
| `ReentrantLockLab` | 04 加锁/重入 | 同线程重入、`getHoldCount`、配对 unlock |
| `WatchdogLockLab` | 05 watchdog | `lock()` 默认续期 vs 显式 lease |
| `FairReadWriteLab` | 07 公平锁/读写锁 | `getFairLock`、`getReadWriteLock` |
| `SpinLockLab` | 07 SpinLock | `getSpinLock`，BackOff 轮询（非忙自旋） |
| `MultiLockLab` | 08 MultiLock | 多把锁组合、统一加/解锁 |
| `FencedLockLab` | 08 FencedLock | `lockAndGetToken`、fencing token |
| `SemaphoreLatchLab` | 09 同步器 | `RSemaphore`、`RCountDownLatch` |
| `PermitExpirableLab` | 09 PermitExpirable | `acquire(lease)` 返回 permitId、按 id release |

## 运行边界

- 默认连接 `redis://127.0.0.1:6379`，需要本地/远程 Redis；本工程不自动启动 Redis。
- 示例是 `main` 方法，按需单独运行，避免一个进程把所有场景混在一起。
- `WatchdogLockLab` 会持锁约 40 秒，只用于观察续期，不要在共享环境跑。
- 生产使用请替换锁名/租约/重试策略，并保留 `try/finally`。

## 为什么统一放在 Microservice-Governance/labs

- 与 `microsphere-spring-cloud-lab` 同一聚合工程，IDEA 打开根目录即可看到；
- Book 里的使用示例指向本模块，不再散落在临时 demo；
- 后续 Redisson 章节的 lab 直接加类/README，不新建独立工程。
