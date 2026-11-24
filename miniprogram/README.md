# 缝纫机经销商小程序 · 前端

微信原生小程序（无构建、无 npm 依赖）。客户和师傅共用一个包，用页面区分，不拆成两个 App。

**一期对齐**：`docs/03-需求澄清与决策.md`（D1–D11）与 `docs/04-功能实现设计.md`。与旧 `openapi.yaml` / `01` / `02` 冲突时以 **03/04 为准**。

技术栈以仓库里已经能跑的 `miniprogram/` 骨架为准：**原生微信小程序**。未引入 TDesign（要 npm 构建，和「打开目录就能跑」冲突）。

## 在微信开发者工具里运行

1. 安装[微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)（基础库 2.30 以上）。
2. 导入项目，目录选本文件夹 `miniprogram/`（不要选仓库根目录）。
3. AppID 用测试号，或沿用 `project.config.json` 里的 `touristappid`。
4. 和 Spring Boot 联调：先在仓库根目录 `mvn spring-boot:run`。`config.js` 的 `USE_MOCK` 默认 `false`，`baseUrl` 是 `http://127.0.0.1:8080`。开发者工具「详情 → 本地设置」勾选 **不校验合法域名、web-view、TLS 版本以及 HTTPS 证书**。
5. 只看页面、不连 Java：把 `USE_MOCK` 改成 `true`，请求改走 `mock/index.js`。

建议路径：商品详情 → 加入购物车 / 立即购买 → 购物车勾选结算 → 确认页选发货方式 → 订单详情线下转账；首页「一键报修」多台/外购。

也可以先起一份不依赖 Java 的 Mock 服务，再把 `USE_MOCK` 改成 `false`、`baseUrl` 指过去：

```bash
python3 api/mock_server.py 8080
```

## Mock 与支付

| 配置 | 作用 |
| --- | --- |
| `USE_MOCK: true` | 走 `mock/index.js`：购物车、多行下单、线下意图、发货、多行报修 |
| `paymentChannel: OFFLINE` | 一期默认（**D9**）；`enableWxPay: false` 时不调起微信 |
| `enableWxPay: true` + `WXPAY` | 二期开关；`utils/pay.js` 保留 `payWx` / prepay 抽象 |

一期客户侧只有「我将线下转账」→ `POST /api/pay/offline/intent`。`POST /api/pay/prepay` 在 Mock 返回 5001。`mock-success` 仅冒烟测状态机，模拟管理员登记收款，正式客户端不调用。

```bash
node miniprogram/tests/smoke.js    # 购物车 → 多行下单 → 线下意图 → 发货 → 多行报修
node miniprogram/tests/routes.js   # app.json 注册、跳转、语法
```

## 目录

```
miniprogram/
├── app.js / app.json / app.wxss
├── config.js                 USE_MOCK、paymentChannel、enableWxPay
├── utils/api.js              对齐 04 §4.1 客户端 API
├── utils/pay.js              OFFLINE 意图 + WXPAY 预留
├── utils/constants.js        DeliveryMethod / OrderStatus 等
├── mock/index.js             一期全流程假数据
└── pages/                    见对照表
```

约定：金额单位**分**；下单带 `requestId` 幂等；页面不直接 `wx.request`。

## 页面与需求对照（含 D 编号）

Tab：首页、机型、**购物车**、订单、我的。

| 页面 | 路径 | 决策 / 设计 |
| --- | --- | --- |
| 首页 | `pages/index/index` | 入口；快捷含购物车 |
| 机型列表 | `pages/product/list` | 整机/配件浏览 |
| 机型详情 | `pages/product/detail` | **D1** 加入购物车 + 立即购买；默认发货方式 |
| **购物车** | `pages/cart/index` | **D1** 整机零件混加、增减/删除/勾选/合计、去结算 → `/api/cart` |
| 确认订单 | `pages/order/confirm` | **D1/D7** 多行；五行发货；上门安装选日期/时段/师傅；运费 0、安装费待确认 → `POST /api/orders` items[] |
| 选安装师傅 | `pages/order/tech` | 仅 `ON_SITE_INSTALL` 行；`GET /api/technicians/available` |
| 订单列表 | `pages/order/list` | **D2** 状态：待付款/已付款/履约中/已完成（04 §3.1） |
| 订单详情 | `pages/order/detail` | **D2/D9/D10/D11** WAIT 笔次、改价提示、管理员联系方式、线下转账意图；**D6** shipments |
| 支付结果 | `pages/order/result` | 线下意向说明（非微信成功页） |
| 验收 / 评价 | `pages/order/accept` `evaluate` | 履约完成后保留 |
| 报修创建 | `pages/repair/create` | **D5** 一单多行；本店设备或外购（品牌/型号/故障/照片）→ `POST /api/repairs` lines[] |
| 报修详情 / 报价 | `pages/repair/detail` `quote` | 多行展示；维修费线下付款 |
| 选维修师傅 | `pages/repair/tech` | 保留演示入口 |
| 我的设备 / 保修卡 | `pages/asset/*` | 本店+外购资产 |
| 我的 / 地址 / 发票 / 客服 / 登录 | `pages/mine/*` | 地址供下单；客服可作补充联系 |
| 师傅工作台等 | `pages/tech/*` | 安装/维修工单演示（非管理端） |

**不做（本次）**：Web 管理后台、管理员小程序、微信支付实付、快递轨迹第三方 API。

## 和契约 / 文档的出入

前端与 Mock 按 **04 §4.1** 实现；`api/openapi.yaml` 仍可能是旧单行下单 / 定金模型，联调前以后端与 04 对齐为准。已知前端侧约定：

| 项 | 说明 |
| --- | --- |
| `POST /api/orders` | body 为 `{requestId,addressId,items[],remark}`，不再用顶层 `skuId` |
| `POST /api/cart/*` | 04 新增；旧 openapi 可能没有 |
| `GET /api/orders/{id}/shipments` | 按 DeliveryMethod 返回字段（04 §2.4） |
| `POST /api/pay/offline/intent` | 一期可选；微信 prepay 二期 |
| `GET /api/contact/admin` | 管理员微信/电话 |
| `POST /api/repairs` | `{expectDate, lines:[assetId\|external,…]}` |
| 订单状态 | `PENDING_PAYMENT` / `PAID` / `FULFILLING` / `COMPLETED`（不再用 `UNPAID`/`DEPOSIT_PAID` 作主态） |
| `mock-success` | 仅测试用，模拟管理员 offline-confirm |

若真实后端字段名与 04 略有差异，只在本 PR 描述列出，**不改**仓库 `src/` 后端代码。

## 还没做

- 管理端改价 / 加运费 / 拆定金尾款 / 登记收款 / 录入发货（属 Web 后台 P1b）
- 微信支付 JSAPI（P2，`enableWxPay` 打开后走 `payWx`）
- 真实 OSS、订阅消息模板 ID、隐私指引弹窗正式配置
