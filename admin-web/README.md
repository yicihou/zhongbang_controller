# 众邦缝纫机 · 商家 Web 管理端

一期（P1a–P1c）商家后台：登录、工作台、订单、报修、师傅排期、待收款。客户小程序在 `miniprogram/`，本目录是另一个独立前端，共用同一个 Spring Boot 后端和数据库。

金额在接口里用**分**，页面显示为元。响应包一层 `{ code, msg, data, traceId }`，列表为 `{ list, total, page, size }`（`page` 从 1 开始）。

## 技术栈

`docs/02` 指定管理后台为 Vue 3 + Vite + Element Plus。本工程在此之上使用 TypeScript、Pinia、Vue Router。

| 项 | 选型 |
| --- | --- |
| 框架 | Vue 3 + TypeScript |
| 构建 | Vite |
| 组件 | Element Plus（中文） |
| 状态 | Pinia |
| 路由 | Vue Router |
| 测试 | Vitest |

配色参考 `prototype/`：主色 `#C8102E`，浅底 `#FFF5F6`。

## 启动

```bash
cd admin-web
npm install
npm run dev      # http://127.0.0.1:5173
npm test         # 单元测试
npm run build    # vue-tsc 类型检查 + 生产构建
```

默认 **真实后端** `http://127.0.0.1:8080`（`admin-web/.env.development` 里 `VITE_USE_MOCK=false`）。要先在仓库根目录启动 Spring Boot。把 `VITE_USE_MOCK=true` 后重启，才改走浏览器 Mock，这时不启动 Java 也能看订单和报修。

Mock 账号：

| 账号 | 密码 | 姓名 |
| --- | --- | --- |
| admin | admin123 | 李敏 |
| clerk | clerk123 | 周晓 |

登录成功后的 JWT `type` 必须是 `STAFF`（对应后端 `ROLE_STAFF`）。客户或师傅令牌会被拒绝。也可以在登录页粘贴一枚已有的 STAFF JWT。

### 切换 Mock / 真实后端

```bash
# admin-web/.env.development 或 .env.local
VITE_USE_MOCK=false
VITE_API_BASE=http://127.0.0.1:8080
```

`VITE_USE_MOCK=true` 走 Mock。`VITE_API_BASE` 写成空字符串时，请求发到 5173 的 `/api`，由 Vite 代理到 8080。改完要重启 `npm run dev`。

## 页面与决策、接口

左侧有「商品」：列表、新增、编辑、上下架、改价。微信支付、企微推送留到二期。报修列表和详情在关掉 Mock 后走 Spring Boot。

| 页面 | 路由 | 决策 / 阶段 | 接口 |
| --- | --- | --- | --- |
| 登录 | `/login` | D8；JWT `STAFF` | `POST /api/auth/staff/login`（admin / admin123） |
| 商品 | `/products` | 上下架、改价、图片地址。库存只读，要改走「盘点」 | `GET/POST /api/admin/products`，以及 `on-shelf` / `off-shelf` / `price` / `POST /api/admin/stock-adjust` |
| 工作台 | `/` | 待付款 / 待发货 / 待派工 / 待报价 | 用订单、报修列表的 `total` 汇总，§4.2 没有单独的汇总接口 |
| 订单列表 | `/orders` | D2、D7、D8；P1a | `GET /api/admin/orders?status&keyword&page&size` |
| 实时新订单 | 顶栏 | D8；§5 | `GET /api/admin/stream`（SSE）。连续失败 3 次后，每 30 秒 `GET /api/admin/notices?unread=1` |
| 订单详情 | `/orders/:id` | D3、D4、D7、D9、D10、D11；P1b | 见下表 |
| 报修列表 | `/repairs` | D5、D6；P1c | `GET /api/admin/repairs` |
| 报修详情 | `/repairs/:id` | D5、D6、D9；P1c | 报价、按行派工、线下收维修费 |
| 师傅排期 | `/technicians` | §2.5 `technician` / `technician_schedule`。整月月历，点格子或师傅名字直接排单 | `GET /api/admin/technicians`、`GET /api/admin/technicians/schedules?from&to`、`GET /api/admin/technicians/pending-installs`，写用 `POST /api/admin/orders/{id}/assign` |
| 库存流水 | `/stock-logs` | 期初 + 进货 − 销售 ± 盘点 = 结存 | `GET /api/admin/stock-logs?skuId&bizType&from&to&page&size` |
| 待收款 | `/receivables` | D9 | `GET /api/admin/receivables` |

订单详情里的写操作：

| 操作 | 方法与路径 | 请求体（金额为分） |
| --- | --- | --- |
| 改价（按行单价 / 安装费，以及整单 `adjustAmount`，必填原因） | `POST /api/admin/orders/{id}/adjust-price` | `{ reason, adjustAmount?, lines?: [{ orderItemId, unitPrice?, installFee? }] }` |
| 手加运费（绝对值，默认 0） | `POST /api/admin/orders/{id}/freight` | `{ freightAmount }` |
| 手拆定金+尾款，或追加一笔 | `POST /api/admin/orders/{id}/payments` | `{ mode: "SPLIT" \| "APPEND", payments: [{ type, title, amount }] }` |
| 改未付金额 / 标题 | `PATCH /api/admin/payments/{id}` | `{ amount?, title? }` |
| 线下收款（可部分收款） | `POST /api/admin/payments/{id}/offline-confirm` | `{ amount, method, paidAt, voucherUrls?, remark? }` |
| 按行发货 | `POST /api/admin/orders/{id}/shipments` | `{ orderItemId, method?, carrier?, trackingNo?, logisticsCompany?, freightNo?, vehicleNo?, courierName?, courierMobile?, appointAt?, pickupCode?, remark? }` |
| 上门安装派师傅、定时间 | `POST /api/admin/orders/{id}/assign` | `{ orderItemId, technicianId, installDate, installSlot, reason? }` |
| 取消（无成功付款、无发货记录） | `POST /api/admin/orders/{id}/cancel` | `{ reason }` |

发货凭证按行上的方式校验：快递要承运商和快递单号；物流要公司，以及物流单号或车牌；上门送货要送货人和电话；到店自提可生成自提码。安装费只能加在 `ON_SITE_INSTALL` 行上（D4）。上门安装行的 `installFee` 为 `null` 表示管理员还没定价（D3），页面显示「待定」；`0` 表示明确免安装费。改价请求里不带 `installFee` 时保持原值。

允许先发货后收款（2026-11-24 用户决定，docs/03 D12）。未付清（`PENDING_PAYMENT` / `PARTIALLY_PAID`）的订单也能按行发货和派安装师傅，收款仍在「应付与收款」里逐笔登记。订单状态只要还有待付就保持待付款 / 部分付款，详情页另外显示「已发货 n/m 行」。结清后，有行已发出就是 `FULFILLING`，所有行完成就是 `COMPLETED`。已取消、已完成的订单不能发货或派工；同一行已有未取消的发货记录时不能重复发货；已有发货或派工记录的订单不能直接取消。

登记线下收款（订单详情、报修详情、待收款页共用一个弹窗）要填本次收款金额、收款方式和收款时间；凭证和备注选填。
- **金额**：默认是该笔应付的剩余金额，可以改小，但必须大于 0，且不能超过剩余。
- **收款方式**：`BANK_TRANSFER` 银行转账、`WECHAT_TRANSFER` 微信转账、`ALIPAY` 支付宝、`CASH` 现金、`OTHER` 其他。
- **收款时间**：格式 `yyyy-MM-dd HH:mm:ss`，不能晚于当前时间。
- **部分收款**：这笔应付按实收金额记为已收；剩余部分自动生成一笔新的待付，同类型，标题加「（余款）」，`splitFromId` 指向原笔。订单进入部分付款，付清后进入已付款，有发货时进入履约中。报修单在付清前保持待付款。

报修：

| 操作 | 方法与路径 | 请求体 |
| --- | --- | --- |
| 报价 | `POST /api/admin/repairs/{id}/quote` | `{ inWarranty, lines: [{ lineId, lineQuoteAmount }], remark? }` |
| 按行派工 | `POST /api/admin/repairs/{id}/assign` | `{ lineId, technicianId, appointAt }` |
| 完工（每一行都已派工） | `POST /api/admin/repairs/{id}/complete` | 无 |
| 取消（未派工、无成功付款） | `POST /api/admin/repairs/{id}/cancel` | `{ reason }` |
| 线下收维修费 | `POST /api/admin/payments/{id}/offline-confirm` | 与订单相同 |

保外报价会生成一笔 `type=REPAIR` 的待付；保内或 0 元直接变为可派工。这样后台单独就能把维修费收完，不等客户小程序点「确认报价」。

SSE 事件：`ORDER_CREATED` / `REPAIR_CREATED` / `PAYMENT_OFFLINE_PENDING`。`data` 是一条通知：

```json
{
  "id": 1,
  "title": "新订单",
  "body": "SO202611240001 张建国 提交了订单",
  "bizType": "ORDER",
  "bizId": 910233001,
  "eventType": "ORDER_CREATED",
  "readFlag": false,
  "createdAt": "2026-11-24 09:12:10"
}
```

浏览器的 `EventSource` 不能带 `Authorization`。真实模式下用 `fetch` 读同一路径，并带上 Bearer。Mock 不会定时自动生成订单，只有点订单列表右上角「模拟新订单」才生成一笔；顶栏可以「模拟断线」看轮询状态。新订单会响一声、顶栏出通知、侧栏订单出现角标。

订单状态用 §3.1：`PENDING_PAYMENT`、`PARTIALLY_PAID`、`PAID`、`FULFILLING`、`COMPLETED`、`CANCELLED`。报修用 §3.2。

## 和文档、后端的出入

员工登录、商品、订单、发货、派工、线下收款、SSE 通知已经接到 Spring Boot。还没对齐的是：

1. **报修没有后端。** `/api/admin/repairs` 仍只存在于 Mock。关掉 Mock 后报修页会 404。
2. **V1 表保留了旧列。** `V2__product_order_payment.sql` 追加了订单行、发货、改价、收款和通知，没有改 `V1__init.sql`。旧的 `total_amount` 会在保存时写成应付。
3. **派工粒度。** OpenAPI 的 `POST /api/admin/orders/{id}/assign` 只有 `technicianId` 和 `reason`。按 04，这里改成订单行 + 安装日期 + 时段。
4. **线下收款路径。** 旧 OpenAPI 是 `POST /api/admin/payments/offline`。04 是 `POST /api/admin/payments/{id}/offline-confirm`，前端跟 04。
5. **订单状态枚举。** OpenAPI 示例仍是 `PENDING_BALANCE` 等旧值。前端用 04 §3 的新状态。
6. **待收款的 `summary`。** OpenAPI 示例在分页体外还有 `summary`。Java `PageResult` 只有 `list/total/page/size`，前端只读这四项。
7. **已付之后的改价（O1）。** 新应付低于已付时直接拒绝，不自动退差。已付清后再加运费，会另开一笔 `FREIGHT` 待付。
8. **没有「履约完成」接口。** §4.2 未提供把订单打到 `COMPLETED` 的写接口，完成单只在列表里展示。
9. **工作台数字**由列表接口聚合，不是新的 summary API。
