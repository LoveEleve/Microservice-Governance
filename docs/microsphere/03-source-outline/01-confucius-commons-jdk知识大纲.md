# JDK 内部机制知识大纲（confucius-commons 触发面）

> 来源：`mapping/01-confucius-commons.md` 35 个 KP 的知识本体提炼
> 性质：**源码侧 outline（L1.5）**——mapping 是文件映射（过程），本大纲是知识本体（结果）
> 组织：按知识维度，非按文件；每个知识点标注来源 KP + 六元元数据 + 现代替代
> 用途：①自学/面试知识图谱 ②与课程 L1 合并成 L3 的源码侧素材 ③后续仓库 outline 的合并模板
> 覆盖核对：35/35 KP 全部归属（每节末标注 KP 清单）

---

## 一、类加载机制（JVM 核心）[工程问题]

> **核心命题**：类加载是 JVM 的"血管"——所有框架魔法（SPI/扫描/热部署）的底层。confucius 用反射触达了 JDK 不公开的类加载内部。

### 1.1 ClassLoader 继承链与双亲委派 [🔴 P1] [时间无关模式]
- **来源**：KP-01（:176-189 继承链遍历）
- **需求**：类可能由任一父加载器加载——查"某类是否已加载"必须遍历整条链
- **机制**：双亲委派（parent-first）→ 子加载器请求先委托父；`ClassLoader.getParent()` 链上任意节点可能已加载目标类
- **现代替代**：无公开 API（诊断用 jcmd/Arthas）
- **my-xhs**：不该用（诊断工具覆盖）

### 1.2 已加载类注册表：`ClassLoader.classes` 字段 [🔴 P2] [过时→--add-opens]
- **来源**：KP-02（:418-427）
- **机制**：每个 ClassLoader 维护已加载类列表——JDK11 `Vector<Class<?>>`（ClassLoader.java:313）→ JDK17 `ArrayList<Class<?>>`（:311）——**内部结构已改型一次**（版本脆弱性实证）
- **现代替代**：反射读字段需 `--add-opens java.base/java.lang`；生产用 `jmap -histo` 类统计
- **my-xhs**：不该用

### 1.3 `findLoadedClass` 反射桥 [🔴 P2] [过时→--add-opens]
- **来源**：KP-01（:49-58）
- **机制**：JDK17 `ClassLoader.java:1282` `protected final findLoadedClass` + `:1288` `private native findLoadedClass0`——Java 侧唯一入口是反射（JDK 未公开）
- **现代替代**：同 1.2——`--add-opens` 放行 + jcmd
- **my-xhs**：不该用

### 1.4 类加载统计：`ClassLoadingMXBean` [🟢 P3] [有效]
- **来源**：KP-03（:39,73-119）
- **机制**：标准 JMX——已加载/卸载计数、verbose 开关（`setVerbose` 等价 `-verbose:class` 运行时控制）
- **my-xhs**：不该用（薄封装无增量）

### 1.5 ClassPath 结构解析 [🟡 P3] [有效]
- **来源**：KP-06（ClassPathUtils:30-54）
- **机制**：`RuntimeMXBean.getClassPath()` 按 PATH_SEPARATOR 切分；bootstrap path（KP-07）**JDK9+ 恒空**（`isBootClassPathSupported` JDK11/17 硬编码 return false——死代码案例）
- **现代替代**：`java.class.path` 系统属性
- **my-xhs**：不该用

### 1.6 类代码来源定位：CodeSource [🟡 P2] [有效]
- **来源**：KP-08（ClassPathUtils:90-130 + ClassUtils:299-319）
- **机制**：`ProtectionDomain.getCodeSource().getLocation()` 定位类所在 jar/目录；bootstrap 类无 ProtectionDomain → 走 classpath 兜底
- **坑**：两处重复实现且语义分化（已加载检查 vs 任意类）；SecurityException 吞异常
- **my-xhs**：不该用（`jar tf`/Arthas 覆盖）

### 1.7 资源名归一化：ResourceType 枚举 [🟡 P2] [时间无关模式]
- **来源**：KP-04（ClassLoaderUtils:484-570）
- **机制**：枚举策略（DEFAULT/CLASS/PACKAGE）+ supported/normalize 分离 + resolve 收口——`com.foo.Bar` → `com/foo/Bar.class`
- **坑**：PACKAGE 判定启发式（:515 TODO）；带斜杠输入行为确定（返回 null，非未定义）
- **my-xhs**：不该用（Spring ClassPathResource 覆盖）

### 1.8 多加载器资源联合查找 [🟢 P3] [过时→JDK getResources]
- **来源**：KP-05（:226-302）
- **机制**：`ClassLoader.getResources()` 原生沿继承链返回全部——confucius 的 Set 去重 + 归一化是**冗余封装**
- **my-xhs**：不该用

### 1.9 ClassPath 全量扫描索引 [🟡 P2] [过时→Spring/ClassGraph]
- **来源**：KP-09（ClassUtils:42-92）
- **机制**：静态初始化扫描全部 classpath 建三索引（classpath→类名/类名→classpath/包→类名）
- **坑**：**性能陷阱**——JDK6 合理、现代 classpath 数百 MB 下启动全扫不可接受
- **现代替代**：`PathMatchingResourcePatternResolver`（spring-core 实证）/ClassGraph
- **my-xhs**：不该用

### 1.10 JAR/目录双模式类发现 [🟡 P2] [时间无关模式]
- **来源**：KP-10（:127-135,205-241）
- **机制**：isDirectory → 递归扫描 / isFile+JAR → JarFile 遍历；jar 扫描异常静默吞（:236-238）
- **现代替代**：`Files.walk`/`JarFile.stream()`（JDK9+）
- **my-xhs**：不该用

### 1.11 完整扫描链路：SimpleClassScanner [🟡 P2] [过时→Spring 组件扫描]
- **来源**：KP-22（:96-134）
- **机制**：PACKAGE 资源名 → CL 资源收集（索引兜底）→ classpath 扫描 → 类加载/查已加载——**KP-01~21 的收口**
- **坑**：`requiredLoad=false` 默认只查已加载类——"扫描包"≠"包内类"（首次扫描可能全空）；IOException 空吞
- **现代替代**：`ClassPathScanningCandidateComponentProvider`（spring-context:345 实证）
- **my-xhs**：不该用

**本节 KP 覆盖**：01,02,03,04,05,06,07,08,09,10,22 ✓（11/35）

---

## 二、反射与模块系统（JPMS）[工程问题]

> **核心命题**：JDK9 模块化是反射的分水岭——`setAccessible` 从"通用后门"变成"受限通道"。confucius 全部反射黑科技在 JDK17 下都需要 `--add-opens`。

### 2.1 模块边界与 `InaccessibleObjectException` [🔴 P1] [时间无关模式]
- **来源**：KP-01/02/24（JDK17 AccessibleObject.java:130/:221/:277 实证）
- **机制**：JDK9+ 模块默认封装——跨模块反射 `setAccessible(true)` 抛 `InaccessibleObjectException`；放行方式：`--add-opens java.base/java.lang`（单模块）或 opens 声明
- **知识要点**：java.base 不 opens 给任意代码；**`--add-opens` 与 `--add-exports` 的区别**——add-exports = 模块导出（编译期 + 运行期 readability，Module.java:865/:912 实证），add-opens = 运行时深层反射开放（setAccessible 相关，:903）——**两个选项都同时影响编译与运行，区别在"作用面"：exports 管"可读"，opens 管"可反射"**
- **my-xhs**：不该用（但机制是排查反射类问题的必备知识）

### 2.2 反射读私有字段的版本脆弱性 [🟡 P2] [时间无关模式]
- **来源**：KP-02（classes 字段 Vector→ArrayList 实证）
- **机制**：JDK 内部字段名/类型非 API——改名即破坏（confucius 已踩中一次）
- **决策**：生产禁用反射触碰 JDK 内部；诊断可容忍
- **my-xhs**：不该用

### 2.3 调用者类解析演进 [🟡 P2] [过时→StackWalker]
- **来源**：KP-28（ReflectionUtils:63-92,95-109）
- **机制**：三代方案——①`sun.reflect.Reflection.getCallerClass(int)`（**JDK17 已移除**，死代码实证）②`Thread.currentThread().getStackTrace()`（慢，全栈生成）③`StackWalker`（JDK9+ 官方，RETAIN_CLASS_REFERENCE）
- **坑**：frame 偏移是脆弱魔法数（注释自承 JDK 6/7/8 不同）
- **my-xhs**：不该用（`StackWalker.getCallerClass()` 原生）

### 2.4 Unsafe 获取与数组布局 [🔴 P2] [过时→VarHandle]
- **来源**：KP-24（UnsafeUtils:105-119,121-139）
- **机制**：①获取模式：`AccessController.doPrivileged` + 反射 `Unsafe.theUnsafe`（历史标准做法）②数组布局：`arrayBaseOffset`/`arrayIndexScale` 定位内存起点——CAS 前地址计算的基座
- **坑**：JDK17 在 `jdk.unsupported` 模块；官方明示"unsupported"；需 `--add-opens jdk.unsupported/sun.misc`
- **现代替代**：VarHandle（类型安全/内存语义安全）
- **my-xhs**：不该用

**本节 KP 覆盖**：01,02,24,28 ✓（4/35，与 §一/§三 交叉归并）

---

## 三、JDK 内部 API 触达（"unsupported" 层）[性能优化]

> **核心命题**：JDK 官方 API 缺失期的"手工补位"——进程号/进程执行/Attach/注册表，全部被 JDK9+ 原生吸收。

### 3.1 进程号获取演进 [🟡 P2] [过时→ProcessHandle]
- **来源**：KP-23（ManagementUtils:45-58,60-71,90-102）
- **机制**：①JDK6 期：反射读 `RuntimeImpl.jvm` 私有字段 + `getProcessId()` 私有方法（两级深挖，极端脆弱）②降级：解析 `runtimeMXBean.getName()`（`pid@host` 格式）③JDK9+：`ProcessHandle.current().pid()`（ProcessHandle.java:135 实证）
- **my-xhs**：不该用

### 3.2 进程执行与超时 [🟡 P3] [过时→ProcessBuilder.waitFor(timeout)]
- **来源**：KP-26（ProcessExecutor:75-113）
- **机制**：轮询 `exitValue()`（抛 IllegalThreadStateException 即未结束）+ sleep + `destroy()` 超时回收；`available()>0` 非阻塞读流
- **坑**：`costTime = endTime - startTime`（:84）endTime 初始 -1——超时判定粒度=轮询间隔；忙等 CPU 空转；`Thread.interrupted()` 吞中断不恢复（:124-125）
- **现代替代**：`process.waitFor(timeout, TimeUnit)` + `destroyForcibly()`
- **my-xhs**：不该用

### 3.3 进程注册表模式 [🟢 P3] [时间无关模式]
- **来源**：KP-27（ProcessManager:23-49）
- **机制**：单例 + ConcurrentMap 登记/注销（`remove(key,value)` 带值删除防误删）；不可变视图
- **my-xhs**：不该用

### 3.4 JVM Attach 机制 [🟡 P2] [过时→jdk.attach 模块 + agent 技术]
- **来源**：KP-33（VirtualMachineTemplate:43-54）
- **机制**：`VirtualMachine.attach(pid)` → 操作 → `detach()`（finally 防泄漏）；`LocalVirtualMachineTemplate` 附自己（用 3.1 的 PID）
- **知识价值**：**attach 是理解 Java Agent/Arthas 的底层前置**——但注意 agent 两种加载路径：`premain`（启动时 `-javaagent` 参数，不走 attach）与 `agentmain`（运行时 `VirtualMachine.loadAgent` 附加——VirtualMachine.java:54-61 javadoc 实证）；Arthas 走 agentmain 路径
- **坑**：依赖 `jdk.attach` 模块（JDK9+ 模块化，attach API 本身 JDK6 已有——六表 #17 已注）；`sun.tools.attach.HotSpotVirtualMachine` 是内部类
- **my-xhs**：不该用（Arthas 覆盖）；**知识本体保留**

### 3.5 Windows 注册表访问 [🟢 P3] [过时→Preferences]
- **来源**：KP-34（WindowsRegistry:30-61）
- **机制**：原生句柄 + 错误码封装（Windows-only）
- **现代替代**：`java.util.prefs.Preferences`（Windows 后端即注册表）
- **my-xhs**：不该用

**本节 KP 覆盖**：23,26,27,33,34 ✓（5/35）

---

## 四、规范与标准（时间无关）[规范]

> **核心命题**：规范是知识本体——工具会过时，规范与机制永不过时。

### 4.1 SPI 机制（服务发现基座）[🔴 P1] [时间无关模式]
- **来源**：KP-32（ServiceLoaderUtils:38-70）
- **机制**：`/META-INF/services/{接口全名}` 文件 + `ServiceLoader.load(接口, classLoader)`；**配置顺序即加载顺序**（loadFirst/Last 语义无排序）
- **坑**：空实现抛 IllegalArgumentException（fail-fast 设计决策）；loadFirst/Last 依赖文件顺序非确定性
- **现代替代**：JDK9+ `ServiceLoader.stream()`（lazy + Provider 封装）；Spring `SpringFactoriesLoader`
- **生态位置**：**microsphere 生态的服务发现基座**——后续仓库大量复用（SPI 是核心知识，面试必考）
- **my-xhs**：不该用（Spring 覆盖）；**机制必须掌握**

### 4.2 ResourceBundle 扩展点演进 [🟡 P2] [过时→ResourceBundleControlProvider]
- **来源**：KP-30（PropertyResourceBundleControl:34-155）
- **机制**：`ResourceBundle.Control` 是 JDK 标准扩展点（编码感知加载：`InputStreamReader(stream, encoding)` + `PropertyResourceBundle(reader)` :134-135）；JDK17 未标弃用（:2518 实证）但官方扩展路径已迁移 `ResourceBundleControlProvider` SPI（:1517-1528）
- **好细节**：reload 时 `setUseCaches(false)`（:118）——开发期热更新
- **my-xhs**：不该用（Spring MessageSource 覆盖）

### 4.3 JSR-305 空注解 [🟢 P3] [过时→JSpecify/Spring Nullable]
- **来源**：KP-12
- **机制**：`javax.annotation.Nonnull/@Nullable` 从未进 JDK（第三方，停维护）；**注意非 javax→jakarta 迁移**（独立于 EE）
- **现代替代**：JSpecify（JEP 方向）/ Spring `org.springframework.lang.Nullable`（spring-core:17 实证）
- **my-xhs**：不该用（Spring 生态直接 Spring Nullable）

### 4.4 Base64 规范（RFC 2045/4648）[🟢 P3] [过时→java.util.Base64]
- **来源**：KP-35（Base64:38-45）
- **机制**：3 字节→4 字符位运算；alternate 变体（无大写字符集）；**JDK 移植实证**——作者 Josh Bloch（:6）+ `@see Preferences`（:8）
- **现代替代**：`java.util.Base64`（JDK8+）
- **my-xhs**：不该用

**本节 KP 覆盖**：12,30,31,32,35 ✓（5/35，KP-31 门面并入 4.2）

---

## 五、设计模式与工程实践（时间无关）[工程问题]

> **核心命题**：confucius 的 JDK6 时代设计手法——模式时间无关，具体实现被 JDK8+ 吸收。

### 5.1 谓词抽象演进 [🟡 P2] [过时→Predicate]
- **来源**：KP-13（Filter:16,25-26）/ KP-16（TrueClassFilter）/ KP-18（JarEntryFilter）
- **机制**：自定义单方法接口（JDK6 无 lambda）→ 标记接口变体（ClassFilter）→ 恒真单例（INSTANCE）——全部被 JDK8+ `Predicate`/lambda 吸收
- **my-xhs**：不该用

### 5.2 组合谓词与短路语义 [🔴 P2] [过时→Predicate.and/or]
- **来源**：KP-14（FilterOperator:21-64）
- **机制**：枚举策略（AND/OR/XOR）+ 闭包工厂（createFilter :91-100）
- **坑（教科书案例）**：①XOR 初始 `success=true`（:60）→ **实为 XNOR 语义**（偶数 true；单 filter 等价取反）——正确应初始 false；②`&=`/`|=` 非短路（JDK `&&` 才是）；③空数组 XOR 返回 true（恒等元应为 false）
- **测试教训**：XOR bug 未被测试捕获（测试只覆盖单 filter AND/OR）
- **my-xhs**：不该用（`Predicate.and/or` 原生短路）

### 5.3 安全过滤模式 [🟡 P3] [过时→Stream.filter]
- **来源**：KP-15（FilterUtils:58-68）
- **机制**：**拷贝 + iterator.remove + 不可变返回三保险**——副作用隔离好设计
- **my-xhs**：不该用

### 5.4 包名过滤与边界 [🟡 P2] [时间无关模式]
- **来源**：KP-17（PackageNameClassFilter:35-43）
- **机制**：Class/ClassName 双变体（getPackage vs resolvePackageName）；子包前缀 `packageName + "."`（防御误匹配）
- **坑**：默认包 `getPackage()` 返回 null → NPE；局部变量与字段同名遮蔽（可读性）
- **my-xhs**：不该用（Spring TypeFilter 覆盖）

### 5.5 文件系统/JAR 扫描 [🟡 P2] [过时→Files.walk/JarFile.stream]
- **来源**：KP-20（SimpleFileScanner:44-83）/ KP-21（SimpleJarEntryScanner:76-133）
- **机制**：目录递归（`subFiles != null` 空保护）+ jar 深度判定（`indexOf(SLASH, relativePath.length()) < 0` 仅该层）
- **坑**：jar recursive=true 用 `startsWith` 前缀匹配（`META-INF/` 匹配 `META-INF2/`）；顶层扫描目录条目被排除
- **my-xhs**：不该用

### 5.6 相对路径解析 [🟢 P3] [过时→Path.relativize]
- **来源**：KP-19（FileUtils:33-40）
- **机制**：字符串子串替换（JDK6 无 Path API）
- **坑**：`contains` 子串匹配非路径边界（`/data/workspace` 误匹配 `/data/workspace2`）；替换为 FILE_SEPARATOR 语义脆弱
- **my-xhs**：不该用

### 5.7 URL 解析家族 [🟡 P2] [时间无关模式]
- **来源**：KP-25（URLUtils:56-124,138-150）/ KP-29（JarUtils:47-146）
- **机制**：jar URL 结构（`jar:file:/path!/entry`）——协议白名单校验（:70 好实践）+ 相对路径截取 + query 手工解析 + 路径归一化
- **坑**：query 解析未处理 URL 编码（%20）；value 含 `=` 截断；循环 replace O(n²)
- **my-xhs**：不该用（`URI`/`Path`/`UriComponentsBuilder` 覆盖）

### 5.8 模板方法模式（attach 生命周期）[🟡 P2] [时间无关模式]
- **来源**：KP-33（VirtualMachineTemplate:43-54）
- **机制**：attach → callback → finally detach——**资源生命周期模板**（防泄漏）
- **my-xhs**：不该用（模式知识保留）

**本节 KP 覆盖**：13,14,15,16,17,18,19,20,21,25,29,33 ✓（12/35，KP-33 与 §三 双归属）

---

## 六、JDK 演进替代图谱（工具→原生总表）[性能优化/工程问题]

> **核心命题**：confucius-commons 36 文件的"死因"总表——每条都是"JDK 演进吸收了框架模式"的实证。

| # | confucius 实现 | 触发知识点 | JDK 原生替代 | 版本 |
|---|---------------|-----------|-------------|------|
| 1 | 反射 findLoadedClass/classes 字段 | 类加载内部 | `--add-opens` + jcmd/Arthas | 9+ |
| 2 | 自定义 Filter 接口 | 谓词抽象 | `java.util.function.Predicate` | 8+ |
| 3 | FilterOperator 组合枚举 | 组合谓词 | `Predicate.and/or/negate` | 8+ |
| 4 | FilterUtils 迭代过滤 | 集合过滤 | `Stream.filter` | 8+ |
| 5 | FileUtils 字符串替换 | 相对路径 | `Path.relativize` | 7+ |
| 6 | SimpleFileScanner 递归 | 文件扫描 | `Files.walk` | 8+ |
| 7 | SimpleJarEntryScanner/JarUtils | JAR 遍历 | `JarFile.stream()` | 9+ |
| 8 | SimpleClassScanner 全量索引 | 类扫描 | `ClassPathScanningCandidateComponentProvider` | Spring |
| 9 | 反射 RuntimeImpl.jvm 取 PID | 进程号 | `ProcessHandle.current().pid()` | 9+ |
| 10 | ProcessExecutor 轮询超时 | 进程执行 | `ProcessBuilder.waitFor(timeout)` | 8+ |
| 11 | Unsafe 反射获取 | 内存后门 | `VarHandle` | 9+ |
| 12 | sun.reflect.Reflection 调用者 | 调用者类 | `StackWalker.getCallerClass()` | 9+ |
| 13 | PropertyResourceBundleControl | 资源包编码 | `ResourceBundleControlProvider` | 9+ |
| 14 | WindowsRegistry 原生句柄 | 注册表 | `java.util.prefs.Preferences` | 1.4+ |
| 15 | Base64 手工位运算 | 编码 | `java.util.Base64` | 8+ |
| 16 | ServiceLoaderUtils 空抛异常 | SPI 加载 | `ServiceLoader.stream()` | 9+ |
| 17 | VirtualMachine 手工 attach | JVM 附加 | Java Agent（agentmain） / Arthas（attach API 本身 JDK6 已有，JDK9 模块化进 jdk.attach） | 6+（模块化 9+） |
| 18 | 常量接口 | 常量组织 | 枚举/final class + 私有构造 | 反模式 |

**本节覆盖**：全部 35 KP 的替代物汇总 ✓

---

## 七、反模式与坑清单（教训集）[工程问题]

> **核心命题**：confucius 是 JDK6 时代的"反面教材 + 正面教材"合集——坑比实现更有教学价值。

### 7.1 代码语义陷阱（"看起来在做≠真的实现"）
| 坑 | 位置 | 正确语义 | 来源 KP |
|----|------|---------|---------|
| XOR 初始 true → XNOR | FilterOperator:60 | 初始 false 才是 XOR（空数组 return true :59 恒等元应为 false） | 14 |
| `&=`/`|=` 非短路 | FilterOperator:29/:46 | `&&`/`||` 短路 | 14 |
| `contains` 当路径边界 | FileUtils:36 | `startsWith`/Path 语义 | 19 |
| `startsWith` 前缀误匹配 | SimpleJarEntryScanner:117 | 包边界判定 | 21 |

### 7.2 死代码与失效 API
| 坑 | 位置 | 事实 | 来源 KP |
|----|------|------|---------|
| bootstrap classpath 恒空 | ClassPathUtils:39 | JDK11/17 `isBootClassPathSupported` return false | 07 |
| `sun.reflect.Reflection` | ReflectionUtils:69 | JDK17 已移除（sun/reflect/ 目录实证——仅 annotation/generics/misc） | 28 |
| 反射 JDK 内部需 opens | ClassLoaderUtils:53/:421、UnsafeUtils:110 | `InaccessibleObjectException`（AccessibleObject.java:130 实证） | 01/02/24 |

### 7.3 工程反模式
- **常量接口**（KP-11）——JDK9+ 接口私有方法时代反模式
- **吞异常静默返回**（KP-10 :236 / KP-22 :131 / KP-08 :122）——排查困难根源
- **全量启动扫描**（KP-09）——现代性能陷阱
- **JSR-305 死依赖**（KP-12）——停维护第三方注解
- **frame 魔法数**（KP-28 :72）——注释自承"JDK 6/7/8 不同"

### 7.4 正面教材（值得学）
- **FilterUtils 三保险**（拷贝+remove+不可变，KP-15）
- **协议白名单校验**（JarUtils:70，KP-29）
- **attach finally detach**（KP-33）
- **reload setUseCaches(false)**（KP-30 :118）
- **ProcessManager remove(key,value)**（KP-27 :32）

**本节覆盖**：07,10,11,12,14,17,19,21,22,24,26,28,29,30,33,34 ✓

---

## 覆盖核对（35/35，无遗漏）

| 维度 | 覆盖 KP | 数 |
|------|---------|:--:|
| 一、类加载机制 | 01,02,03,04,05,06,07,08,09,10,22 | 11 |
| 二、反射与模块系统 | 01,02,24,28（01/02/24 与一/三交叉） | 4 |
| 三、JDK 内部 API | 23,26,27,33,34 | 5 |
| 四、规范与标准 | 12,30,31,32,35 | 5 |
| 五、设计模式 | 13,14,15,16,17,18,19,20,21,25,29,33（33 与三交叉） | 12 |
| 六、替代图谱 | 全部 35（汇总表） | 35 |
| 七、坑清单 | 07,10,11,12,14,17,19,21,22,24,26,28,29,30,33,34 | 16 |

**去重后唯一 KP 全集**：01,02,03,04,05,06,07,08,09,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35 = **35/35 ✓**

**遗漏检查**：
- KP-11（常量接口）→ 七 §7.3 ✓
- KP-27（ProcessManager）→ 三 §3.3 ✓
- KP-31（门面）→ 四 §4.2（并入）✓
- KP-20（SimpleFileScanner）→ 五 §5.5 ✓
- 全部 KP 至少归属一个维度，无孤儿 KP ✓
