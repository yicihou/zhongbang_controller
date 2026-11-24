# -*- coding: utf-8 -*-
"""图 1 / 3 / 4 / 5：功能地图、安装履约、订单状态机、报修流程"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from chartlib import *

OUT = os.path.dirname(__file__)

# ---------------------------------------------------------------- 图 1
c = C(1040, 1250)
cards = [
    ("01", "客户 · 选购与下单", [
        "机型 / 配置浏览与图文详情",
        "价格、定金比例、库存实时展示",
        "下单：地址、期望安装时间、备注",
        "指定安装师傅（评分 / 距离 / 档期）",
        "订单进度跟踪与尾款支付",
        "发票信息与电子发票申请"]),
    ("02", "客户 · 售后与报修", [
        "我的设备：一机一码电子档案",
        "一键报修 + 拍照 / 短视频",
        "保内保外自动判定",
        "自选或系统推荐维修师傅",
        "维修报价确认与进度查看",
        "服务评价与再次预约"]),
    ("03", "师傅 · 服务工单", [
        "接单 / 改约 / 拒单（转派）",
        "上门导航、签到打卡",
        "安装 / 维修过程记录",
        "配件领用与旧件登记",
        "客户电子签字确认",
        "工单收入与结算明细"]),
    ("04", "商家 · 客户与订单", [
        "客户 360 档案与标签",
        "订单全流程看板",
        "已付 / 待付 / 逾期账款",
        "客户偏好与跟进记录",
        "设备与保修到期提醒",
        "报表导出（Excel / 打印）"]),
    ("05", "商家 · 商品与库存", [
        "机型 / 配置 / 阶梯价",
        "库存与门店间调拨",
        "促销、报价单与合同",
        "配件与耗材管理",
        "上下架与展示排序",
        "库存流水与盘点"]),
    ("06", "商家 · 运营与调度", [
        "师傅档案、技能与排班",
        "派单 / 抢单 / 改派策略",
        "保修规则与费用模板",
        "评价、绩效与提成",
        "消息模板与订阅通知",
        "账号权限与操作审计"]),
]
CW, CH = 484, 360
for i, (no, title, items) in enumerate(cards):
    x = 26 + (i % 2) * 510
    y = 26 + (i // 2) * 386
    c.rrect(x, y, CW, CH, 12, fill=WHITE, outline=LINE, width=2)
    c.d.rounded_rectangle([x * S, y * S, (x + CW) * S, (y + 56) * S], radius=12 * S,
                          fill=RED, corners=(True, True, False, False))
    c.text(x + 22, y + 28, no, sz=16, bold=True, color=(255, 205, 212), align="left")
    c.text(x + 58, y + 28, title, sz=19, bold=True, color=WHITE, align="left", maxw=CW - 80)
    for j, it in enumerate(items):
        by = y + 90 + j * 42
        c.d.ellipse([(x + 26) * S, (by - 3.5) * S, (x + 33) * S, (by + 3.5) * S], fill=RED)
        c.text(x + 44, by, it, sz=16, color=INK, align="left", maxw=CW - 68)

c.rrect(26, 1176, 988, 52, 10, fill=TINT, outline=EDGE, width=2)
c.text(44, 1202, "共通能力：微信登录与支付 · 订阅消息 · 图片 / 视频存储 · 电子发票 · 权限与审计 · 操作日志",
       sz=15, color=RED_D, align="left", maxw=950)
c.save(os.path.join(OUT, "fig1-functional-map.png"))

# ---------------------------------------------------------------- 图 3
c = C(1000, 1130)
cx, W = 380, 460
X = cx - W // 2
RC, RW, RM = 700, 262, 990
c.pill(cx, 24, 380, 54, "安装工单已派给所选师傅", "start")
c.node(X, 104, W, 78, "师傅接单：确认档期 / 改约 / 拒单", "staff", sz=17)
c.diamond(cx, 248, 330, 104, "能否按期上门？")
c.node(X, 340, W, 78, "上门安装：拆箱、组装、调平、通电试缝", "staff", sz=17)
c.node(X, 446, W, 78, "上传安装单：机身码、现场照片、客户签字", "staff", sz=17)
c.node(X, 552, W, 78, "客户在小程序确认验收", "client", sz=17)
c.diamond(cx, 696, 320, 100, "验收是否通过？")
c.node(X, 788, W, 78, "支付尾款（订单总额 − 已付定金）", "client", sz=17)
c.node(X, 894, W, 78, "结算师傅费用、开票、归档设备档案", "sys", sz=17)
c.pill(cx, 1000, 400, 54, "订单完成，客户评价", "end")

for a, b in [(78, 104), (182, 196), (300, 340), (418, 446), (524, 552),
             (630, 646), (746, 788), (866, 894), (972, 1000)]:
    c.arrow([(cx, a), (cx, b)])

c.arrow([(cx + 165, 248), (RC, 248)], label="否", lpos=(600, 228))
c.node(RC, 210, RW, 78, "转派其他师傅或协商改期\n（返回重选）", "note", sz=14)
c.arrow([(RC + RW, 249), (RM, 249), (RM, 143), (X + W, 143)], dash=True, color=LINE, width=2)

c.arrow([(cx + 160, 696), (RC, 696)], label="否", lpos=(600, 676))
c.node(RC, 658, RW, 78, "返工 / 换机，商家介入\n后重新验收", "warn", sz=14)
c.arrow([(RC + RW, 700), (RM, 700), (RM, 379), (X + W, 379)], dash=True, color=RED, width=1.8)

c.arrow([(cx, 746), (cx, 788)], label="是", lpos=(416, 767))
c.legend(150, 1085, [("客户操作", "client"), ("师傅操作", "staff"), ("平台系统", "sys")])
c.save(os.path.join(OUT, "fig3-install-flow.png"))

# ---------------------------------------------------------------- 图 4
c = C(1250, 560)
states = ["待付款", "已付定金\n待安装", "安装中", "待付尾款", "已完成"]
labs = ["支付定金", "派单 / 接单", "安装完成", "支付尾款"]
xs = [30, 280, 530, 780, 1030]
for i, s in enumerate(states):
    c.node(xs[i], 60, 190, 78, s, "start" if i == 0 else ("end" if i == 4 else "sys"),
           sz=17, r=10)
for i in range(4):
    c.arrow([(xs[i] + 190, 99), (xs[i + 1], 99)])
    c.text(xs[i] + 220, 74, labs[i], sz=13, color=MUT, align="center", maxw=60)

c.node(30, 300, 190, 78, "已取消", "note", sz=17, r=10)
c.node(280, 300, 190, 78, "退款中 / 已退款", "note", sz=16, r=10)
c.node(530, 300, 190, 78, "售后中（报修）", "note", sz=16, r=10)
c.arrow([(125, 138), (125, 300)], dash=True, color=RED, width=1.8, label="超时/主动", lpos=(199, 215))
c.arrow([(375, 138), (375, 300)], dash=True, color=RED, width=1.8, label="申请退款", lpos=(449, 215))
c.arrow([(625, 138), (625, 300)], dash=True, color=RED, width=1.8, label="发起报修", lpos=(699, 215))

for i, t in enumerate([
        "· 定金默认 30%（可配置）；支付定金后订单不可直接取消，须商家审批退款。",
        "· 尾款在安装验收后生成，第 7 天自动催付，逾期 15 天进入人工催收并限制再次下单。",
        "· 状态流转统一由状态机驱动（见架构文档 §4.3），禁止在业务代码中直接修改状态字段。"]):
    c.text(30, 432 + i * 32, t, sz=15, color=MUT, align="left", maxw=1180)
c.save(os.path.join(OUT, "fig4-order-state.png"))

# ---------------------------------------------------------------- 图 5
c = C(1000, 1330)
cx, W = 380, 460
X = cx - W // 2
RC, RW, RM = 700, 262, 990
c.pill(cx, 24, 400, 54, "客户发起报修（小程序 / 机身码扫码）", "start")
c.node(X, 106, W, 78, "选择设备 + 故障描述 + 拍照 / 短视频", "client", sz=17)
c.node(X, 212, W, 78, "系统校验保修状态：购机日期、保修范围", "sys", sz=17)
c.diamond(cx, 348, 320, 100, "是否在保修期内？")
c.node(X, 440, W, 78, "生成服务工单：保内免费 / 保外需报价", "sys", sz=17)
c.node(X, 546, W, 78, "推荐维修师傅（技能 / 距离 / 评分）或客户自选", "client", sz=16)
c.node(X, 652, W, 78, "师傅接单 → 电话预约上门时间", "staff", sz=17)
c.node(X, 758, W, 78, "上门检测 → 维修方案 + 报价单", "staff", sz=17)
c.diamond(cx, 900, 320, 100, "客户是否确认报价？")
c.node(X, 992, W, 78, "维修施工：换件登记（新件 / 旧件）、试机", "staff", sz=16)
c.node(X, 1098, W, 78, "客户确认维修结果并评价", "client", sz=17)
c.pill(cx, 1204, 470, 54, "归档：写入设备服务档案，同步保修", "end")

for a, b in [(78, 106), (184, 212), (290, 298), (398, 440), (518, 546),
             (624, 652), (730, 758), (836, 850), (950, 992), (1070, 1098), (1176, 1204)]:
    c.arrow([(cx, a), (cx, b)])

c.arrow([(cx + 160, 348), (RC, 348)], label="否 · 保外", lpos=(600, 328))
c.node(RC, 300, RW, 96, "预付检测费 / 上门费\n客户同意后再派单", "warn", sz=15)
c.arrow([(RC + RW, 348), (RM, 348), (RM, 466), (X + W, 466)], dash=True, color=LINE, width=2)

c.arrow([(cx, 950), (cx, 992)], label="是", lpos=(416, 971))
c.arrow([(cx + 160, 900), (RC, 900)], label="否", lpos=(600, 880))
c.node(RC, 862, RW, 78, "放弃维修，工单关闭\n（可收检测费）", "note", sz=15)

c.legend(150, 1284, [("客户操作", "client"), ("师傅操作", "staff"), ("平台系统", "sys")])
c.save(os.path.join(OUT, "fig5-repair-flow.png"))
print("ok fig1/3/4/5")
