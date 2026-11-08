# 分布式事务与 TCC 面试思维导图

> 基线：分布式事务通用理论 + 58CRM 当前实现；消息服务另见 `58CRM-消息服务.md`。
> 范围：一致性模型、2PC/3PC/XA、TCC、SAGA、AT、Outbox、幂等、补偿、选型、58CRM 落地。
> 用法：标题层级即节点层级；每个节点按“核心结论、核心原理、关键机制、面试要点”展开。

## 0. 分布式事务总览

### 0.1.1 分布式事务到底在解决什么？

- 核心结论：一次业务操作跨多个库/服务时，本地事务无法同时保证所有资源一起成功或一起撤销。
- 核心原理
  - 服务 A 提交成功、服务 B 失败，A 的本地提交无法回滚。
  - 网络超时导致结果未知，不能直接当成功或失败。
- 关键机制
  - 全局协调、资源预留或补偿、状态持久化、失败重试。
- 面试要点
  - 引入 MQ 不等于分布式事务，核心是跨资源状态如何收敛。

### 0.1.2 分布式事务先分清哪三件事？

- 核心结论：原子性、一致性、隔离性不是同一个问题。
- 核心原理
  - 原子性：多个资源要么都成功、要么都撤销。
  - 一致性：业务约束最终是否成立。
  - 隔离性：中间态会不会被其他事务看到。
- 关键机制
  - Outbox 主要解决业务与消息的原子性。
  - TCC 解决资源预留/确认/取消，但隔离性弱。
  - Saga 解决长流程补偿，但不保证隔离。
- 面试要点
  - 很多方案只解决其中一个，不能笼统说“保证一致性”。

### 0.1.3 CAP 和 BASE 怎么答？

- 核心结论：分区发生时，强一致和可用性需要取舍；BASE 是基本可用、软状态、最终一致。
- 核心原理
  - CAP 不是简单的“三选二”口号。
  - 分布式系统必须容忍分区，讨论重点是一致性/可用性取舍。
- 关键机制
  - 强一致、线性一致、可串行化、最终一致是不同强度。
  - 业务上常选择最终一致 + 补偿/对账。
- 面试要点
  - 不要背 CAP 定义，要能结合业务说取舍。

### 0.1.4 分布式事务的失败模型有哪些？

- 核心结论：真正难的是超时、重复、乱序、崩溃和旧请求晚到。
- 核心原理
  - 节点宕机、网络超时/丢包/重复、时钟漂移、协调者宕机、参与者宕机。
  - 超时后结果未知，不能假设对方没执行。
- 关键机制
  - 状态持久化、幂等键、重试、fencing、对账。
- 面试要点
  - 正常流程谁都能讲，故障恢复才是分水岭。

## 1. 2PC、3PC 与 XA

### 1.1.1 2PC 的两个阶段是什么？

- 核心结论：Prepare 询问所有参与者能否提交，Commit/Rollback 执行最终决定。
- 核心原理
  - Prepare：参与者执行本地事务但不提交，锁住资源，回复 Yes/No。
  - 全部 Yes 才 Commit，任一 No 就 Rollback。
- 关键机制
  - 协调者统一决策。
  - 参与者 Prepare 后持有本地锁。
- 面试要点
  - 能画出协调者、参与者、Prepare、Commit 四要素。

### 1.1.2 2PC 有什么问题？

- 核心结论：同步阻塞、协调者单点、部分提交导致不一致、性能差。
- 核心原理
  - Prepare 后参与者锁资源等待协调者。
  - 协调者宕机，参与者可能一直阻塞。
  - Commit 消息部分到达，出现数据不一致。
- 关键机制
  - 超时/恢复/启发式决策都很复杂。
- 面试要点
  - 2PC 是强一致协议，但代价是锁和阻塞。

### 1.1.3 3PC 改进了什么？

- 核心结论：增加 CanCommit/PreCommit，减少参与者长期阻塞，但没有根治分区不一致。
- 核心原理
  - 三阶段：CanCommit、PreCommit、DoCommit。
  - PreCommit 后参与者知道大家大概率能提交。
- 关键机制
  - 网络分区下仍可能不一致。
  - 工程复杂度更高。
- 面试要点
  - 3PC 是缓解，不是银弹。

### 1.1.4 XA 是什么？

- 核心结论：XA 是数据库/资源管理器层面的两阶段提交规范。
- 核心原理
  - TM 协调多个 XA Resource。
  - Prepare/Commit/Rollback 由数据库驱动参与。
- 关键机制
  - 强一致、锁粒度大、性能差。
  - 适合短事务、低并发、强一致场景。
- 面试要点
  - XA 不是业务 TCC，它锁的是数据库资源。

### 1.1.5 2PC/XA 什么时候能用？

- 核心结论：参与者少、事务短、并发低、强一致要求高时可以考虑。
- 核心原理
  - 锁持有时间可控。
  - 协调者/参与者故障可运维。
- 关键机制
  - 长事务、高并发、跨互联网通常不优先选。
- 面试要点
  - 说边界，不要一刀切说“2PC 已死”。

## 2. TCC 模型

### 2.1.1 TCC 的三个角色是什么？

- 核心结论：TM 发起全局事务，TC 维护状态并调度，RM 执行 Try/Confirm/Cancel。
- 核心原理
  - TM 调 Try，决定 commit/rollback。
  - TC 管 global/branch、超时、重试、回调。
  - RM 管本地资源、barrier 和三个业务动作。
- 关键机制
  - TC 不执行业务。
  - RM 不决定全局状态。
- 面试要点
  - 能区分 TM、TC、RM，不把三者混成一个服务。

### 2.1.2 TCC 三个阶段分别做什么？

- 核心结论：Try 预留、Confirm 转正、Cancel 释放。
- 核心原理
  - 库存：Try 冻结，Confirm 扣减冻结，Cancel 解冻。
  - 优惠券：Try 锁定，Confirm 核销，Cancel 释放。
- 关键机制
  - Try 只预占，不写终态。
  - Confirm/Cancel 必须幂等。
- 面试要点
  - 用具体资源状态解释，不要只背三个单词。

### 2.1.3 Try 的职责边界是什么？

- 核心结论：Try 做检查 + 预留，不写最终业务结果。
- 核心原理
  - Try 成功不代表全局一定提交。
  - 后续可能因为其他分支失败而 Cancel。
- 关键机制
  - 预留状态要显式建模，如 frozen、locked、reserved。
  - Try 必须幂等、原子。
- 面试要点
  - Try 里直接扣终态是常见错误。

### 2.1.4 Confirm 的职责边界是什么？

- 核心结论：Confirm 把预留转正式，必须幂等且尽量不依赖临时判断。
- 核心原理
  - TC 可能重复回调。
  - Confirm 失败需要重试。
- 关键机制
  - 业务终态 + barrier + Outbox 同本地事务。
  - Confirm 不应再做“可能失败”的业务校验。
- 面试要点
  - Confirm 不是“再执行一次 Try”。

### 2.1.5 Cancel 的职责边界是什么？

- 核心结论：Cancel 释放 Try 预占，必须幂等且能处理空回滚。
- 核心原理
  - Try 可能没到、失败、超时。
  - Cancel 可能重复。
- 关键机制
  - 无 barrier 插 SUSPENDED，不执行业务释放。
  - 已 CANCELLED/SUSPENDED 直接成功。
- 面试要点
  - Cancel 不能假设 Try 一定成功。

### 2.1.6 TCC 和 2PC 的本质区别是什么？

- 核心结论：2PC 锁数据库资源，TCC 锁业务预留状态。
- 核心原理
  - 2PC 锁持有到二阶段结束。
  - TCC Try 后数据库锁可以释放，资源由业务状态表示。
- 关键机制
  - TCC 隔离性弱，需要业务补偿和隔离。
  - TCC 业务侵入高。
- 面试要点
  - 用“数据库锁 vs 业务预留”一句话区分。

### 2.1.7 TCC 的优点和代价是什么？

- 核心结论：不长期占数据库锁、可跨服务、可补偿；但业务侵入、隔离弱、三方法复杂。
- 核心原理
  - 资源提前预留，避免超卖。
  - Confirm/Cancel 可重试、可恢复。
- 关键机制
  - 需要 barrier、事务日志、重试、对账。
  - 业务要处理空回滚、悬挂、幂等。
- 面试要点
  - 说优点必须同时说代价。

## 3. TCC 工程化

### 3.1.1 global 状态机怎么流转？

- 核心结论：TRYING 进入，成功走 CONFIRMING/CONFIRMED，失败走 CANCELLING/CANCELLED。
- 核心原理
  - 全局状态只能向前。
  - 更新带状态条件，CAS 失败表示并发推进。
- 关键机制
  - FAILED 表示不可自动恢复，需要人工。
- 面试要点
  - 能画出 TRYING、CONFIRMING、CONFIRMED、CANCELLING、CANCELLED。

### 3.1.2 branch 状态机怎么流转？

- 核心结论：TRYING 后分 TRY_SUCCESS/TRY_FAIL，之后进入 Confirm 或 Cancel。
- 核心原理
  - TRY_SUCCESS 的分支才需要真正 Confirm。
  - 失败分支也要收敛到 CANCELLED。
- 关键机制
  - DEAD 表示重试耗尽。
- 面试要点
  - 分支状态和全局状态是两层，不要混。

### 3.1.3 TCC 事务日志要存什么？

- 核心结论：global、branch、barrier 三层状态都要持久化。
- 核心原理
  - global：xid、requestId、biz、status、timeout、owner lease。
  - branch：resourceKey、action、callback endpoint、status、retry。
  - barrier：RM 本地 xid+branchId 状态。
- 关键机制
  - 不能只放内存或 Redis。
- 面试要点
  - 事务日志是恢复、幂等、审计的依据。

### 3.1.4 分支注册应该谁做？

- 核心结论：目标设计由 RM 在 Try 入口自己注册，TM 只传 xid 和业务参数。
- 核心原理
  - actionName、resourceKey、callbackEndpoint 属于 RM。
  - TM 不应知道 RM 部署细节。
- 关键机制
  - TC 从 RM 注册的分支行读取回调地址。
  - 当前项目 TM 注册是过渡实现。
- 面试要点
  - 不要把当前实现当成标准 TCC。

### 3.1.5 TCC 的超时怎么设计？

- 核心结论：Try 超时、回调重试超时、全局超时要分开。
- 核心原理
  - Try 超时决定预留窗口。
  - Confirm/Cancel 用退避和上限。
  - gRPC deadline 只是单次网络超时。
- 关键机制
  - 超时后结果未知，不能假设未执行。
- 面试要点
  - 不要用一个超时时间覆盖所有阶段。

### 3.1.6 TCC 的重试怎么做？

- 核心结论：可重试错误退避重试，永久错误进入 DEAD/人工。
- 核心原理
  - 网络、RM 暂时不可用、RETRYABLE 可重试。
  - 参数错误、业务永久失败不可重试。
- 关键机制
  - next_retry_at + retry_count 持久化。
  - 进程重启后仍能恢复。
- 面试要点
  - 重试是状态机的一部分，不是 for 循环。

### 3.1.7 TC 怎么做到高可用？

- 核心结论：TC 无状态，状态在 MySQL，多实例通过 owner lease 竞争推进。
- 核心原理
  - owner_token + lease_until 标记当前 owner。
  - 旧 owner 的写被 fencing。
- 关键机制
  - Scheduler 抢租约、恢复、重试。
- 面试要点
  - 没有 lease，多 TC 会重复 Confirm/Cancel。

### 3.1.8 TCC 的性能成本在哪里？

- 核心结论：多一次 Try RPC，多一次 Confirm/Cancel RPC，多几张状态表。
- 核心原理
  - Try、Register、Report、Commit、Callback 都是网络/DB 操作。
  - barrier 和幂等表增加写入。
- 关键机制
  - 批量、连接池、异步回调、状态 CAS。
  - 不能为了性能跳过持久化。
- 面试要点
  - TCC 不是零成本，换来的是灵活的资源预留。

## 4. TCC 三防

### 4.1.1 空回滚是什么？

- 核心结论：Cancel 先到，Try 还没到。
- 核心原理
  - 没有 barrier 就没有资源可释放。
  - 直接执行 cancelAction 可能误释放。
- 关键机制
  - 插入 SUSPENDED，返回成功。
- 面试要点
  - 空回滚是“没有资源”，不是失败。

### 4.1.2 悬挂是什么？

- 核心结论：Cancel 已经处理完，Try 才迟到。
- 核心原理
  - 迟到 Try 不能再占资源。
- 关键机制
  - Try 看到 SUSPENDED/CANCELLED 返回 FAILURE。
- 面试要点
  - SUSPENDED 是防悬挂标记。

### 4.1.3 幂等怎么实现？

- 核心结论：重复 Try/Confirm/Cancel 按 barrier 状态直接返回。
- 核心原理
  - Try 已 TRIED/CONFIRMED -> SUCCESS。
  - Confirm 已 CONFIRMED -> SUCCESS。
  - Cancel 已 CANCELLED/SUSPENDED -> SUCCESS。
- 关键机制
  - 冲突状态报错，不反向覆盖。
- 面试要点
  - 幂等不是“再执行一次”，而是“识别已经执行过”。

## 5. TCC 隔离与并发

### 5.1.1 TCC 能保证强隔离吗？

- 核心结论：不能，TCC 是最终一致，中间态可能被读到。
- 核心原理
  - Try 后资源处于 frozen/locked 中间态。
  - 其他事务可能读到期中状态。
- 关键机制
  - 业务状态机、预留字段、只读终态、版本号。
- 面试要点
  - 说“TCC 没有脏读”是错误。

### 5.1.2 Try 怎么防止超卖？

- 核心结论：用原子条件更新，不要先 SELECT 再 UPDATE。
- 核心原理
  - `available >= n` 作为 UPDATE 条件。
  - 影响行数为 0 就失败。
- 关键机制
  - 预留和冻结在同一 SQL/事务。
- 面试要点
  - 并发安全靠数据库原子性，不靠应用判断。

### 5.1.3 Confirm 和 Cancel 并发怎么办？

- 核心结论：用 barrier 状态 CAS，只允许一个方向成功。
- 核心原理
  - Confirm 只能从 TRIED 转 CONFIRMED。
  - Cancel 只能从 TRIED 转 CANCELLED。
- 关键机制
  - 更新影响行数为 0 表示状态已变。
- 面试要点
  - 不能靠“先查再改”。

## 6. 其他分布式事务方案

### 6.1.1 SAGA 是什么？

- 核心结论：每个服务执行本地事务，失败时执行补偿。
- 核心原理
  - 向后恢复：反向补偿。
  - 向前恢复：重试直到成功。
- 关键机制
  - 适合长流程、服务自治。
  - 没有隔离性，补偿可能失败。
- 面试要点
  - SAGA 的补偿不是物理回滚。

### 6.1.2 AT 模式是什么？

- 核心结论：业务 SQL + 自动反向 SQL + 全局锁。
- 核心原理
  - 业务侵入低。
  - 依赖 SQL 解析和回滚日志。
- 关键机制
  - 复杂业务/多表更新难。
  - 全局锁有性能代价。
- 面试要点
  - AT 不是“无锁强一致”。

### 6.1.3 本地消息表/Outbox 是什么？

- 核心结论：业务表和消息表同一个本地事务，后台 relay 投递。
- 核心原理
  - 业务成功消息一定在 MySQL。
  - 发送失败可重扫。
- 关键机制
  - 消费端幂等。
  - 不解决资源预留。
- 面试要点
  - Outbox 解决可靠通知，不解决跨服务回滚。

### 6.1.4 事务消息是什么？

- 核心结论：半消息 + 本地事务 + Commit/Rollback + Broker 回查。
- 核心原理
  - 生产者本地事务和消息发送绑定。
  - Broker 回查本地事务状态。
- 关键机制
  - 依赖 MQ 能力。
  - 不解决消费者业务回滚。
- 面试要点
  - 和本地消息表目标相似，实现位置不同。

### 6.1.5 exactly-once 存在吗？

- 核心结论：端到端严格 exactly-once 很难；工程上用至少一次 + 幂等。
- 核心原理
  - 网络确认不可靠。
  - 外部副作用不可回滚。
- 关键机制
  - 幂等键 + 唯一约束 + 状态机。
- 面试要点
  - 不要轻易说“MQ 保证 exactly-once”。

## 7. 选型矩阵

### 7.1.1 按业务场景怎么选？

- 核心结论：短事务强一致看 2PC/XA；资源预留看 TCC；长流程看 SAGA；业务消息看 Outbox/事务消息。
- 核心原理
  - 先看是否需要资源预留。
  - 再看是否能接受最终一致。
- 关键机制
  - 复杂 SQL 自动回滚评估 AT。
  - 非核心通知用最大努力通知。
- 面试要点
  - 先问业务约束，再谈方案。

### 7.1.2 按代价怎么选？

- 核心结论：XA 锁重，TCC 侵入大，SAGA 补偿复杂，Outbox 最终一致。
- 核心原理
  - 一致性与性能、复杂度是取舍。
- 关键机制
  - 不要为了“先进”选最复杂方案。
- 面试要点
  - 能说出每种方案的代价。

## 8. TCC 设计检查清单

### 8.1.1 业务建模检查什么？

- 核心结论：资源是否能预留、Confirm 是否只转终态、Cancel 是否能释放。
- 核心原理
  - Try 原子扣减/锁定。
  - Confirm/Cancel 幂等。
- 关键机制
  - 业务状态机显式化。
- 面试要点
  - 建模不清，代码一定乱。

### 8.1.2 异常检查什么？

- 核心结论：空回滚、悬挂、重复、超时、TC/RM 崩溃、callback 不可达。
- 核心原理
  - 每个异常都要有状态和恢复路径。
- 关键机制
  - barrier、事务日志、Scheduler、对账。
- 面试要点
  - 不要只画成功流程。

## 9. 58CRM 项目背景

### 9.1.1 58CRM 为什么需要 TCC？

- 核心结论：线索分配同时涉及 quota 和 coupon 等多个服务，本地事务管不了跨服务资源。
- 核心原理
  - 一个 RM 成功、另一个失败，已预占资源必须撤销。
- 关键机制
  - 成功统一 Confirm，失败 Cancel 已成功分支。
- 面试要点
  - 用“A 成功、B 失败、撤销 A”讲，不要只说用了 TCC。

### 9.1.2 为什么不用普通 MQ？

- 核心结论：MQ 没有资源预留和撤销协议。
- 核心原理
  - MQ 是事后通知，不是事前占位。
  - 消费失败不能阻止超卖。
- 关键机制
  - TCC 管资源，Outbox 管通知。
- 面试要点
  - 两者是分工，不是替代。

## 10. 模块地图

### 10.1.1 TCC 模块怎么分？

- 核心结论：api、protocol、client、participant、coordinator、id、starter、examples 分层。
- 核心原理
  - api 只放业务契约和注解。
  - protocol 放 gRPC stub 和内部 DTO。
  - client/participant/coordinator 各自依赖 api/protocol。
- 关键机制
  - starter 按 client/participant 角色开关。
- 面试要点
  - 包边界是 public/protocol/internal 三层。

### 10.1.2 消息模块怎么分？

- 核心结论：outbox 生产、consumer 消费、starter 装配。
- 核心原理
  - outbox 负责业务表+消息表+relay。
  - consumer 负责 Stream/PEL/幂等。
- 关键机制
  - 一条业务线一个 Stream/消费组。
- 面试要点
  - 不要把生产端和消费端混成一个模块。

## 11. 架构设计

### 11.1.1 逻辑架构是什么？

- 核心结论：业务应用 TM、独立 Coordinator、多个 RM、MySQL 状态、Redis Stream 消息。
- 核心原理
  - TM 调 Try，TC 管状态，RM 执行三阶段。
  - MySQL 是正本，Redis 是传输。
- 关键机制
  - TC 不发现 RM，回调地址由分支元数据提供。
- 面试要点
  - 能画出 TM/TC/RM/MySQL/Redis 五块。

### 11.1.2 传输矩阵是什么？

- 核心结论：TM→TC gRPC；TM→RM Try OpenFeign；RM→TC 注册/上报 gRPC；TC→RM Confirm/Cancel gRPC。
- 核心原理
  - Try 是业务 API。
  - Confirm/Cancel 是 TCC 协议。
- 关键机制
  - 不要用 TC 发现 RM。
- 面试要点
  - 能说清每条链路的发起方。

## 12. 目标流程

### 12.1.1 成功链路怎么走？

- 核心结论：TM begin → RM Try → RM 自注册分支 → RM barrier/业务预留 → 上报成功 → TM commit → TC 回调 Confirm。
- 核心原理
  - Try 由 TM 通过业务 API 调用。
  - 分支注册由 RM 在 Try 入口完成。
- 关键机制
  - TC 从分支表读取回调地址。
- 面试要点
  - 流程必须是 RM 自注册，不是 TM 代注册。

### 12.1.2 失败链路怎么走？

- 核心结论：quota Try 成功、coupon Try 失败 → coupon 上报 TRY_FAIL → TM rollback → TC Cancel quota → coupon 空回滚 SUSPENDED。
- 核心原理
  - 已成功分支必须撤销。
  - 失败分支没有 barrier，Cancel 插 SUSPENDED。
- 关键机制
  - 最终 global CANCELLED。
- 面试要点
  - 这是 TCC 最核心的价值场景。

### 12.1.3 当前仓库和目标设计差在哪？

- 核心结论：当前 TM 的 `@TccBranch` 先注册分支；目标由 RM 自注册。
- 核心原理
  - 当前 callbackEndpoint 写在 TM，耦合部署细节。
- 关键机制
  - 这是过渡实现，不是标准 TCC。
- 面试要点
  - 能主动说出设计债。

## 13. 状态机与数据

### 13.1.1 global/branch/barrier 的关系是什么？

- 核心结论：global 管全局，branch 管 TC 侧分支，barrier 管 RM 本地幂等/防悬挂。
- 核心原理
  - global/branch 在 TC 库。
  - barrier 在 RM 业务库。
- 关键机制
  - branch 状态推进不等于 RM 业务状态。
- 面试要点
  - 三张表职责不要混。

### 13.1.2 branchId 的作用域是什么？

- 核心结论：branchId 只在 xid 内唯一，真正标识是 `(xid, branchId)`。
- 核心原理
  - `xid=1` 和 `xid=2` 都可以有 branchId=1。
- 关键机制
  - 日志必须打 `xid:branchId`。
- 面试要点
  - 不是全局 ID，不是主键冲突。

## 14. Barrier 三防

### 14.1.1 58CRM 怎么处理空回滚？

- 核心结论：Cancel 无 barrier 时插 SUSPENDED，返回成功。
- 核心原理
  - 没有 Try 就没有资源。
  - 不执行 cancelAction。
- 关键机制
  - coupon Try 失败回滚 barrier，Cancel 空回滚。
- 面试要点
  - SUSPENDED 是防悬挂标记。

### 14.1.2 58CRM 怎么处理悬挂？

- 核心结论：迟到 Try 看到 SUSPENDED/CANCELLED 直接 FAILURE。
- 核心原理
  - Cancel 已处理过，不能再占资源。
- 关键机制
  - barrier 状态检查先于业务 Try。
- 面试要点
  - 没有它，资源会在事务结束后被重新占用。

## 15. 消息边界

### 15.1.1 TCC 和 Outbox 怎么配合？

- 核心结论：Confirm 的本地事务里写业务终态 + barrier + Outbox。
- 核心原理
  - TCC 管资源状态。
  - Outbox 管终态通知。
- 关键机制
  - 消息由 MySQL relay 到 Redis Stream。
- 面试要点
  - 框架不自动生成业务事件。

## 16. 注解 API 与 AOP

### 16.1.1 `@TccTransactional` 做什么？

- 核心结论：标记 TM 全局事务入口，切面负责 begin/commit/rollback。
- 核心原理
  - requestId 是幂等键。
  - bizCode/bizId 用于业务定位。
- 关键机制
  - 进入方法体前已经 begin 并绑定 xid。
- 面试要点
  - 它不等于本地 `@Transactional`。

### 16.1.2 为什么注解类都是代理对象？

- 核心结论：`LeadAssignService` 被 `@TccTransactional` 代理，两个 Gateway 被 `@TccBranch` 代理。
- 核心原理
  - Spring AOP 靠代理拦截。
  - 同类自调用绕过代理。
- 关键机制
  - Starter 里 `@EnableAspectJAutoProxy` 强制 CGLIB。
- 面试要点
  - 业务代码看不到 `@Enable`，但代理确实存在。

## 17. 设计债

### 17.1.1 当前最大的设计债是什么？

- 核心结论：TM 代 RM 注册分支、TM 配置 callbackEndpoint。
- 核心原理
  - actionName/resourceKey/地址属于 RM。
- 关键机制
  - 目标改为 RM 自注册，TM 只传 xid + payload。
- 面试要点
  - 能说出为什么这是债，而不是照着背。

### 17.1.2 还有哪些边界？

- 核心结论：嵌套 TCC 不支持、begin 不自动重试、TccContext 只表示当前分支。
- 核心原理
  - 同线程不能再开全局事务。
  - branchId 会被后续分支覆盖。
- 关键机制
  - begin 靠 requestId 幂等，但当前没重试。
- 面试要点
  - 这些是当前边界，不是“完美实现”。

## 18. 06 文档 66 问

> 题目来自 `06-项目文档/58CRM_-_系统分布式事务项目_.md` 的 1-66；答案按当前仓库重写，不照抄原答案。

### 18.1.1 TCC 与项目 1-22

- 1. 整体分几块？→ TCC 协调器、TM 客户端、RM、Outbox、Stream 消费、Starter。
- 2. 消息从生产到消费？→ 业务事务写 Outbox → Scanner → Buffer → Sender → Stream → Consumer → XACK。
- 3. MySQL 和 Redis 各放什么？→ MySQL 正本/日志/幂等/barrier；Redis Stream 传输。
- 4. 为什么两个载体？→ MySQL 保可靠，Redis 保吞吐。
- 5. 三阶段谁执行？→ TM 发起，RM 执行，TC 回调。
- 6. 协调器管什么？→ global/branch、lease、超时、重试、回调。
- 7. 切面算哪层？→ TM 接入层。
- 8. 切面内部流程？→ 解析 → begin → 业务 Try → commit/rollback。
- 9. 多业务线怎么隔离？→ bizCode、resourceKey、Stream/消费组。
- 10. 最终一致性校验在哪？→ Coordinator Scheduler；对账未落地。
- 11. Try 预留什么？→ quota reserved、coupon LOCKED。
- 12. Confirm 做什么？→ 转终态 + barrier + Outbox。
- 13. Cancel 做什么？→ 释放预占。
- 14. Try 防重？→ barrier 主键 + 状态。
- 15. Confirm 幂等？→ barrier CONFIRMED 直接成功。
- 16. XID 怎么生成？→ MySQL 号段。
- 17. 事务日志记什么？→ global/branch/barrier 状态。
- 18. 两个 RM 一成功一失败？→ 失败上报 → rollback → Cancel 成功分支。
- 19. 成功资源怎么办？→ Cancel 释放。
- 20. 中间态对外？→ 业务状态机控制，不直接暴露。
- 21. 超时怎么定？→ Try/回调/全局分开。
- 22. 空回滚？→ 无 barrier 插 SUSPENDED。

### 18.1.2 消息 23-54

- 23. 消息何时产生？→ 业务事实同本地事务写 Outbox。
- 24. 第一步写哪？→ MySQL `t_local_message`。
- 25. 怎么绑定？→ 同本地事务。
- 26. 业务回滚？→ Outbox 一起回滚。
- 27. 消息字段？→ msgId/eventKey/biz/payload/status。
- 28. Redis 结构？→ Stream。
- 29. 为什么不用 List？→ 无消费组/PEL/ACK。
- 30. 推 Redis 失败？→ 保持 INIT，重扫。
- 31. 批量推送？→ Sender 批量 XADD。
- 32. 合并窗口？→ linger 10ms 默认，按吞吐调。
- 33. 消费方式？→ XREADGROUP。
- 34. 为什么批量拉？→ 减少 RTT。
- 35. 一批多少？→ 默认 500，按压测调。
- 36. 回调前做什么？→ 落消费记录/幂等/租约。
- 37. 重复消费第一道防线？→ msgId/eventKey + MySQL 唯一约束。
- 38. 占位多久？→ 按处理时间，当前 MySQL 幂等不做 TTL。
- 39. 设短问题？→ 重复执行风险。
- 40. 兜底？→ 唯一约束 + 消费记录。
- 41. 唯一约束在哪？→ `t_idempotent`。
- 42. 占位后崩溃？→ PEL/租约恢复。
- 43. 消费失败去哪？→ 重试/DEAD。
- 44. 重试几次？→ 默认 3 次。
- 45. 到头去哪？→ DEAD/失败记录。
- 46. 多消费者只消费一次？→ 消费组 + PEL + 幂等。
- 47. 消息正本表？→ `t_local_message`。
- 48. 索引？→ 状态+重试时间、eventKey 唯一。
- 49. 表一直涨？→ 归档/清理，当前未做。
- 50. Redis key？→ 按业务线 Stream。
- 51. Redis/MySQL 不一致听谁？→ MySQL 正本。
- 52. Redis 挂了会丢吗？→ Outbox 还在，可重放。
- 53. 从哪补？→ Scanner 扫 INIT。
- 54. 幂等为什么两边？→ 原项目 Redis+MySQL；本仓库只 MySQL，Redis 仅传输。

### 18.1.3 接入与性能 55-66

- 55. 注解属性？→ requestId/bizCode/bizId/tryTimeoutSeconds。
- 56. AOP 找 Confirm/Cancel？→ RM `@TccActionHandler(actionName)`。
- 57. 方法签名？→ BusinessActionContext + TccTryResult/TccPhaseResult。
- 58. 接口直接调用场景？→ 动态分支/TccRequest。
- 59. 两种方式同底层？→ 是，最终走 Coordinator + barrier。
- 60. 接入写什么？→ `@TccTransactional` + RM TccAction；目标设计下分支由 RM 自注册。
- 61. 8W 场景？→ 原项目促销/开工季口径。
- 62. 日常/峰值？→ 原项目 1.5W/8W；本仓库无生产数据。
- 63. 压测？→ 本仓库本机约 700 msg/s。
- 64. 瓶颈？→ 本机 DB/Redis/线程模型，无生产结论。
- 65. 提升吞吐？→ 批量、缓冲、异步、减少 RTT。
- 66. 积压发现？→ Stream lag、PEL、监控。

## 19. 讨论过的设计点

### 19.1.1 分支注册职责

- 核心结论：目标 RM 自注册，当前 TM 代注册是过渡。
- 核心原理：元数据属于 RM。
- 关键机制：TM 只传 xid + payload。
- 面试要点：主动说设计债。

### 19.1.2 TM 配置 callbackEndpoint

- 核心结论：TM 不应知道 RM 地址。
- 核心原理：callback 属于 RM 注册元数据。
- 关键机制：TC 从 branch 行读取。
- 面试要点：这是当前实现的问题。

### 19.1.3 AOP 代理入口

- 核心结论：Starter 隐藏了 `@EnableAspectJAutoProxy`。
- 核心原理：`LeadAssignService`/Gateway 都是代理。
- 关键机制：强制 CGLIB 可能覆盖用户配置。
- 面试要点：能解释为什么业务代码看不到。

### 19.1.4 嵌套 TCC 与 TM 兼 RM

- 核心结论：TM 兼 RM 不冲突；同线程嵌套全局 TCC 不支持。
- 核心原理：RM 路径不走 `TccTransactionManager`。
- 关键机制：嵌套 begin 直接抛异常。
- 面试要点：区分角色和线程。

### 19.1.5 TccContext 覆盖

- 核心结论：branchId 只表示当前分支，会被后续分支覆盖。
- 核心原理：分支必须串行。
- 关键机制：历史 branchId 在 branch 表里。
- 面试要点：不支持并行/异步传播。

### 19.1.6 begin 重试与 branchId 作用域

- 核心结论：begin 当前不自动重试；branchId 只在 xid 内唯一。
- 核心原理：requestId 幂等；`(xid, branchId)` 才是完整键。
- 关键机制：日志打 `xid:branchId`。
- 面试要点：不要把 branchId 当全局 ID。

## 20. 白板顺序

### 20.1.1 面试白板怎么画？

- 核心结论：先角色，再状态，再成功/失败，最后故障和取舍。
- 核心原理
  - 画 TM/TC/RM 三块。
  - 画 global/branch/barrier。
  - 画成功链路。
  - 画 quota 成功、coupon 失败。
- 关键机制
  - 最后补 owner lease、重试、设计债。
- 面试要点
  - 不要一上来讲源码。

## 21. 一句话

### 21.1.1 用一句话总结

- 核心结论：TCC 用业务预留和补偿换灵活，Outbox 用最终一致换可靠通知，选型本质是一致性、隔离性、复杂度和性能的取舍。
- 核心原理：没有免费的一致性。
- 关键机制：RM 自注册、barrier 三防、状态机、重试、对账。
- 面试要点：能说清代价，比背协议更加分。
