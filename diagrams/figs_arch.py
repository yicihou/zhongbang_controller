# -*- coding: utf-8 -*-
"""图 6 系统架构 / 图 7 核心数据关系"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from chartlib import *

OUT = os.path.dirname(__file__)

# ---------------------------------------------------------------- 图 6 架构
c = C(1240, 780)
BAND = (250, 250, 251)


def band(y, h, label):
    c.rrect(20, y, 1200, h, 12, fill=BAND, outline=(226, 230, 236), width=1.6)
    c.rrect(20, y, 5, h, 2.5, fill=RED, outline=RED, width=1)
    c.text(36, y + h / 2, label, sz=17, bold=True, color=RED_D, align="left", maxw=132)


def row(y, h, items, w, gap, sz=15, fill=WHITE):
    for i, t in enumerate(items):
        x = 180 + i * (w + gap)
        c.rrect(x, y, w, h, 9, fill=fill, outline=(206, 212, 220), width=1.6)
        c.text(x + w / 2, y + h / 2, t, sz=sz, color=INK, maxw=w - 18, lh=1.35)


band(24, 104, "用户端")
row(46, 60, ["客户小程序\n微信 · 下单 / 报修", "商家管理后台\nWeb · 订单 / 客户",
             "师傅端\n小程序 · 接单 / 打卡", "客服 / 运营工作台\nWeb"], 246, 16, sz=14)

band(142, 86, "接入层")
row(159, 52, ["Nginx 反向代理\nHTTPS · 静态资源", "Spring Security + JWT\n登录态 / 接口鉴权",
              "接口限流 · 幂等 · 防重放", "微信支付回调验签"], 246, 16, sz=13.5)

band(242, 156, "应用层")
row(258, 58, ["认证与权限", "商品与库存", "订单与履约", "支付与对账", "售后与工单"], 192, 15)
row(326, 58, ["派单调度", "客户 360 档案", "消息通知", "报表与看板", "系统配置"], 192, 15)

band(412, 104, "支撑层")
row(430, 52, ["Redis\n缓存 · 分布式锁", "RabbitMQ\n消息 · 延迟队列", "XXL-Job\n定时任务",
              "对象存储 OSS\n图片 / 视频", "短信 · 地图\n电子发票"], 192, 15, sz=13.5)

band(530, 96, "数据层")
row(548, 52, ["MySQL 8\n主从读写分离", "Redis 持久化", "OSS / MinIO\n文件存储", "日志与审计库"], 246, 16, sz=13.5)

band(640, 76, "运维层")
row(658, 40, ["Docker Compose", "Prometheus + Grafana", "每日备份 / 恢复演练", "Loki 日志采集"], 246, 16, sz=13.5)

c.text(20, 748, "调用方向：用户端 → 接入层 → 应用层 → 支撑层 / 数据层；所有外部回调统一经接入层验签后进入应用层。",
       sz=13, color=MUT, align="left", maxw=1180)
c.save(os.path.join(OUT, "fig6-architecture.png"))

# ---------------------------------------------------------------- 图 7 ER
c = C(1150, 880)
W, H = 310, 236
CONN = (150, 156, 166)


def table(x, y, cn, en, fields):
    c.rrect(x + 3, y + 4, W, H, 10, fill=(238, 239, 242), outline=(238, 239, 242))
    c.rrect(x, y, W, H, 10, fill=WHITE, outline=RED, width=1.8)
    c.d.rounded_rectangle([x * S, y * S, (x + W) * S, (y + 40) * S], radius=10 * S,
                          fill=TINT, corners=(True, True, False, False))
    c.d.line([(x + 1) * S, (y + 40) * S, (x + W - 1) * S, (y + 40) * S], fill=EDGE, width=int(1.4 * S))
    c.text(x + 14, y + 20, cn, sz=16, bold=True, color=RED_D, align="left", maxw=190)
    # en 需要右对齐：text() 只有左/居中，这里单独绘制
    c.d.text(((x + W - 12) * S, (y + 21) * S), en, font=F(11.5), fill=MUT, anchor="rm")
    for i, f in enumerate(fields):
        cx = x + 16
        cy = y + 62 + i * 29
        if i == 0:
            c.d.ellipse([(cx - 1) * S, (cy - 4) * S, (cx + 7) * S, (cy + 4) * S], fill=RED)
            c.text(cx + 20, cy, f, sz=13.5, color=RED_D, align="left")
        else:
            c.text(cx + 12, cy, f, sz=13.5, color=INK, align="left", maxw=W - 34)


def rel(pts, label, lx, ly):
    c.arrow(pts, color=CONN, width=1.6, head=False)
    f = F(12)
    tw = f.getlength(label) / S
    c.d.rectangle([(lx - tw / 2 - 4) * S, (ly - 9) * S, (lx + tw / 2 + 4) * S, (ly + 9) * S], fill=WHITE)
    c.d.text((lx * S, ly * S), label, font=f, fill=MUT, anchor="mm")


# 关系线（先画线，再盖表格）
rel([(340, 148), (420, 148)], "1 : N", 380, 128)
rel([(730, 148), (810, 148)], "1 : N", 770, 128)
rel([(185, 320), (185, 293), (480, 293), (480, 266)], "1 : N", 330, 276)
rel([(185, 556), (185, 610)], "1 : N", 205, 583)
rel([(620, 266), (620, 320)], "1 : N", 640, 293)
rel([(730, 438), (810, 438)], "N : 1", 770, 418)
rel([(480, 556), (480, 610)], "1 : 1", 500, 583)
rel([(700, 556), (700, 580), (965, 580), (965, 610)], "1 : N", 880, 566)

table(30, 30, "商品配置", "product_sku", [
    "id", "product_id  机型", "model  型号", "config  配置（台板/电机）", "price  售价", "stock  库存"])
table(420, 30, "订单", "orders", [
    "id", "order_no  订单号", "customer_id", "sku_id", "amount / deposit / balance", "status  状态"])
table(810, 30, "收款流水", "payment", [
    "id", "order_id", "type  定金/尾款/维修费", "amount  金额", "pay_no  微信支付单号", "paid_at"])
table(30, 320, "客户", "customer", [
    "id", "openid / unionid", "mobile  手机号", "name / address", "tags  偏好标签", "total_paid  累计成交"])
table(420, 320, "服务工单", "work_order", [
    "id", "order_id / asset_id", "type  安装/维修", "technician_id", "status  状态", "quote / actual_fee"])
table(810, 320, "师傅", "technician", [
    "id", "name / mobile", "skills  技能标签", "region  服务区域", "rating  评分 / 接单量", "settle_account"])
table(30, 610, "设备档案", "customer_asset", [
    "id", "customer_id", "model / sn  机身码", "buy_date  购机日期", "warranty_end  保修到期"])
table(420, 610, "评价", "evaluation", [
    "id", "work_order_id", "score  评分 / 标签", "content  内容 / 图片"])
table(810, 610, "工单轨迹", "work_order_log", [
    "id", "work_order_id", "action  动作 / 操作人", "created_at  时间 / 备注"])

c.save(os.path.join(OUT, "fig7-er.png"))
print("ok fig6/fig7")
