# Microsphere Gateway 动态端点路由知识大纲（gateway 触发面）

> 来源：`mapping/09-microsphere-gateway.md` 5 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + my-xhs 落地实证
> 用途：①自学/面试知识图谱（SCG 动态端点路由）②与课程 L1 合并成 L3 的源码侧素材 ③my-xhs 对照
> 覆盖核对：5/5 KP 全部归属（文末核对表）+ G1-G15 缺陷表 10/15 已证

---

## 一、we:// 端点粒度动态路由（核心命题）[分布式问题]

> **核心命题**：官方 SCG `lb://service-name` 只能到服务粒度（不分端点）；microsphere `we://` 协议端点粒度（`POST /users` 限流 10QPS/`GET /users` 不限流）——**端点级路由 + 方法级过滤**。

### 1.1 we:// 协议 + URI 重写链 [🔴 P1] [时间无关模式]
- **来源**：KP-802（WebEndpointMappingGlobalFilter）
- **机制**：**多接口过滤器**（GlobalFilter + SmartApplicationListener + ApplicationContextAware :107——路由缓存刷新与请求拦截合一）+ **uriTemplateVariables 取应用名**（:142-143）+ **URI 重写**（实例地址 + 去应用名前缀 rewritePath :333 + targetURI :158）+ **端点映射构建**（getSubscribedServices → choose 样本实例 :280 → getWebEndpointMappings :281——**单实例取样**（G5——金丝雀场景路由表不完整）+ TODO ZonePreferenceFilter :279——**06 仓库能力缺口**）
- **事件刷新**：ContextRefreshedEvent/RefreshRoutesResultEvent/EnvironmentChangeEvent/ServiceInstancesChangedEvent 四事件（REQ-002）
- **缺陷**：G2 supportsAsyncExecution=false（:170——事件循环阻塞）/ G4 无实例 NPE（:277-278 无防御）/ G15 静默 200（未命中无日志）
- **my-xhs**：该用没用——官方 SCG lb:// 服务粒度；端点级限流为差距

### 1.2 端点排除过滤 [🔴 P1] [时间无关模式]
- **来源**：KP-801（WebEndpointConfig）
- **机制**：**六维度过滤**（patterns/methods/params/headers/consumes/produces——Mapping 内部类 :57-130）+ **methods 空 = 全部**（:74-77）+ **配置双来源**（Environment 绑定 :62 + 注册 metadata 绑定 :74——**服务侧 metadata 驱动网关**（03 能力链路））
- **缺陷**：G14 绑定失败静默（onFailure 未实现——excludes 格式错误无日志）
- **my-xhs**：该用没用——端点排除配置为差距

---

## 二、Filter 链缓存（性能优化）[性能优化]

### 2.1 缓存 + 事件重建 [🔴 P1] [时间无关模式]
- **来源**：KP-803（CachingFilteringWebHandler）
- **机制**：**覆写 handle() 缓存**（:66——ConcurrentHashMap<routeId, GatewayFilter[]>）+ **@EventListener(RefreshRoutesResultEvent) 重建**（:79-80——路由变更才重建）+ **BDRPP 替换官方 Bean**（FilteringWebHandlerBeanDefinitionRegistryPostProcessor :39——Bean 定义级覆盖）
- **缺陷三连**：G7 空链吞请求（DefaultGatewayFilterChain:58 empty()——缓存未建立窗口无响应）/ G8 反射脆弱（:122 getFieldValue globalFilters——SCG 升级静默失败）/ G9 dispose 阻塞（:89-93——Redis 路由仓库消费不完整）
- **my-xhs**：该用没用——高并发 Filter 链缓存优化；三坑教训可借鉴

---

## 三、路由刷新事件控制 [分布式问题]

### 3.1 事件传播 + 心跳禁用 [🔴 P1] [时间无关模式]
- **来源**：KP-804（PropagatingRefreshRoutesEventApplicationListener + DisabledHeartbeatEventRouteRefreshListenerInterceptor）
- **机制**：**EnvironmentChangeEvent 转发刷新**（:20）+ **监听器拦截器禁用心跳刷新**（:41——拦截 SCG RouteRefreshListener 的 HeartbeatEvent/ParentHeartbeatEvent :60-61——**官方刷新通道控制**）
- **缺陷 G1（断链根源）**：**禁用官方心跳刷新但无 ServiceInstancesChangedEvent 发布者**——端点表永不更新（滚动发布断链）——**禁用通道必须配替代发布者**
- **G13 死代码**：GatewayUtils 两方法仅测试引用（:54/:67）
- **my-xhs**：该用没用——Nacos 场景无心跳问题；配置变更刷新传播可借鉴

---

## 四、双栈网关 [工程问题]

### 4.1 WebFlux/WebMVC 双实现 [🔴 P2] [时间无关模式]
- **来源**：KP-805
- **机制**：**webflux GlobalFilter** vs **webmvc HandlerSupplier/HandlerFilterFunction**（:83——官方 SGC Server MVC SPI——**同一能力双栈实现**）+ **excludes → RequestMappingInfo 集**（:178——MVC 端点匹配模型）
- **my-xhs**：不该用——官方 SCG WebFlux 单栈

---

## 覆盖核对（5/5 + G 表）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、we:// 动态路由 | 801,802 | 2 |
| 二、Filter 链缓存 | 803 | 1 |
| 三、刷新事件控制 | 804 | 1 |
| 四、双栈网关 | 805 | 1 |

**去重后唯一 KP**：801-805 全部 = **5/5 ✓**
**无孤儿 KP** ✓
**G1-G15 进度**：10/15 已证（G1/G2/G3/G4/G5/G7/G8/G9/G13/G14/G15）；待验证 5（G6/G10/G11/G12——MVC 静态缓存/metadata 协议/双缓存窗口）
**my-xhs 对照**：该用没用 4 / 不该用 1——**端点粒度路由是真实差距**；G 表教训对官方 SCG 升级有警示价值。

---

## 五、问题域知识（历史 16 篇分析对照）[分布式问题/工程问题]

### 5.1 metadata 协议链路 [🔴 P1] [时间无关模式]
- **来源**：KP-806（16-07）
- **机制**：**序列化协议**（toJSON 拼接 :87 → 单条 metadata 附加 :74 → 网关 parseWebEndpointMappings :207 → **microsphere_wem_id 头 → 下游反查 registry**——三方协议）；**id 语义**（endpoint.hashCode() :1271——跨 JVM 不稳定——**进程内配对成立**——不可当跨环境指纹）
- **协议隐患三连**：G11 无分片（超注册中心长度限制）/ G12 无版本号（Kind.valueOf 解析抛错）/ G3 字段脱节（context-path 发布不消费）
- **my-xhs**：该用没用——Nacos 官方 metadata 覆盖；协议设计教训可借鉴

### 5.2 演进史与重构损失 [🟡 P2] [时间无关模式]
- **来源**：KP-807（16-02）
- **机制**：三阶段演进（2a40e27 → f227106 重构 → 演进）+ **重构损失清单**——buildPath/GatewayUtils 死代码 = **重构残留非笔误**——**读码先读史**
- **my-xhs**：不该用——分析方法可借鉴

### 5.3 与官方 SCG 集成关系 [🟡 P2] [时间无关模式]
- **来源**：历史 16-01 :204 + 本仓库 KP-803
- **机制**：**微球动了官方哪些组件**（FilteringWebHandler→CachingFilteringWebHandler（BDRPP 替换）/RouteRefreshListener 心跳拦截（DisabledHeartbeat 拦截器）/GlobalFilter 链追加（order=10149——16-03 :7））——**官方组件替换/拦截清单**
- **my-xhs**：该用没用——官方 SCG 升级时的兼容警示

---

## 覆盖核对（7/7 + 对照）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、we:// 动态路由 | 801,802 | 2 |
| 二、Filter 链缓存 | 803 | 1 |
| 三、刷新事件控制 | 804 | 1 |
| 四、双栈网关 | 805 | 1 |
| 五、问题域知识 | 806,807 | 2 |

**去重后唯一 KP**：801-807 全部 = **7/7 ✓**
**无孤儿 KP** ✓
**G1-G15 进度**：**15/15 全部证实**（本轮补 G6/G10/G11/G12）——缺口表清零
**my-xhs 对照**：该用没用 4 / 不该用 1——端点粒度路由为真实差距；协议设计教训可借鉴。
