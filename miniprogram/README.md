# 缝纫机经销商小程序 · 前端

微信原生小程序（无构建步骤、无第三方框架），客户端与师傅端共用一个包，按页面区分。

## 跑起来

1. 打开微信开发者工具 → 导入项目 → 选择本目录；
2. AppID 选「测试号」或填自己的（`project.config.json` 里是 `touristappid`）；
3. 和后端联调：`config.js` 里 `USE_MOCK` 默认 `false`，`baseUrl` 是 `http://127.0.0.1:8080`。先在仓库根目录 `mvn spring-boot:run`。开发者工具「详情 → 本地设置」勾选 **不校验合法域名、web-view、TLS 版本以及 HTTPS 证书**。
4. 只看页面、不连 Java：把 `USE_MOCK` 改成 `true`。商品、下单、我的订单就改走 `mock/index.js`。

```bash
# 起 Mock 服务（Python 3，零依赖）
python3 ../api/mock_server.py 8080
```

## 目录

```
miniprogram/
├── app.js / app.json / app.wxss   全局逻辑、路由与红白设计系统
├── config.js                      USE_MOCK 开关、baseUrl
├── utils/
│   ├── request.js                 统一请求：解包 {code,msg,data}、401 静默重登、Mock 路由
│   ├── api.js                     按接口契约封装的方法（页面只调这里）
│   └── format.js                  分→元、日期、时段
├── mock/index.js                  本地假数据 + 有状态的订单/工单流转
└── pages/
    ├── index/index                首页
    ├── product/list               机型列表（分类 / 排序 / 搜索）
    ├── product/detail             详情 + 选配置 + 定金尾款拆分
    ├── order/confirm              确认订单（地址 / 时间 / 备注 / 选师傅）
    ├── order/tech                 选安装师傅（评分·距离·档期）
    ├── order/list                 订单列表（状态分组）
    ├── order/detail               订单详情（时间轴 / 支付 / 验收 / 取消）
    ├── repair/create              报修（设备 / 故障 / 图片 / 时间）
    ├── mine/mine                  我的（资料 / 订单入口 / 设备档案）
    └── tech/worklist              师傅工作台（接单）
```

## 关键约定

- **页面不直接调 `wx.request`**，一律走 `utils/api.js`，接口路径与 `api/openapi.yaml` 一一对应；
- **金额全部是「分」**，展示前必须过 `format.yuan()`；
- **支付**：`USE_MOCK` 时跳过 `wx.requestPayment`，直接调 `/api/pay/mock-success` 推进订单状态；
  接入真实后端时删掉这个分支即可（`order/confirm.js`、`order/detail.js` 各一处）；
- **登录**：`app.js` 启动即静默 `wx.login`，token 存 storage，过期由 `request.js` 自动重登重放；
- **样式**：所有通用样式在 `app.wxss`（`.card` `.btn` `.tag` `.price` `.tabs` `.tech` 等），
  页面里只写少量内联布局，改配色只需改 `app.wxss` 顶部与 `app.json` 的导航栏色值。

## 与后端的对接清单

| 页面 | 接口 |
| --- | --- |
| 首页 | `GET /api/home` |
| 机型列表 | `GET /api/categories`、`GET /api/products` |
| 机型详情 | `GET /api/products/{id}` |
| 选师傅 | `GET /api/technicians/available` |
| 确认订单 | `GET /api/customer/addresses`、`POST /api/orders`、`POST /api/pay/prepay` |
| 订单列表 | `GET /api/orders` |
| 订单详情 | `GET /api/orders/{id}`、`POST /api/orders/{id}/accept`、`POST /api/orders/{id}/cancel` |
| 报修 | `GET /api/assets`、`POST /api/repairs` |
| 我的 | `GET /api/customer/profile`、`GET /api/assets` |
| 师傅端 | `GET /api/tech/workorders`、`POST /api/tech/workorders/{id}/accept` |

## 还没做的

- 地址新增/编辑页（当前确认页只能取默认地址，接口已就绪）；
- 评价页（`POST /api/evaluations` 已就绪，无界面）；
- 维修报价确认页、维修进度详情页；
- 订阅消息授权引导（后端模板 ID 定了再加）；
- 真实图片上传（现在是本地临时路径，接 OSS 时换成 `wx.uploadFile` + `api.getUploadPolicy`）。
