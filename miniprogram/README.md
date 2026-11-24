# 缝纫机经销商小程序 · 前端

微信原生小程序（无构建、无 npm 依赖）。客户和师傅共用一个包，用页面区分，不拆成两个 App。

技术栈以仓库里已经能跑的 `miniprogram/` 骨架为准：**原生微信小程序**。架构文档 §1 写过 TDesign Miniprogram，这里没有引入，原因是：

- 现有页面和 `prototype/index.html` 用的是需求文档 §9 的红白样式，不是 TDesign 的默认组件皮肤；
- 引入组件库要走 npm 构建，微信开发者工具里多一步，和「打开目录就能跑」冲突；
- 主包目前都是轻量 WXML，远小于 1.5MB，所以也没有按架构文档拆分包，避免把已经接好的路径拆散。

## 在微信开发者工具里运行

1. 安装[微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)（基础库 2.30 以上，工程里写的是 3.5.5）。
2. 导入项目，目录选本文件夹 `miniprogram/`（不要选仓库根目录）。
3. AppID 用测试号，或沿用 `project.config.json` 里的 `touristappid`。
4. 编译即可。默认 `config.js` 的 `USE_MOCK = true`，不需要后端、不需要合法域名。

建议先看这几条路径：首页 → 机型详情 → 选配置 → 确认订单 → 选师傅 → 支付结果；订单里点「待付尾款」样例单；首页「一键报修」；「我的 → 师傅工作台」接单、打卡、填安装单。

真机或联调：

```bash
# 仓库根目录，零依赖
python3 api/mock_server.py 8080
```

把 `config.js` 的 `USE_MOCK` 改成 `false`，`baseUrl` 指向上面的地址或 Java 后端。开发者工具「详情 → 本地设置」勾选 **不校验合法域名、web-view、TLS 版本以及 HTTPS 证书**。

`api/mock_server.py` 与小程序内置 Mock 覆盖同一套契约。报修列表、工单详情这两个路径契约里没有单列，两个 Mock 都补了，见下文「和契约不一致的地方」。

## Mock 开关

| 配置 | 作用 |
| --- | --- |
| `USE_MOCK: true` | 请求进 `mock/index.js`，内存里能走下单、支付、验收、报修、报价、接单、打卡、完工 |
| `USE_MOCK: false` | `utils/request.js` 请求 `baseUrl`，自动带 `Authorization`，401 / 业务码 2001 会静默重新 `wx.login` 并重放一次 |
| `subscribeTmplIds` | 订阅消息模板 ID。空数组时支付结果页只提示「尚未配置」，不会调用 `wx.requestSubscribeMessage` |

支付在 Mock 下不会调 `wx.requestPayment`，而是 `POST /api/pay/mock-success`（生产没有这个接口）。接真实商户号后保持 `USE_MOCK = false` 即可，`utils/pay.js` 会走预支付参数。

自测（不打开开发者工具，桩掉 `wx`）：

```bash
node miniprogram/tests/smoke.js    # 下单 → 定金 → 验收 → 尾款 → 报修 → 接单 → 地址 → 报价 → 打卡
node miniprogram/tests/routes.js   # app.json 注册、跳转路径、页面脚本语法
```

## 目录

```
miniprogram/
├── app.js / app.json / app.wxss   登录、tabBar、红白样式
├── styles/theme.wxss              色彩变量（与需求 §9 一致）
├── config.js                      USE_MOCK、baseUrl、客服电话、订阅消息模板
├── components/
│   ├── price/                     分 → 元
│   ├── empty/                     空状态
│   └── tech-card/                 师傅卡片（评分、距离、档期）
├── utils/
│   ├── request.js                 解包 {code,msg,data}、401 重登
│   ├── api.js                     页面只调这里，路径对齐 openapi
│   ├── pay.js                     预支付 + Mock 支付
│   ├── upload.js                  选图 / 30 秒视频；真环境走上传签名
│   ├── auth.js                    手机号组件回调
│   └── format.js                  金额、日期、星级、手机号打码
├── mock/index.js                  本地假数据，订单和工单有状态
└── pages/                         见下表
```

约定：

- 页面不直接写 `wx.request`；
- 金额接口单位是**分**，展示用 `format.yuan()` 或 `<price>`；
- 下单带 `requestId`，同一键重复提交不会生成两笔订单。

## 页面与需求

Tab：首页、机型、订单、我的。导航栏和页面底色按原型：首页 / 我的用品牌红导航，交易和服务页用白导航。

| 页面 | 路径 | 需求 |
| --- | --- | --- |
| 首页 | `pages/index/index` | 原型 01；FR-B01 入口、热销机型 |
| 机型列表 | `pages/product/list` | FR-B01 分类、搜索、排序；价格区间在已返回结果上过滤 |
| 机型详情 / 选配置 | `pages/product/detail` | 原型 02；FR-B02 参数与保修、FR-B03 配置、FR-B04 定金尾款、FR-B05 缺货登记 |
| 确认订单 | `pages/order/confirm` | FR-C01 地址、日期时段、数量、备注、发票 |
| 选安装师傅 | `pages/order/tech` | 原型 03；FR-C02 评分、距离、技能、档期；排序 recommend / distance / rating |
| 支付结果 | `pages/order/result` | FR-C03 订单号与下一步；可引导订阅消息 |
| 订单列表 | `pages/order/list` | 原型 05；FR-C05 待付款 / 待安装 / 待验收 / 待尾款 / 已完成 / 售后 |
| 订单详情 | `pages/order/detail` | FR-C05 时间轴、师傅、金额；未支付可取消（FR-C06 的自助部分） |
| 验收 | `pages/order/accept` | FR-C04 的触发：通过才生成尾款，不通过退回返工 |
| 评价 | `pages/order/evaluate` | FR-E07 星级、标签、文字、图片 |
| 报修 | `pages/repair/create` | 原型 04；FR-E01 故障分类、9 图、30 秒视频、上门时间 |
| 选维修师傅 | `pages/repair/tech` | FR-E03；保外提示上门 / 检测费 |
| 维修进度 | `pages/repair/detail` | FR-E05 |
| 报价确认 | `pages/repair/quote` | FR-E04，超过 300 元必须确认；拒绝则关闭 |
| 我的设备 | `pages/asset/list` | FR-A04 列表与扫码绑定 |
| 电子保修卡 | `pages/asset/warranty` | FR-A04 / FR-E02 保修说明 |
| 我的 | `pages/mine/mine` | FR-A05 |
| 地址 | `pages/mine/address` `address-edit` | FR-A03 增删改、默认、微信导入、地图选点 |
| 发票抬头 | `pages/mine/invoice` | FR-C01 发票需求。抬头存在本地，下单时放进 `invoice` 字段 |
| 客服 | `pages/mine/service` | FR-A05 |
| 隐私说明 | `pages/mine/privacy` | 非功能里的隐私合规 |
| 登录与手机号 | `pages/mine/login` | FR-A01 静默登录、FR-A02 手机号组件 |
| 师傅工单列表 | `pages/tech/worklist` | FR-G01 |
| 工单详情 | `pages/tech/detail` | FR-G02 接单确认时间、拒单、导航 |
| 到场打卡 | `pages/tech/checkin` | FR-G03，超过 500 米返回 3005。开发者工具可用「模拟到场」 |
| 安装单 | `pages/tech/install` | FR-G04 机身码、至少 3 张照片、安装项、客户签字 |
| 维修单 | `pages/tech/repair` | FR-G05 措施、换件、旧件归属、试机结果 |
| 收入与结算 | `pages/tech/income` | FR-G07 的演示页，见下方未实现项 |

## 和契约不一致或契约没写的地方

这些是为了把页面跑通才加的，联调时要对一下：

| 项 | 处理 |
| --- | --- |
| `GET /api/repairs` 列表 | OpenAPI 只有 `GET /api/repairs/{id}`。内置 Mock 和 `api/mock_server.py` 都提供了列表。`USE_MOCK = false` 且后端没有该接口时，`api.listRepairs()` 改为用本地记下的工单号逐条拉详情 |
| `GET /api/tech/workorders/{id}` | 列表项不够详情页用。两个 Mock 都按 WorkOrder 模型返回这一条；若 404，前端再从列表里找 |
| `POST /api/tech/workorders/{id}/reject` | 契约没有拒单。仅 `USE_MOCK` 会改状态；连真实后端时提示「接口未在契约中」，不会发请求 |
| 改约 | 需求写了要客户同意。契约没有接口，详情页只在接单时带 `appointmentAt`（后端可以忽略这个字段） |
| 维修支付 | `POST /api/pay/prepay` 的 `type=REPAIR` 时，`orderId` 传工单 id。这是沿用契约里的字段名 |
| 发票抬头、到货通知 | 没有单独接口。抬头存本地存储；缺货只做前端提示 |
| 结算金额 | 没有接口。收入页按「安装 80 元、维修 50 元」本地估算，页面上写了是演示 |
| 品牌 / 针数 / 电压筛选 | 列表接口没有这些查询参数，也没有品牌字段的筛选语义。价格区间是前端对当前结果过滤 |
| 排序「最快上门」 | 契约枚举是 `recommend` / `distance` / `rating`，页面用这三个，推荐序里已经包含档期 |

## 还没做、需要产品确认的

- **商家后台 / 客户 360**（原型第 6 屏，FR-F）不在这个小程序里。
- **FR-G06 配件领用扣库存、FR-G08 排班请假**：没有客户端接口。维修单可以登记换了什么件，但不会扣库存。
- **FR-C06 已付定金的退款审批、FR-C08 赊销、FR-E06 以外的线下收款登记、FR-E08 保修到期订阅、FR-E09 投诉**：客户端没有对应接口，未做独立页面。未支付订单可以自助取消。
- **FR-B06 收藏对比、FR-B07 报价单、FR-C07 电子签收单**：优先级是 P1/P2，未做。
- **真实 OSS 直传** 的代码在 `utils/upload.js`，Mock 和示例 host 会退回本地临时路径。
- **订阅消息、隐私保护指引弹窗** 需要公众平台里的模板 ID 和类目配置。`__usePrivacyCheck__` 先没开，避免开发阶段把选图和定位拦住。
- 架构文档写文件接口是 `/api/file/policy`，接口清单是 `/api/upload/policy`。前端跟的是接口清单。
- 文档待确认项 Q-01～Q-08（多门店、商户号、师傅用工性质等）仍然会影响支付、结算和权限，前端没有替你做决定。
