# MyBatis-Plus Lab

对应 MyBatis-Plus 书：`BaseMapper`、`LambdaQueryWrapper`、分页插件、逻辑删除。

## 展示什么

```text
GET    /lab/mybatis-plus/users/1                  BaseMapper.selectById
GET    /lab/mybatis-plus/users/1/raw              自定义 @Select，绕过逻辑删除
GET    /lab/mybatis-plus/users?name=Ali&age=20    LambdaQueryWrapper
GET    /lab/mybatis-plus/users/page?current=1&size=2
                                                  PaginationInnerInterceptor
POST   /lab/mybatis-plus/users                    BaseMapper.insert
PUT    /lab/mybatis-plus/users/1/name?name=Tom    updateById（只更新非 null 字段）
DELETE /lab/mybatis-plus/users/1                  deleteById（逻辑删除）
```

## 关键点

```text
BaseMapper                CRUD 不用写 XML
LambdaQueryWrapper        类型安全的查询条件
PaginationInnerInterceptor 物理分页，拦截 SQL 加 LIMIT
@TableLogic               deleteById 变 UPDATE deleted=1
selectRawById             自定义 SQL 绕过逻辑删除，用于对照
```

## 依赖

```text
JDK 17
Spring Boot 3.2.5
mybatis-plus-spring-boot3-starter 3.5.7
MyBatis 3.5.16
MySQL 8.0.33
```

不要求默认启动。需要 MySQL 时：

```bash
docker run --name mybatis-plus-lab-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=mybatis_plus_lab \
  -p 3306:3306 -d mysql:8.0.33
```

在 `labs/` 根目录执行：

```bash
mvn -pl mybatis-plus-lab -am package
java -jar mybatis-plus-lab/target/mybatis-plus-lab-1.0.0-SNAPSHOT.jar
```

## 逻辑删除验证

```text
DELETE /lab/mybatis-plus/users/1
GET    /lab/mybatis-plus/users/1       → 200 空 body（逻辑删除过滤）
GET    /lab/mybatis-plus/users/1/raw   → 仍能查到 deleted=1
```

`schema.sql` / `data.sql` 可重复执行；`spring.sql.init.mode=always` 会在启动时初始化。

## 边界

- 教学 lab，无鉴权、无幂等、无生产级分页深度限制。
- MySQL 只提供 schema 和连接配置；不要求实际启动。
- 分页只配了 `PaginationInnerInterceptor(DbType.MYSQL)`；没有加多租户、乐观锁等额外插件。
