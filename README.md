# 黑马点评（hm-dianping）

一个 **本地生活点评类社区** 的后端服务，核心是 **Redis 各类数据结构在高并发业务场景中的真实落地**：缓存一致性、优惠券秒杀、分布式锁、点赞排行榜、关注与 Feed 流、附近商铺检索、签到统计。

> 本项目为个人学习实践项目，用于展示 Java 后端与 Redis 应用能力。

---

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 语言 / 框架 | Java 8、Spring Boot 2.x、Spring MVC |
| 持久层 | MyBatis-Plus、MySQL 8 |
| 缓存 / 中间件 | Redis（String / Hash / List / Set / ZSet / GEO / BitMap）、Redisson、Lettuce 连接池 |
| 原子操作 | Redis Lua 脚本（`DefaultRedisScript`） |
| 并发处理 | Redisson 可重入锁、JVM 阻塞队列 + 单线程异步消费 |
| 工具 | Hutool、Lombok |

---

## 功能模块

| 模块 | 说明 |
| --- | --- |
| 登录鉴权 | 手机号 + 验证码登录，Redis 存 token，两个拦截器分别做「登录校验」与「token 刷新」 |
| 商铺查询 | 商铺分类缓存、商铺详情缓存（含缓存穿透 / 雪崩 / 击穿处理）、按类型分页 |
| 优惠券秒杀 | 库存预扣、一人一单、Lua 原子校验、异步下单 |
| 探店笔记 | 发布笔记、查看详情、点赞、点赞排行榜 |
| 关注与 Feed | 关注 / 取关、共同关注、关注流滚动分页 |
| 附近商铺 | 按经纬度查询半径内的商铺，按距离排序 |
| 签到 | 按月签到、连续签到天数统计 |
| 文件上传 | 笔记图片上传 |

---

## 关键实现

### 1. 缓存三件套（`ShopServiceImpl` + `CacheClient`）

| 问题 | 方案 | 实现 |
| --- | --- | --- |
| 缓存穿透 | 空值缓存 | 查库为 null 时也写一个短 TTL 的空值，避免同一 key 反复打库 |
| 缓存雪崩 | TTL 随机化 | 缓存过期时间加随机值，避免大量 key 同时失效 |
| 缓存击穿 | 互斥锁重建 / 逻辑过期 | `queryWithMutex`：未命中时抢 `lock:shop:{id}` 再重建；`queryWithLogicalExpire`：缓存里存逻辑过期时间，过期后抢锁异步重建，读取方先返回旧值 |

### 2. 优惠券秒杀（`VoucherOrderServiceImpl`）

```
请求 → Lua 脚本（原子执行：库存校验 + 一人一单校验 + 扣减库存）
     → 通过则返回 0，进入下单流程
     → 单线程执行器异步消费阻塞队列 → 事务代理落库
```

- **原子性**：`seckill.lua` 在 Redis 内一次性完成「判断库存、判断用户是否已下单、扣库存、记录用户」，避免并发下的超卖与重复下单；
- **限流削峰**：校验通过后把订单任务丢进 `ArrayBlockingQueue`，由单线程 `ExecutorService` 异步消费，请求线程立即返回；
- **事务**：异步消费通过 `AopContext` 拿到代理对象调用 `createVoucherOrder`，保证 `@Transactional` 生效；
- **兜底**：`redissonClient.getLock("lock:order" + userId)` 对同一用户加分布式锁，防止队列模式下的一人多单。
  （注释中保留了 Redis List 阻塞队列的版本，可用于对比内存队列与 Redis 队列的差异。）

### 3. 点赞与排行榜（`BlogServiceImpl`）

- 点赞关系用 **ZSet**：`key = blog:liked:{blogId}`，`member = userId`，`score = 时间戳`；
- 按时间滚动分页：以 `score < lastTime` 反复取一页，避免 offset 分页的数据重复与深分页性能问题；
- 点赞数用独立计数器维护，按点赞数排序实现 **Top N 排行榜**。

### 4. 关注与 Feed 流（`FollowServiceImpl` / `BlogServiceImpl`）

- 关注关系用 **Set** 存储，两个 Set 求交集得到「共同关注」；
- Feed 流采用 **推模式**：发布笔记时把笔记 ID 写入粉丝的收件箱 ZSet，读取时按 `score` 滚动分页拉取。

### 5. 附近商铺（`ShopServiceImpl`）

把商铺经纬度写入 **GEO** 集合，按半径检索并支持按距离由近到远排序分页，避免在 MySQL 里做经纬度范围计算。

### 6. 签到（`UserServiceImpl`）

用 **BitMap** 按位记录每月签到情况（`key = sign:{userId}:{yyyyMM}`，第几天就第几位），通过位运算统计连续签到天数，单用户单月只占 4 字节。

---

## 项目结构

```
dianping
└── hm-dianping
    └── src/main
        ├── java/com/hmdp
        │   ├── controller   接口层
        │   ├── service      业务层（含 impl）
        │   ├── mapper       持久层
        │   ├── entity/DTO/VO
        │   ├── config       Redis / MVC / MyBatis-Plus 配置
        │   ├── interceptor  登录校验、token 刷新
        │   ├── handler      token 刷新 Handler
        │   ├── utils        Redis 常量、正则、密码工具、缓存客户端
        │   └── dto / task
        └── resources
            ├── application.yaml      仅占位符，真实值在 application-dev.yaml
            ├── db/hmdp.sql           建表 + 初始化数据
            └── seckill.lua           秒杀校验脚本
```

---

## 本地运行

### 环境要求

- JDK 8
- MySQL 8
- Redis 6+
- Maven 3.6+

### 步骤

1. **建库导数据**

   ```bash
   mysql -uroot -p -e "CREATE DATABASE hmdp DEFAULT CHARSET utf8mb4;"
   mysql -uroot -p hmdp < hm-dianping/src/main/resources/db/hmdp.sql
   ```

2. **配置本地参数**

   `application.yaml` 里全部是 `${...}` 占位符，需自行创建 `application-dev.yaml`：

   ```yaml
   spring:
     datasource:
       driver-class-name: com.mysql.cj.jdbc.Driver
       url: jdbc:mysql://localhost:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai
       username: root
       password: 你的密码
     redis:
       host: localhost
       port: 6379
       password: 你的密码
   ```

3. **启动**

   ```bash
   mvn clean package -DskipTests
   java -jar hm-dianping/target/hm-dianping-0.0.1-SNAPSHOT.jar
   ```

   服务端口 `8081`。

---

## Docker 部署

仓库自带 `Dockerfile`（多阶段构建）与 `docker-compose.yml`（MySQL 5.7 + Redis + 应用）：

```bash
docker compose up -d --build
```

启动后访问 `http://localhost:8081`。

设计说明：

- **多阶段构建**：构建阶段用 Maven + JDK 8 打包，运行阶段只保留 JRE 8；
- **配置外置**：`application-dev.yaml` 被 `.dockerignore` 排除，镜像里不含任何密钥，配置通过环境变量注入（`SPRING_DATASOURCE_URL`、`SPRING_REDIS_HOST` 等）；
- **MySQL 版本**：项目固定使用 `mysql-connector-java 5.1.47`，只能连 MySQL 5.7 及以下；换成 MySQL 8 会因 `caching_sha2_password` 认证插件不兼容而启动失败，所以 compose 里用的是 `mysql:5.7`；
- **数据初始化**：首次启动自动执行 `hm-dianping/src/main/resources/db/hmdp.sql`。

只构建镜像：`docker build -t hm-dianping:0.0.1 .`

## 说明

- 数据库脚本：`hm-dianping/src/main/resources/db/hmdp.sql`
- 短信验证码为本地模拟（日志打印），无需真实短信服务即可登录调试
- 敏感配置不入库，克隆后需自行创建 `application-dev.yaml`
