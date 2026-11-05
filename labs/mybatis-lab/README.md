# MyBatis Lab

对应 `MyBatis/book/02`-`06`：XML Mapper、动态 SQL、resultMap 嵌套映射、插件。

## 展示什么

```text
GET  /lab/mybatis/users/1               selectById（XML + #{}）
GET  /lab/mybatis/users?name=Ali&age=20 search（<where>/<if> 动态 SQL）
GET  /lab/mybatis/users/1/orders        selectWithOrders（collection 嵌套 resultMap）
POST /lab/mybatis/users                 insert（useGeneratedKeys 回填 id）
PUT  /lab/mybatis/users/1/name?name=Tom updateName（@Param）
DELETE /lab/mybatis/users/1             deleteById
```

- `UserMapper.java`：只有接口，没有实现类。
- `UserMapper.xml`：`namespace` 绑定接口，`id` 绑定方法，`#{}` 使用 PreparedStatement。
- `UserWithOrdersMap`：一对多嵌套映射，`orders` / `items` 一次查询组装。
- `SqlCostInterceptor`：拦截 Executor 的 `query/update`，记录 SQL 执行耗时（不是拦截 `prepare`）。

## 依赖

```text
JDK 17
Spring Boot 3.2.5
mybatis-spring-boot-starter 3.0.3
MyBatis 3.5.16
MySQL 8.0.33
```

不要求默认启动。需要 MySQL 时：

```bash
docker run --name mybatis-lab-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=mybatis_lab \
  -p 3306:3306 -d mysql:8.0.33
```

然后在 `labs/` 根目录执行：

```bash
mvn -pl mybatis-lab -am package
java -jar mybatis-lab/target/mybatis-lab-1.0.0-SNAPSHOT.jar
```

`spring.sql.init.mode=always` 会自动执行 `schema.sql` / `data.sql`；脚本使用 `CREATE TABLE IF NOT EXISTS` 和 `INSERT IGNORE`，重复启动不会炸。

## 关键配置

```yaml
mybatis:
  mapper-locations: classpath*:mapper/*.xml
  type-aliases-package: io.microsphere.labs.mybatis.domain
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
```

## 边界

- 这是教学 lab，不是生产工程：无鉴权、无分页、无重试/幂等。
- MySQL 只提供 schema 和连接配置；不要求实际启动。
- URL 已带 `createDatabaseIfNotExist=true`；若账号没有建库权限，先手动创建 `mybatis_lab`。
