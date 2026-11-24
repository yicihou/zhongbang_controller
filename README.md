# Zhongbang Controller

缝纫机经销商小程序后端骨架（Spring Boot 3 + Java 21）。

业务契约以仓库内文档为准：

- `docs/02-技术架构与数据库设计.md`
- `api/openapi.yaml` / `api/接口清单.md`
- 小程序：`miniprogram/`
- 商家 Web 后台：`admin-web/`（见该目录 README）

当前 Java 代码只落实架构评审中的 **H1–H6** 骨架，业务模块多数仍是空包，便于你继续扩展。

## 已对齐的能力（H1–H6）

| 项 | 说明 |
| --- | --- |
| H1 | MyBatis-Plus；`module/*` + `adapter/*` + `security` + `job`；`/api/assets` 替换原 device 示例 |
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
├── common/                 统一响应、错误码、TraceId
├── config/                 Web / MyBatis / 基础设施开关
├── security/               JWT、三角色、过滤器
├── support/                幂等、分布式锁、消息发布（Redis/内存）
├── adapter/                wx / oss / map 外部能力空壳
├── module/                 auth / customer / product / ... 领域包
└── job/                    定时任务占位
```

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
| GET | `/api/products` | 公开 |
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

1. 按 `api/接口清单.md` 在空模块中补 Controller/Service/Mapper。
2. 将 `WxAuthAdapter` 换成 WxJava；补齐 `/api/auth/phone`。
3. 订单状态机、支付回调验签、延迟关单消费者落在 `module/order`、`module/payment`、`job`。
4. 中优先级项（SpringDoc、CI、更完整异常映射等）见项目 Agent Store 中的架构评审文档。
