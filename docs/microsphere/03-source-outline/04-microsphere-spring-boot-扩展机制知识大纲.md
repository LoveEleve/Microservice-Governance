# Microsphere Spring Boot 扩展机制知识大纲（spring-boot 触发面）

> 来源：`mapping/04-microsphere-spring-boot.md` 17 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + 现代替代
> 用途：①自学/面试知识图谱（Spring Boot 扩展机制）②与课程 L1 合并成 L3 的源码侧素材
> 覆盖核对：17/17 KP 全部归属（文末核对表）

---

## 一、条件注解扩展（Boot 条件机制的补全）[工程问题]

> **核心命题**：Spring Boot 条件注解（@ConditionalOnXxx）的**缺失场景补全**——前缀条件/可选依赖条件/Web 栈条件。

### 1.1 `@ConditionalOnPropertyPrefix` 属性前缀条件 [🔴 P1] [时间无关模式]
- **来源**：KP-301
- **机制**：注解 + Condition + getMatchOutcome 三件套（SpringBootCondition 标准生命周期）；**前缀 startsWith 匹配**（:78——属性族/命名空间匹配）
- **差异精确定位**：官方 @ConditionalOnProperty 的 name 是 **String[] 数组 + 精确属性名匹配**（官方源码实证 ConditionalOnProperty.java:128）；microsphere 是**前缀匹配**——**差异在"前缀 vs 精确名"非数量**
- **my-xhs**：该用没用——命名空间级配置开关

### 1.2 可选依赖条件（@ConditionalOnClass + Web 栈探测）[🟡 P2] [时间无关模式]
- **来源**：KP-313/315
- **机制**：@ConditionalOnActuatorEndpointPresent（按类名条件 :42）/@ConditionalOnWebFluxAvailable/@ConditionalOnWebMvcAvailable（Web 栈探测）——**可选依赖不引入则跳过**
- **my-xhs**：该用没用——可选依赖自动配置；官方 @ConditionalOnClass 覆盖

---

## 二、启动生命周期扩展 [工程问题]

> **核心命题**：Boot 启动过程（RunListener/ApplicationPreparedEvent）的可观测性与幂等处理。

### 2.1 RunListener 生命周期适配 [🔴 P1] [时间无关模式]
- **来源**：KP-308（SpringApplicationRunListenerAdapter + Logging 变体 + FailureReport）
- **机制**：**RunListener 适配器模板**（8 回调空实现——子类只覆写需要的 :20）+ Logging 生命周期日志变体（:157-159——"started : {context}" 格式化）+ FailureReport 失败报告
- **my-xhs**：该用没用——启动钩子/埋点；官方 RunListener 是扩展点

### 2.2 一次性启动监听幂等 [🔴 P2] [时间无关模式]
- **来源**：KP-302（OnceApplicationPreparedEventListener + OnceMain/LoggingOnce 变体）
- **机制**：**按 contextId 幂等**（静态 Map 类级共享 :48/:96——同一监听器类所有实例共享处理记录——多 context 防重）+ final 模板方法（:90）+ 主 context 专用变体
- **my-xhs**：该用没用——启动一次性处理；官方 Boot 无此幂等

### 2.3 条件评估报告 + 启动诊断 [🔴 P2] [时间无关模式]
- **来源**：KP-304（ConditionEvaluationReportBuilder）/ KP-305（ArtifactsCollisionFailureAnalyzer）
- **机制**：**条件报告主动构建**（Builder + Listener/Initializer 采集 + MessageBuilder 人类可读 + SpringBootExceptionReporter 异常时输出——官方 debug=true 才有）；**FailureAnalyzer 三件套**（Exception + Analyzer + DiagnosisListener——构件冲突友好报错）
- **my-xhs**：该用没用——启动诊断（自动配置排查）

---

## 三、配置绑定扩展（Binder 机制）[分布式问题]

> **核心命题**：Spring Boot Binder 的**过程监听与事件化**——配置热更新的 Boot 级基础设施。

### 3.1 绑定监听 + 属性变更事件化 [🔴 P1] [时间无关模式]
- **来源**：KP-303（ListenableConfigurationPropertiesBindHandlerAdvisor + bind 子包）
- **机制**：**BindListener SPI 三回调**（onStart/onSuccess/onFailure——测试实证 :65/:69）+ **ConfigurationPropertiesBindHandlerAdvisor 官方扩展点**（wrap BindHandler :74）+ 属性变更事件（ConfigurationPropertiesBeanPropertyChangedEvent）
- **生态呼应**：microsphere-spring KP-213（配置变更事件）的 Boot 级升级
- **my-xhs**：该用没用——配置绑定监控/热更新（对接 Nacos）

### 3.2 @Enable 扩展外壳 [🟡 P2] [时间无关模式]
- **来源**：KP-306（EnableConfigurationPropertiesExtension + Registrar）
- **机制**：@OverrideAnnotationAttributes + @Import(Registrar)（:85-86——KP-211 家族复用）+ 开关属性（adviseBindListener/publishEvents :99/:116）+ 三源枚举（:132）
- **my-xhs**：该用没用——配置绑定监听开关

### 3.3 元数据读取 + 自动配置排除 + 默认属性 + 绑定配套 [🔴 P1] [时间无关模式]
- **来源**：KP-307（ConfigurationMetadataReader）/ KP-310（ConfigurableAutoConfigurationImportFilter）/ KP-311（默认属性家族）/ KP-309（绑定配套）
- **机制**：**编译期元数据读取**（spring-configuration-metadata.json 双路径 :49-51——三阶段闭环的 Boot 版）；**AutoConfigurationImportFilter 官方扩展点**（:53——自动配置排除——@ConfigurationProperty 注解属性落地 :55-59）；**默认属性家族**（DefaultPropertiesApplicationListener——ApplicationEnvironmentPreparedEvent 时从多源合并默认属性 :31-50 + OriginTrackedConfigurationPropertyInitializer——origin 跟踪 + BeanFactoryListenerAdapter 复用）；**绑定配套**（BindableConfigurationBeanBinder——microsphere-spring KP-220 的 Boot 级版 + SpringBootVersion 版本判断）
- **my-xhs**：该用没用——自动配置排除/默认属性；官方 spring.autoconfigure.exclude 覆盖排除

---

## 四、Actuator 与兼容层 [工程问题]

### 4.1 生态能力 Actuator 化 [🔴 P2] [时间无关模式]
- **来源**：KP-312（ArtifactsEndpoint + ConfigurationMetadataEndpoint + ConfigurationPropertiesEndpoint）
- **机制**：**内部工具 → Actuator 端点**（构件探测 /actuator/artifacts :18/:48——运维监控生产化路径；配置元数据 /actuator/configMetadata）
- **my-xhs**：该用没用——classpath 审计端点/依赖冲突运维排查

### 4.2 监控线程池调度器 [🔴 P2] [时间无关模式]
- **来源**：KP-316（MonitoredThreadPoolTaskScheduler）
- **机制**：**createExecutor 工厂钩子双轨设计**（:82-84——内部原对象 + 外部可监控委托 DelegatingScheduledExecutorService——监控透传）
- **my-xhs**：该用没用——线程池监控（任务队列/拒绝统计）

### 4.3 Boot 3 兼容层（同名同包重实现）[🔴 P2] [过时→Boot 3.2 官方]
- **来源**：KP-314（BootstrapContext + BootstrapRegistry 家族）
- **机制**：`package org.springframework.boot` **同名同包重实现**（Boot 2 项目用 Boot 3 API——版本迁移兼容策略）+ 五方法完整契约（get/getOrElse/getOrElseSupply/getOrElseThrow/isRegistered :45-90）
- **my-xhs**：该用没用——Boot 2→3 迁移参考；官方 3.2 已内建

### 4.4 测试基座（boot-test）[🟢 P3] [时间无关模式]
- **来源**：KP-317（AutoConfigurationTest 家族）
- **机制**：ApplicationContextRunner 泛型化测试基类（:33——自动配置加载测试）
- **my-xhs**：该用没用——自动配置测试；官方 @SpringBootTest 覆盖

---

## 覆盖核对（17/17）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、条件注解扩展 | 301,313,315 | 3 |
| 二、启动生命周期 | 302,304,305,308 | 4 |
| 三、配置绑定 | 303,306,307,309,310,311 | 6 |
| 四、Actuator/兼容 | 312,314,316,317 | 4 |

**去重后唯一 KP**：301-317 全部 = **17/17 ✓**（KP-309/311 归三；KP-317 归四）
**无孤儿 KP** ✓
