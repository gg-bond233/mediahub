# 拾光书影音社区（MediaHub）

一个书影音标记与社区平台：浏览条目、标记想看/在看/看过、写长评与楼中楼回复、关注作者刷动态、看热榜、抢限量典藏周边。**Spring Boot 3 + Redis + Vue 3 个人全栈项目**，后端 12 个 Controller / 50 个接口 / 10 张表，重点练习 Redis 在典型互联网场景下的应用：缓存防护、秒杀链路、Feed 流、排行榜、签到与 UV 统计。

## 功能一览

| 模块 | 说明 |
|---|---|
| 用户 | 手机验证码登录（开发模式验证码直接在响应中返回）、个人资料、积分与等级、每日签到 |
| 书影音条目 | 四大类目（图书/电影/音乐/剧集）分页浏览、详情、标记（想看/在看/看过）、评分 |
| 评论 | 一级长评（支持配图）+ 楼中楼回复、评论点赞、热评排序 |
| 社交 | 关注/粉丝、共同关注、推模式 Feed 流（关注页动态收件箱） |
| 热榜 | 条目热度榜（评分 + 评论 + 标记加权）、热评榜 |
| 秒杀典藏 | 限量典藏周边限时抢购：库存预热、Lua 原子判定、异步落库 |
| 管理端 | 基于 RBAC 的角色权限（`/admin/**`），条目 / 典藏 / 秒杀活动的增删改 |

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5、MyBatis-Plus 3.5.7、Jakarta Validation |
| 中间件 | MySQL 8、Redis 7（Lettuce + Redisson 3.50） |
| 前端 | Vue 3（Composition API）、Vite、Pinia、Vue Router、Axios |
| 工具 | Hutool、Lombok、Docker Compose |

## 核心设计与技术亮点

### 1. 秒杀链路（三道防线）

```
请求 → ① Lua 脚本原子判定（库存是否充足 + 一人一单）
     → ② 通过者写入 Redis Stream 消息队列，立即返回订单号
     → ③ 后台单线程消费：Redisson 分布式锁兜底 + 乐观扣库存 + ACK 确认
         失败消息进 pending-list 重试；库存预热采用 setIfAbsent（不覆盖进行中的数据）
```

- Lua 保证「查库存 + 判一人一单 + 扣减」原子执行，杜绝超卖；
- Stream 异步落库削峰，消费组 ACK + pending-list 重试保证消息不丢；
- 数据库层再以乐观扣库存（`stock > 0` 条件更新）兜底，三层防护互为冗余。

### 2. 多级缓存方案

- **缓存穿透**：空值缓存（短 TTL 的 `""` 占位）；
- **缓存击穿**：互斥锁方案与逻辑过期方案都做了实现，按场景切换；
- **一致性**：Cache Aside，先更新库再删缓存；
- **缓存雪崩**：TTL 加随机抖动。详情查询均经过统一封装的 CacheClient。

### 3. 推模式 Feed 流

- 关注的作者发布评论时，推送到粉丝收件箱（Redis ZSet，score 为评论时间戳）；
- 滚动分页：基于 `lastId + offset` 时间游标，避免深分页；
- 两个内容补齐：**关注时回填**（点关注时把对方历史一级评论补推进收件箱）+ **启动预热任务**（按存量关注关系重建收件箱，ZADD 幂等）。

### 4. 热榜（惰性重建 + 事件增量）

- ZSet 存热度分：互动时 `ZINCRBY` 增量更新，TTL 到期后下一次查询全量重建；
- 热度算法：条目 = 评分×3 + 长评数×20 + 标记数÷10；评论 = 点赞×2 + 回复×3；
- 增量路径与重建公式严格对齐，避免榜单重建后分数跳变。

### 5. 认证与 RBAC

- 三级拦截器链：`RefreshTokenInterceptor`（order=0，恢复用户上下文 + Token 滑动续期）→ `LoginInterceptor`（order=1，登录校验）→ `AdminInterceptor`（order=2，`/admin/**` 角色校验）；
- 会话存 Redis Hash（按 field 局部读写），用户上下文经 ThreadLocal 传递；
- 写接口统一 DTO + Jakarta Validation 参数校验，全局异常处理返回友好提示。

### 6. Redis 数据结构应用地图

| 结构 | 场景 |
|---|---|
| String | 手机验证码、秒杀库存 |
| Hash | 登录会话（Token → 用户信息） |
| Set | 关注关系、共同关注（交集运算） |
| ZSet | 条目/热评榜、Feed 收件箱 |
| BitMap | 每日签到 |
| Stream | 秒杀订单异步落库消息队列 |


## 项目结构

```
mediahub/
├── mediaHub-backend/           # Spring Boot 后端
│   └── src/main/java/com/mediahub/
│       ├── config/             # 拦截器链注册、全局异常处理、Redisson 配置
│       ├── controller/         # 12 个 Controller（用户端 + AdminXxx 管理端）
│       ├── dto/                # 请求入参 DTO（含校验注解）
│       ├── vo/  result/        # 视图对象 / 统一响应封装
│       ├── entity/  mapper/    # MyBatis-Plus 实体与 Mapper
│       ├── service/            # 业务接口与实现（秒杀、Feed、缓存、热榜核心逻辑）
│       ├── task/               # 启动任务：秒杀库存预热、Feed 收件箱预热
│       └── utils/              # CacheClient、拦截器、RedisConstants、ThreadLocal 用户上下文
└── mediaHub-frontend/          # Vue 3 单页应用
```

## 已知限制
- 验证码为开发模式模拟，未接入短信通道；
- 部分会话字段（如改资料后的昵称、角色变更）在重登前不会同步到 Redis 会话
- 秒杀消费端为单线程，针对个人项目体量设计，未做消费水平扩容。
