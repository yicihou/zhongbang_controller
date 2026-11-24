# Zhongbang Controller

缝纫机经销商小程序 / 管理端后端（Spring Boot 3 + Java 21）。

业务契约：

- `docs/03-需求澄清与决策.md`（D1–D11）
- `docs/04-功能实现设计.md`（P1a/P1b/P1c）
- `api/openapi.yaml`

## 当前阶段：P1a

已落地：

| 能力 | 说明 |
| --- | --- |
| Flyway `V2` | 购物车、订单行、发货单、报修票/行、排班、支付阶段字段、管理端通知、演示 SKU |
| 购物车 | `GET/POST /api/cart`、`PUT/DELETE /api/cart/items/{id}` |
| 多行下单 | `POST /api/orders`：标价合计、运费默认 0、一笔 `FULL/WAIT/OFFLINE`、SSE `ORDER_CREATED` |
| 管理端订单 | `GET /api/admin/orders`、详情；`POST /api/auth/staff-login`（dev stub） |
| 实时推送 | `GET /api/admin/stream`（SSE）+ `GET /api/admin/notices` |
| 支付渠道 | `PaymentChannelHandler`：`OFFLINE` 已实现登记；`WXPAY` stub |
| 状态机 | `OrderStateMachine` / `RepairStateMachine` / `PaymentStateMachine` |

P1b/P1c 接口已挂 Controller，业务多为 TODO（改价、运费、定金尾款、发货、派工、报修闭环等）。

## 技术栈

- Spring Boot 3.4 / Java 21 / Maven
- MyBatis-Plus + Flyway（无 JPA ddl-auto）
- Spring Security + JWT（`CUSTOMER` / `STAFF` / `TECHNICIAN`）
- Redis / RabbitMQ 可选（默认内存回退）

## 本地启动

```bash
mvn spring-boot:run
```

默认 `dev`：H2 + Flyway；端口 `8080`。

### P1a 冒烟

```bash
# 客户登录
curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"code":"demo"}'

# 加购 + 下单（Bearer 换成客户 token）
curl -s localhost:8080/api/cart -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"skuId":1011,"quantity":1,"deliveryMethod":"EXPRESS"}'

curl -s localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"requestId":"req-1","items":[{"skuId":1011,"quantity":1,"deliveryMethod":"EXPRESS"}]}'

# 管理端
curl -s localhost:8080/api/auth/staff-login -H 'Content-Type: application/json' -d '{"username":"admin"}'
curl -s 'localhost:8080/api/admin/orders' -H "Authorization: Bearer $STAFF"
```

演示 SKU：`1011`（整机 328000 分）、`2011`（零件 8800 分）。

## 测试

```bash
mvn test
```

覆盖：安全骨架、`Order`/`Repair` 状态机、P1a 购物车→下单→SSE 通知→管理端列表。

## 目录

```text
src/main/java/com/zhongbang/controller
├── common/enums + api + exception
├── module/order|payment|aftersale|notify|admin|product|...
├── security/
└── support/  幂等、锁、消息
src/main/resources/db/migration
├── V1__init.sql
└── V2__cart_fulfillment_repair_payment.sql
```

## 后续（按设计分期）

1. **P1b**：改价、手加运费、定金/尾款拆分、线下收款闭环、五行发货、安装派工  
2. **P1c**：报修一单多行 + 外购 + 报价 + 维修费支付  
3. **P2**：微信支付实付、管理端小程序、企微/订阅推送  
