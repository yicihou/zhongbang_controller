# Zhongbang Controller

缝纫机经销商小程序后端骨架（Spring Boot 3 + Java 21）。

业务契约以仓库内文档为准：

- `docs/05-代码导读（初学者版）.md`：目录、一次请求怎么走、在 IDEA 里打开并启动
- `docs/02-技术架构与数据库设计.md`
- `api/openapi.yaml` / `api/接口清单.md`
- 小程序：`miniprogram/`
- 商家 Web 后台：`admin-web/`（见该目录 README）

商品、订单、报修、线下收款和员工登录已经接到同一套数据库。微信支付还没接。

## 已对齐的能力（H1–H6）

| 项 | 说明 |
| --- | --- |
| H1 | MyBatis-Plus；`controller` / `service` / `mapper` / `pojo` 按业务再分，另有 `adapter`、`security`、`job`；`/api/assets` 替换原 device 示例 |
| H2 | Spring Security + JWT；角色 `CUSTOMER` / `STAFF` / `TECHNICIAN` |
| H3 | Flyway `db/migration`；不再使用 JPA/`ddl-auto` |
| H4 | 响应体 `{code,msg,data,traceId}`；分页 `page` 从 1、`list`；路径对齐 OpenAPI |
| H5 | Redis/RabbitMQ 接入位 + 本地内存/NoOp 回退；`docker/docker-compose.yml` |
| H6 | 默认关闭 H2 Console；Actuator 仅暴露 `health`（含 probes），其余拒绝 |

## 技术栈

- Spring Boot 3.4 / Java 21 / Maven
- MyBatis-Plus + Flyway
- Spring Security + JJWT
- Redis / RabbitMQ（可选，默认关闭）

## 目录（后端）

```text
src/main/java/com/zhongbang/controller
├── controller/             HTTP 入口，按业务再分（auth / customer / product / system）
├── service/                业务规则（auth、customer 已有类；实现可放 <业务>/impl）
├── mapper/                 表访问，@MapperScan 扫这里
├── pojo/                   entity / dto / vo，按业务再分
├── common/                 统一响应、错误码、TraceId
├── config/                 Web / MyBatis / 基础设施开关
├── security/               JWT、三角色、过滤器
├── support/                幂等、分布式锁、消息发布（Redis/内存）
├── adapter/                wx / oss / map 外部能力空壳
└── job/                    定时任务占位
```

请求路径：`controller/<业务>` 收 HTTP → `service/<业务>` 写规则 → `mapper/<业务>` 访问数据库。数据对象在 `pojo/<业务>`。商品、订单、报修、收款、通知、师傅已经有实现。报表、定时任务仍是占位。

## 本地联调（管理端 + 小程序 + 后端）

三端共用这一套 Spring Boot 和同一张库。默认 `dev` 是内存 H2，停掉进程数据就没了，但会重新放进管理员和四台样例商品。

1. 启动后端（仓库根目录）：

```bash
mvn spring-boot:run
```

看到端口 `8080` 即可。初始管理员 **admin / admin123**，老板 **boss / boss123**（密码在库里是 BCrypt）。客户档案只有老板能进。

2. 启动管理端：

```bash
cd admin-web
npm install
npm run dev
```

浏览器打开 `http://127.0.0.1:5173`。开发配置默认 `VITE_USE_MOCK=false`，请求发到 `http://127.0.0.1:8080`（见 `admin-web/.env.development`）。用 admin / admin123 或 boss / boss123 登录。左侧有「商品」「报修」，老板还能看到「客户」。

客户分析要调用 DeepSeek 时，启动前设置环境变量 `DEEPSEEK_API_KEY`（不要写进仓库）。可选 `DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`。没配密钥时页面会提示，接口不会 500。开发环境老板可以点「生成演示数据」；生产配置里这个开关是关的。

只想看页面、不启 Java 时，把 `admin-web/.env.development` 改成 `VITE_USE_MOCK=true`，或在 `admin-web/.env.local` 里写同样一行，然后重启 `npm run dev`。Mock 另有 clerk / clerk123。

3. 微信开发者工具打开小程序：

- 导入目录 `miniprogram/`。
- 右上角「详情 → 本地设置」，勾选 **不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**。否则工具不会请求 `http://127.0.0.1:8080`。
- `miniprogram/config.js` 里 `USE_MOCK` 默认 `false`，商品、下单、我的订单和报修走真实接口。改成 `true` 就回到 `miniprogram/mock`，不访问后端。两个开关互不影响。页面显示和下拉会重新请求；订单和报修详情大约每 10 秒查一次 `GET /api/me/changes`。

小程序要先能登录。本地登录不访问微信：开发者工具里 `wx.login` 拿到的 code 会变成 `dev_` 加 code 的客户。下单支持购物车多行；上门安装的安装费先是空的，管理端显示「待定」。

## 本地启动（无需 Redis/MQ）

```bash
mvn spring-boot:run
```

默认 `dev`：H2 内存库 + Flyway；`zhongbang.redis.enabled=false`、`zhongbang.rabbitmq.enabled=false`。

端口：`8080`

### 常用接口

| Method | Path | 鉴权 |
| --- | --- | --- |
| POST | `/api/auth/login` | 公开（本地 stub：`code` → openid） |
| POST | `/api/auth/staff/login` | 公开。admin / admin123，返回 type=STAFF 的 JWT |
| GET | `/api/products` | 公开，只含上架商品 |
| GET/POST/PUT/DELETE | `/api/admin/products` | STAFF。另有 `.../on-shelf`、`.../off-shelf`、`.../price` |
| POST | `/api/orders` | CUSTOMER。body 可带 `items[]` |
| GET | `/api/orders` | CUSTOMER，我的订单 |
| GET | `/api/admin/orders` | STAFF |
| POST | `/api/admin/orders/{id}/shipments` | STAFF。未付款也可发货 |
| POST | `/api/admin/orders/{id}/assign` | STAFF。派安装师傅 |
| POST | `/api/admin/payments/{id}/offline-confirm` | STAFF。部分收款会拆出「（余款）」 |
| GET | `/api/admin/receivables` | STAFF，待收款 |
| GET | `/api/admin/stream` | STAFF，SSE。新订单 `ORDER_CREATED`，新报修 `REPAIR_CREATED` |
| GET | `/api/admin/notices` | STAFF。SSE 断了之后轮询 `unread=1` |
| POST | `/api/repairs` | CUSTOMER。多行报修，可带外购设备 |
| GET | `/api/repairs` `/api/repairs/{id}` | CUSTOMER，我的报修 |
| GET | `/api/admin/repairs` | STAFF。工作台待报价用 `status=SUBMITTED` |
| POST | `/api/admin/repairs/{id}/quote` | STAFF。保外生成待付，保内或 0 元变为可派工 |
| POST | `/api/admin/repairs/{id}/assign` | STAFF。按行派师傅 |
| POST | `/api/admin/repairs/{id}/complete` | STAFF。全部行已派工后完工 |
| POST | `/api/admin/repairs/{id}/cancel` | STAFF。`{ reason }` |
| GET | `/api/me/changes?since=` | CUSTOMER。轻量变更检查，供小程序约 10 秒轮询 |
| GET | `/api/assets` | CUSTOMER |
| POST | `/api/assets/bind` | CUSTOMER |
| GET | `/api/system/ping` | 公开 |
| GET | `/actuator/health` | 公开 |

登录示例：

```bash
curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"code":"demo"}'
```

## 启用 Redis / RabbitMQ

```bash
docker compose -f docker/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=infra mvn spring-boot:run
```

`infra` profile 会连 Compose 中的 MySQL/Redis/RabbitMQ，并切换到 Redis/Rabbit 实现（幂等、锁、消息）。

## 测试

```bash
mvn test
```

## 后续扩展建议

1. 报修仍只在管理端 Mock。真实后端还没有 `/api/admin/repairs`。
2. 将 `WxAuthAdapter` 换成 WxJava；补齐 `/api/auth/phone`。微信支付预下单仍未做，小程序提交的是意向单，收款在管理端登记。
3. 中优先级项（SpringDoc、CI、更完整异常映射等）见项目 Agent Store 中的架构评审文档。
