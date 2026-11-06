# Spring Lab

Spring 系列 Book 的配套用法工程。

## 模块

```text
framework-lab   Spring Framework：容器、生命周期、DI、AOP、事务
mvc-lab         Spring MVC：DispatcherServlet + 嵌入式 Tomcat（可启动）
boot-lab        Spring Boot：启动、自动配置、Web（可启动）
tomcat-lab      纯 Tomcat：Connector、Mapper、Valve、Servlet（可启动）
test-lab        Spring Test：TestContext / @SpringJUnitConfig
```

## 约束

```text
不依赖 Redis/MySQL/Nacos 等外部中间件；
需要数据库时只用 H2 内存库；
一次只启动一个模块；
默认限制 JVM 内存；
跑完即停，不留后台服务。
```

## framework-lab 运行

JDK 17：

```bash
JAVA_HOME=/env/javaEnv/jdk17 \
PATH=/env/javaEnv/jdk17/bin:/env/javaEnv/maven-3.9/bin:$PATH \
MAVEN_OPTS=-Xmx256m \
mvn -q -f spring-lab/framework-lab/pom.xml test
```

这是 JUnit 测试，执行完 JVM 自动退出，不会常驻。

## 后续 Web 模块的启动/停止

如果模块是 Spring Boot Web 应用：

```bash
JAVA_HOME=/env/javaEnv/jdk17 \
PATH=/env/javaEnv/jdk17/bin:/env/javaEnv/maven-3.9/bin:$PATH \
MAVEN_OPTS=-Xmx256m \
mvn -f spring-lab/boot-lab/pom.xml spring-boot:run
```

停止：

```text
Ctrl-C
```

跑完确认没有残留 java 进程；需要时再启动，不常驻。


## mvc-lab 启动/停止

构建：

```bash
JAVA_HOME=/env/javaEnv/jdk17 \
PATH=/env/javaEnv/jdk17/bin:/env/javaEnv/maven-3.9/bin:$PATH \
mvn -q -f mvc-lab/pom.xml -DskipTests package

JAVA_HOME=/env/javaEnv/jdk17 \
PATH=/env/javaEnv/jdk17/bin:/env/javaEnv/maven-3.9/bin:$PATH \
mvn -q -f mvc-lab/pom.xml dependency:build-classpath \
  -Dmdep.outputFile=/tmp/mvc-lab-cp.txt
```

启动（随机端口，`-Xmx256m`）：

```bash
CP="mvc-lab/target/classes:$(cat /tmp/mvc-lab-cp.txt)"
JAVA_HOME=/env/javaEnv/jdk17 PATH=/env/javaEnv/jdk17/bin:$PATH \
java -Xmx256m -cp "$CP" \
  io.microsphere.labs.spring.mvc.MvcLabApplication
```

日志会打印：

```text
PORT=xxxxx
```

验证：

```bash
curl --noproxy '*' "http://127.0.0.1:xxxxx/hello?name=spring"
# hello spring
```

停止：

```text
Ctrl-C，或 kill 启动的 java 进程。
```

注意：当前环境有 HTTP 代理时，本地 curl 要加 `--noproxy '*'`。


## boot-lab 启动/停止

已包含 Actuator health 与 graceful shutdown 配置。


```bash
JAVA_HOME=/env/javaEnv/jdk17 \
PATH=/env/javaEnv/jdk17/bin:/env/javaEnv/maven-3.9/bin:$PATH \
mvn -q -f boot-lab/pom.xml -DskipTests package

JAVA_HOME=/env/javaEnv/jdk17 PATH=/env/javaEnv/jdk17/bin:$PATH \
java -Xmx256m -jar boot-lab/target/boot-lab-1.0.0-SNAPSHOT.jar \
  --server.port=0
```

日志出现：

```text
Tomcat started on port xxxxx
```

验证：

```bash
curl --noproxy '*' "http://127.0.0.1:xxxxx/hello?name=boot"
# hello boot
```

停止：`Ctrl-C` 或 kill 进程；不要留后台服务。


## tomcat-lab 启动/停止

```bash
JAVA_HOME=/env/javaEnv/jdk17 \
PATH=/env/javaEnv/jdk17/bin:/env/javaEnv/maven-3.9/bin:$PATH \
mvn -q -f tomcat-lab/pom.xml -DskipTests package

JAVA_HOME=/env/javaEnv/jdk17 PATH=/env/javaEnv/jdk17/bin:$PATH \
mvn -q -f tomcat-lab/pom.xml dependency:build-classpath \
  -Dmdep.outputFile=/tmp/tomcat-lab-cp.txt

CP="tomcat-lab/target/classes:$(cat /tmp/tomcat-lab-cp.txt)"
JAVA_HOME=/env/javaEnv/jdk17 PATH=/env/javaEnv/jdk17/bin:$PATH \
java -Xmx256m -cp "$CP" \
  io.microsphere.labs.spring.tomcat.TomcatLabApplication
```

日志打印 `PORT=xxxxx`，验证：

```bash
curl --noproxy '*' -i "http://127.0.0.1:xxxxx/hello"
# X-Tomcat-Valve: trace
# hello tomcat
```

停止：Ctrl-C 或 kill 进程。
