# FlagServer

基于 Java 17、Spring Boot、MyBatis、MySQL 和 Redis 的功能开关与灰度发布学习项目。业务方按 `flagKey` 和 `userId` 查询功能是否开放，也可以通过仓库里的 Java SDK 接入。

## 求值与缓存

- 先查开关配置，总开关关闭直接返回 `false`。开启后查 MySQL 白名单，命中则返回 `true`，否则按 `flagKey` 和 `userId` 稳定分桶，与灰度比例比较。
- Redis 缓存开关配置，TTL 为 10 秒。修改后删除缓存，删除开关时在数据库事务提交后清缓存。Redis 读取异常时回查 MySQL，并跳过本次写回。
- 开关和所属白名单在同一事务中删除。缓存删除失败只记录日志，并发读写时仍可能出现旧值回填。管理接口当前未配置鉴权。

## 本地运行

1. 准备 JDK 17、MySQL 和 Redis。用下面的 SQL 初始化空库。
2. 新建 `src/main/resources/application-local.properties`，填写下面的连接配置。该文件已在 `.gitignore` 中，主配置默认启用 `local`。
3. 在仓库根目录运行 `./mvnw spring-boot:run`（Windows 用 `.\mvnw.cmd spring-boot:run`），或在 IDEA 中运行 `FlagServerApplication`。
4. 访问 `http://localhost:8080/api/flags`。空库应返回 `[]`。

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/feature_flag
spring.datasource.username=YOUR_MYSQL_USER
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

<details>
<summary>MySQL 初始化 SQL</summary>

```sql
CREATE DATABASE feature_flag DEFAULT CHARACTER SET utf8mb4;
USE feature_flag;

CREATE TABLE flag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flag_key VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    rollout_percent INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flag_key (flag_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE flag_whitelist (
    flag_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (flag_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

</details>

## 接口与 SDK

| 操作 | 方法和路径 |
| --- | --- |
| 列表与新增 | `GET /api/flags`、`POST /api/flags` |
| 查询、修改与删除 | `GET`、`PUT`、`DELETE /api/flags/{id}` |
| 查询总开关 | `GET /api/flags/evaluate/{flagKey}` |
| 为用户求值 | `GET /api/flags/evaluate/{flagKey}/{userId}` |
| 查询白名单 | `GET /api/flags/{id}/whitelist` |
| 添加与删除白名单成员 | `POST`、`DELETE /api/flags/{id}/whitelist/{userId}` |

先向 `POST /api/flags` 发送 JSON 创建示例开关：

```json
{"flagKey":"new-homepage","name":"新版首页","enabled":true,"rolloutPercent":0}
```

再调用 `GET /api/flags/evaluate/new-homepage/1001`，预期返回 `false`。查询开关列表拿到数字 `id`，把用户 `1001` 加入白名单后，重新求值应返回 `true`。`PUT` 需提交 `name`、`enabled`、`rolloutPercent`，可附带 `description`。

在根目录执行 `mvn -f flag-SDK/pom.xml install` 将 SDK 安装到本地 Maven 仓库，再在 IDEA 中导入 `flag-sdk-demo/pom.xml`，运行 `Main.Main`。示例默认访问 `http://localhost:8080` 中的 `new-homepage`，服务地址可通过 `FlagClient` 构造参数设置。

## 测试与实测

- **单元测试**：`mvn -Dtest=FlagServiceTests test`，7 个用例已通过，覆盖灰度边界、白名单、缓存命中和 Redis 读取异常回退。
- **数据库集成测试**：`FlagDeletionIntegrationTests` 使用独立的 `feature_flag_test` 库验证关联删除及异常回滚。按相同表结构初始化测试库，再单独配置测试连接。

### 本机 HTTP 压测

Java `HttpClient` 请求 `evaluate`，每档预热后正式测约 30 秒，同时校验 HTTP 状态码和求值结果。所有测试开关均开启，灰度为 0%，每个开关有 10 条白名单关系。每个单元格为**正确响应数/秒 / 客户端端到端 P99**，九档均未观察到响应错误。

| 请求分布 | 10 并发 | 30 并发 | 50 并发 |
| --- | ---: | ---: | ---: |
| 随机查 10,000 个开关，20 个用户 ID | 8,710 / 3.24 ms | 21,124 / 2.96 ms | 22,670 / 9.40 ms |
| 随机查 50 个开关，5,000 个用户 ID | 9,616 / 2.44 ms | 22,144 / 5.49 ms | 22,762 / 10.31 ms |
| 固定查一个开关，5,000 个用户 ID | 8,523 / 3.86 ms | 21,033 / 2.74 ms | 22,176 / 4.02 ms |

第一组有 100,000 条白名单关系，命中率约 50%。后两组共用 500 条白名单关系，命中率均约 0.2%。三组在 30–50 并发下均达到约 2.1–2.3 万次正确响应/秒。

### 高并发补测

沿用随机访问 50 个开关、5,000 个用户 ID 的负载，以 **端到端 P99 ≤ 10 ms** 为本轮延迟目标。

| 并发线程 | 正确响应数/秒 | P99 | 错误数 |
| ---: | ---: | ---: | ---: |
| 75 | 23,963 | 6.55 ms | 0 |
| **100** | **26,314** | **7.04 ms** | **0** |
| 150 | 26,029 | 10.48 ms | 0 |
| 200 | 22,570 | 18.89 ms | 0 |

100 并发的吞吐在本轮最高，且满足延迟目标。150 并发是本轮首个延迟超标的测试档位，200 并发的吞吐较 100 并发低约 14%。两次测试的 P99 有波动，以上结果对应本机负载与采样窗口。

### 双实例变更实验

本机低并发下完成 10 次 `0% ↔ 100%` 切换。两个实例共用 MySQL 和 Redis，从发出 `PUT` 到两边都首次返回新结果，中位数为 11.99 ms，最慢 58.92 ms。更新成功响应后发起的 7,773 次求值未观察到旧值。耗时包含轮询等待，结果对应本次实验条件。
