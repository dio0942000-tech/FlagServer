# FlagServer：功能开关与灰度发布学习项目

使用 Java 和 Spring Boot 实现的后端学习项目。通过 HTTP 接口管理功能开关，并根据总开关、用户白名单和灰度百分比，判断某个功能是否对指定用户开放。

## 已实现的功能

- 开关配置的新增、列表查询、按编号查询、修改和删除。
- 根据功能标识查询总开关状态。
- 根据功能标识和用户编号进行稳定的灰度判断。
- 按开关管理白名单：添加、查询、删除，重复添加返回冲突。
- 参数校验及 400、404、409 错误响应。
- 删除开关时，在同一事务中先清理所属白名单，再删除开关。
- 使用 Redis 缓存开关配置，支持 JSON 序列化、10 秒 TTL 和修改/删除后的缓存清理。
- Redis 读取失败时回退查询 MySQL，并跳过本次缓存写回；缓存写入或清理失败时记录日志。
- 提供基于 Java HttpClient 的 SDK 源码和独立调用示例，包含连接及请求超时设置。

## 技术与运行环境

- JDK 17，Maven（可以使用 IDEA 的 Maven 集成）。
- Spring Boot 4.1.2、Spring MVC、Spring 事务管理。
- MySQL、MySQL Connector/J。
- MyBatis-Plus Spring Boot 4 Starter 3.5.17；当前使用手写 MyBatis 注解 SQL。
- Lombok，用于生成实体和请求对象的访问方法。
- Redis、Spring Data Redis、Lettuce；通过 StringRedisTemplate 存取字符串。
- Jackson 3 JsonMapper，用于 Flag 对象与 JSON 的相互转换。
- JUnit 5、Mockito，用于单元测试及集成测试。

## 本地启动

1. 使用 IDEA 打开项目根目录，将项目 JDK 设为 17，加载 Maven 依赖。
2. 启动 MySQL，并准备 `feature_flag` 数据库和下面的两张表。
3. 在 `src/main/resources/application-local.properties` 中填写自己的数据库连接信息。
4. 主配置已设置 `spring.profiles.active=local`，会加载本地配置。
5. 启动 Redis，配置下面的 Redis 连接信息，再运行 `com.dio.flagserver.FlagServerApplication`。
6. 访问 `http://localhost:8080/api/flags`，应返回 JSON 列表；新数据库返回 `[]`。

本地配置示例（请替换用户名和密码，端口按实际情况调整）：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/feature_flag
spring.datasource.username=YOUR_MYSQL_USER
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.connect-timeout=3s
spring.data.redis.timeout=3s
```

`application-local.properties` 已加入 `.gitignore`，不要把真实密码复制到 README 或提交到仓库。遇到 Lombok 相关编译问题时，检查 IDEA 是否启用了注解处理。

### 初始化数据库

以下根据本项目此前的建表设计整理，供新的学习环境初始化使用，不是数据库迁移脚本。已有表不要直接重复创建，也不要为了执行这些语句删除现有数据；实际表结构可以通过 `SHOW CREATE TABLE` 检查。

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

白名单中的 `flag_id` 对应 `flag.id`。联合主键保证同一开关下的同一用户不能重复添加。上述建表语句没有设置外键；当前由 Service 负责存在性检查和删除时的白名单清理。

## 功能判断规则

用户判断接口按以下顺序执行：

1. 根据 `flagKey` 查询开关，不存在则返回 404。
2. 总开关关闭则返回 `false`，包括白名单用户。
3. 用户属于该开关的白名单则返回 `true`。
4. 否则计算分组编号，与灰度百分比比较。

```java
String input = flagKey + ":" + userId;
int bucket = Math.floorMod(input.hashCode(), 100);
return bucket < flag.getRolloutPercent();
```

编号范围是 0～99。总开关开启时，非白名单用户在 0% 下不通过、100% 下全部通过。输入、算法和分组数量不变时，分组编号稳定；最终结果还取决于开关配置和白名单。提高比例不会排除原本通过灰度判断的用户。

百分比表示开放的分组比例，不保证小样本中用户人数比例完全一致。当前使用 Java 字符串哈希作为学习实现，整体分布均匀性尚未进行系统评估。

## HTTP 接口

默认基地址：`http://localhost:8080`。

`{id}` 是开关的数据库编号，`{flagKey}` 是功能标识（如 `new-search`），`{userId}` 是数字用户编号。实际请求时替换占位符，不保留花括号。

| 方法 | 路径 | 用途与成功响应 |
|---|---|---|
| GET | `/api/flags` | 查询全部开关，200 + JSON 数组 |
| GET | `/api/flags/{id}` | 查询单个开关，200 + JSON 对象 |
| POST | `/api/flags` | 新增开关，200 + 插入行数 `1`，不是新记录 id |
| PUT | `/api/flags/{id}` | 修改配置，200 + 修改后的开关对象 |
| DELETE | `/api/flags/{id}` | 删除开关及所属白名单，204，无正文 |
| GET | `/api/flags/evaluate/{flagKey}` | 仅查询总开关状态，200 + 布尔值 |
| GET | `/api/flags/evaluate/{flagKey}/{userId}` | 综合白名单和灰度规则判断，200 + 布尔值 |
| GET | `/api/flags/{id}/whitelist` | 查询白名单用户编号，200 + 数组；无成员为 `[]` |
| POST | `/api/flags/{id}/whitelist/{userId}` | 添加白名单成员，204，无正文 |
| DELETE | `/api/flags/{id}/whitelist/{userId}` | 删除白名单成员，204，无正文 |

白名单接口的参数都在路径中，不需要 JSON 请求体。

### 新增与修改请求

新增开关：`POST /api/flags`，请求头 `Content-Type: application/json`。

```json
{
  "flagKey": "readme-demo",
  "name": "README测试开关",
  "description": "用于手动验证",
  "enabled": true,
  "rolloutPercent": 0
}
```

修改开关：`PUT /api/flags/{id}`，请求头同上。

```json
{
  "name": "README测试开关",
  "description": null,
  "enabled": true,
  "rolloutPercent": 30
}
```

修改要求提交全部可编辑配置，不修改 `flagKey`、id 和创建时间。`name` 非空且不能只包含空白，长度不超过 128；`description` 可为 null，长度不超过 512；`enabled` 必填；`rolloutPercent` 必填且在 0～100。新增时 `flagKey` 必填、不超过 64 位，只允许英文字母、数字和短横线。

### 状态码

- 200：成功，并有返回内容。
- 204：成功，没有响应正文。
- 400：参数不符合要求。
- 404：开关不存在，或删除的白名单成员不存在。
- 405：路径不支持所使用的 HTTP 方法。
- 409：开关标识重复，或同一开关的白名单用户重复。
- 500：未妥善处理的服务端异常；不能仅凭状态码判断事务是否回滚。

业务异常响应示例：`{"message":"白名单已经存在该用户"}`。未匹配路由等框架错误的响应格式可能不同。

## 手动验收流程

可使用 Postman；新增、修改时选择 Body → raw → JSON。

1. 创建总开关开启、灰度为 0% 的测试开关。
2. 通过列表查询找到它的实际 id。
3. 使用用户 1001 调用用户判断接口，预期 `false`。
4. 将该用户加入这个开关的白名单，预期 204；再次判断应为 `true`。
5. 重复添加，预期 409；查询名单应包含 1001。
6. 移除该用户，预期 204；再次判断应为 `false`；重复移除应为 404。
7. 删除测试开关。直接查询数据库，确认开关和所属白名单均已清理。

注意区分开关 id 与 flagKey，以及 GET、POST、PUT、DELETE。Postman 请求的显示名称不决定发送地址，实际地址以 Send 左侧的 URL 输入框为准。

## Redis 缓存与故障处理

缓存用于 `findByFlagKey`，即两个 evaluate 接口的开关配置查询。列表和按 id 查询不经过这份缓存；白名单仍查询 MySQL。

- 键：`flag:` 加功能标识，例如 `flag:new-search`。
- 值：Flag 对象序列化后的 JSON 字符串，TTL 为 10 秒。
- 命中时反序列化为 Flag；未命中时查询 MySQL，不存在则返回 404，存在则写回缓存。
- 更新数据库后删除对应缓存；删除开关时，在事务成功提交后的 `afterCommit()` 中清理缓存。
- Redis 读取出现 DataAccessException 时记录警告、查询 MySQL，并跳过本次写回。
- 缓存写入失败仍返回数据库结果；更新/删除后的缓存清理失败只记录日志，不因该 Redis 异常将已成功的数据库操作报成失败。

当前没有删除重试机制。清理失败可能留下旧缓存，由原有 TTL 兜底；TTL 从写入时计时，不会因删除失败重置。在没有并发旧数据回填的条件下，旧键会在剩余 TTL 内失效。本实现不保证强一致性，也不能保证所有并发情形都在 10 秒内恢复一致。MySQL 事务不包含 Redis 操作。

超时时间是等待上限，并非固定延迟；连接立即被拒绝时可能很快回退。缓存故障会增加数据库压力，当前没有熔断、限流或缓存击穿保护。

### Windows + WSL 开发环境

在 Windows PowerShell 中进入 Ubuntu：

```powershell
wsl -d Ubuntu
```

在已安装 Redis 的 Ubuntu 终端中：

```bash
sudo systemctl start redis-server
redis-cli ping
```

返回 `PONG` 表示该客户端能够连接 Redis。退出 redis-cli 不等于停止服务。

当前仓库主配置使用 `spring.data.redis.host=::1`：本地开发中 Windows 的 IPv4 `127.0.0.1:6379` 曾由 Memurai 占用，而 IPv6 `::1:6379` 对应 WSL 转发入口。它是当前机器的配置，不是通用要求；新环境应按实际地址在本地配置中覆盖。务必确认 Java 和命令行连接的是同一个实例及同一个 Redis 数据库。

### 手动验证缓存

选择专用测试开关，记录其 id 和 flagKey：

1. 调用 evaluate 接口，在 10 秒内用 `GET flag:<flagKey>` 和 `TTL flag:<flagKey>` 检查缓存。
2. 更新前执行 `EXPIRE flag:<flagKey> 120`，确认返回 1，避免自然过期干扰判断。
3. 调用 PUT 后立即检查旧键不存在；再次调用 evaluate，确认缓存包含新配置。
4. 对可删除的测试开关重复准备缓存后调用 DELETE，预期 204，缓存不存在，再次 evaluate 返回 404。
5. 停止 Redis 后查询仍存在的开关，预期正常返回 200 和布尔值，并出现读取失败日志；本次不应再尝试写回。
6. Redis 停止期间验证更新、删除，检查数据库结果和缓存清理失败日志；最后重新启动 Redis 并确认恢复。

步骤中的尖括号表示需要替换的内容。Redis 命令在 redis-cli 中执行，HTTP 请求在 Postman 中发送。停止服务使用 `sudo systemctl stop redis-server`。

## Java 调用示例

服务端的 Maven 项目位于仓库根目录。客户端在 `flag-SDK/`，独立示例在 `flag-sdk-demo/`。

1. 启动服务端，并创建一个 flagKey 为 `new-homepage` 的开关。也可以把示例里的 flagKey 改成已有开关。
2. 在仓库根目录执行 `mvn -f flag-SDK/pom.xml install`，把 SDK 安装到本地 Maven 仓库。
3. 在 IDEA 中导入 `flag-sdk-demo/pom.xml` 并运行 `Main.Main`。示例默认访问 `http://localhost:8080`，服务端地址可以通过 `FlagClient` 的构造参数配置。

SDK 使用 Java HttpClient 调用求值接口，连接超时为 3 秒，请求超时为 5 秒。HTTP 200 时将响应体转换为布尔值，非 200 时抛出 `IOException`。SDK 可通过源码在本地构建。

## 事务与验证记录

`FlagService.deleteById()` 使用 Spring 的 `@Transactional`，将删除白名单与删除开关放在一个事务中。

事务验证在删除白名单后模拟删除开关失败，再查询两张表，确认数据回滚。数据库回滚和 HTTP 状态码需要分别判断；提交后的 Redis 清理失败不会撤销已经提交的数据库事务。

接口手动测试覆盖了白名单增删查、重复添加 409、缺失记录 404、空列表、跨开关隔离，以及灰度 0%/100%、总开关关闭、重复请求稳定性和扩大比例保留原用户。正常关联删除由开发者手动验证。以上不是一套自动化回归测试，也不代表并发场景均已验证。

## 当前范围

这是本地学习用原型，已实现 Redis 缓存和基础 Java SDK。管理接口尚未实现登录认证和权限控制。用户编号由客户端传入，功能判断不能当作身份认证或安全授权。并发情况下的缓存旧值回填、白名单添加与开关删除等一致性问题，仍需后续设计和验证。

## 自动化测试

| 测试类 | 验证内容 | 外部依赖 |
|---|---|---|
| FlagServiceTests | 总开关、白名单、灰度边界、缺失开关、缓存命中、Redis 读取失败回退 | Mockito 模拟依赖，不连接真实数据库或 Redis |
| RedisConnectTest | 字符串读写、Flag JSON 转换及 Redis 存取 | Spring 环境和真实 Redis，使用当前激活配置 |
| FlagDeletionIntegrationTests | 数据库连接检查、关联删除、异常回滚 | test profile、真实 feature_flag_test 数据库及完整表结构 |
| FlagServerApplicationTests | Spring 应用上下文加载 | 项目配置及所需环境 |

可在 IDEA 中单独运行测试类。若已配置 Maven 和 JDK 17，可在项目根目录运行：

```bash
mvn -Dtest=FlagServiceTests test
```

运行集成测试前，在 `application-test.properties` 中配置专用测试数据库；测试会检查当前库名必须为 `feature_flag_test`。不要将其指向业务数据库。回滚测试包含 finally 清理；正常删除测试如果中途失败，仍可能遗留测试数据，需要检查清理。

RedisConnectTest 使用 `study:java:name` 和 `study:flag:new-search`，有效期为 60 秒。它没有单独声明 test profile，不要假设所有测试都使用测试库。

开发者已反馈完成缓存正常流程、Redis 故障回退及清理失败场景的手动验证，并运行通过 Redis 读取失败回退单元测试。并发性能需要按测试环境、负载和客户端分别验证。
