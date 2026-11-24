# 缝纫机经销商小程序 · 方案交付包

一套面向**单经销商（含其门店）**的微信小程序 + 商家后台方案：客户端下单、指定安装师傅、报修选维修师傅；商家端看全客户订单、已付/待付、偏好与设备档案。后端 Java（Spring Boot 3），前端红白配色。

> 当前进度：**方案文档 + 接口契约 + 小程序前端（可跑）** 已完成；Java 后端与商家后台待开发。

## 交付物

| 文件 / 目录 | 内容 | 适合谁看 |
| --- | --- | --- |
| `缝纫机经销商小程序-需求规格说明书.docx` | 可行性分析、角色权限、6 张流程图、功能需求（FR-xxx）、业务规则、非功能需求、红白视觉规范、验收标准、12 周迭代计划 | 老板 / 产品 / 全员 |
| `缝纫机经销商小程序-技术架构与数据库设计.docx` | 技术选型、分层架构、状态机/支付/派单/库存等关键设计、ER 与建表 SQL、接口清单、部署运维、工作量估算 | Java 开发 / 前端 |
| `api/接口文档.html` | **可读版接口手册**：34 个接口、字段表、请求/响应示例、错误码、数据模型，带搜索 | 前后端 |
| `api/openapi.yaml` / `openapi.json` | OpenAPI 3.0 契约，可导入 Apifox / Postman / Swagger | 前后端 |
| `api/接口清单.md` | 一页纸接口总表（模块 / 方法 / 路径 / 说明） | 快速查阅 |
| `api/mock_server.py` | 零依赖 Mock 服务，`python3 api/mock_server.py 8080` 即用 | 前端 / 联调 |
| `miniprogram/` | **微信原生小程序**：10 个页面，客户端 + 师傅端，内置 Mock 数据层 | 前端 |
| `prototype/index.html` | 六屏红白配色视觉稿（浏览器直接打开） | 设计 / 老板 |
| `docs/*.md` `docs/*.pdf` | 两份文档的 Markdown 原文与 PDF 版 | 维护 / 传阅 |
| `diagrams/fig1~fig7.png` | 高清流程图与架构图（300dpi，可直接进 PPT） | 汇报 |

## 三个结论

1. **可行**：技术全是微信生态标准能力；真正门槛是**微信认证 + 支付商户号 + 域名备案**，需与开发并行（约 10–20 个工作日）。投入约 7.5 万元，按年销 300 台测算 6–10 个月回收。
2. **范围**：先做"卖机器 + 修机器"闭环（12 周），多商户 SaaS 等列入演进，但表结构预留 `tenant_id` / `store_id`。
3. **架构**：Spring Boot 3 单体 + MySQL 8 + Redis + RabbitMQ + OSS，按领域分包。日订单 200 单量级，微服务是纯负担。

## 接口文档怎么用

```bash
# 1. 浏览器打开可读版
open api/接口文档.html

# 2. 起 Mock 服务（前端联调用）
python3 api/mock_server.py 8080
curl http://127.0.0.1:8080/api/home

# 3. 改了契约后重新生成文档（需要 PyYAML）
pip install pyyaml
python3 api/build_docs.py
```

Mock 服务有内存状态：下单 → 支付 → 验收 → 尾款 全流程可跑通，`/api/pay/mock-success` 用来模拟支付回调（**生产不存在**）。

## 小程序怎么跑

微信开发者工具 → 导入 `miniprogram/` 目录 → 直接用（`USE_MOCK = true`，不需要后端）。
真机/联调时改 `miniprogram/config.js` 的 `USE_MOCK = false`，并勾选"不校验合法域名"。

详见 `miniprogram/README.md`。

## 当前实现进度

| 模块 | 状态 |
| --- | --- |
| 需求 / 架构文档 | ✅ 完成 |
| 接口契约（34 个） | ✅ 完成 |
| Mock 服务（Python） | ✅ 完成，全流程可跑 |
| 小程序：首页 / 机型 / 详情 / 选师傅 / 下单 / 订单列表 / 订单详情 / 报修 / 我的 / 师傅工作台 | ✅ 完成（10 页） |
| 小程序：地址管理、评价、维修报价确认、图片直传 | ⏳ 待补（接口已就绪） |
| Java 后端 | ⏳ 未开始 |
| 商家后台（Vue） | ⏳ 未开始 |

## 需要你确认的事

| 编号 | 问题 | 影响 |
| --- | --- | --- |
| Q-01 | 只有一家店，还是多门店 / 多经销商？ | 决定是否马上启用 `tenant_id` / `store_id` |
| Q-02 | 微信支付商户号是否已办？ | 决定 MVP 能否直接在线收款 |
| Q-03 | 师傅是自有员工还是外包？ | 决定结算、提成、发票模型 |
| Q-04 | 有没有赊销/账期客户？ | 决定信用额度与账龄模块优先级 |
| Q-05 | 是否已有进销存/ERP/财务系统需要对接？ | 决定是否需要同步任务 |
| Q-06 | 历史订单与客户数据是什么格式？ | 决定导入工具工作量 |
| Q-07 | 是否需要电子合同/签章？ | 大额交易举证 |
| Q-08 | 是否做以旧换新与二手机业务？ | 影响 SKU 是否支持非标品 |

## 怎么改

```bash
# 文档：改 docs/*.md 后重新生成 Word
python3 md2docx.py docs/01-需求规格说明书.md "缝纫机经销商小程序-需求规格说明书.docx"

# 图：改 diagrams/fig*.py 后重跑（需要 Pillow）
python3 diagrams/figs_rest.py && python3 diagrams/figs_arch.py && python3 diagrams/fig2.py

# 接口：改 api/openapi.yaml 后重新生成 HTML / JSON
python3 api/build_docs.py

# 前端自测（Node，桩掉 wx，跑一遍下单→支付→验收→报修）
node miniprogram/tests/smoke.js
```

> Word 文档正文用宋体、标题用黑体，在 macOS 的 Word/Pages 中会分别映射到 Songti SC 与 Heiti SC。
