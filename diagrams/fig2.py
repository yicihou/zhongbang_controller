# -*- coding: utf-8 -*-
"""图 2：客户下单与定金支付流程"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from chartlib import *

c = C(1000, 1210)
cx, W = 380, 460
X = cx - W // 2
RC, RW = 700, 272

c.pill(cx, 24, 350, 54, "客户进入小程序", "start")
c.node(X, 108, W, 78, "浏览机型与配置（机头 / 台板 / 电机 / 配件）", "client", sz=17)
c.node(X, 216, W, 78, "下单：数量、收货地址、联系人、期望安装时间", "client", sz=17)
c.node(X, 324, W, 78, "选择安装师傅：按距离、评分、档期筛选", "client", sz=17)
c.diamond(cx, 456, 340, 108, "库存与师傅档期\n是否可用？")
c.node(X, 552, W, 78, "提交订单并支付定金（默认 30%）", "client", sz=17)
c.diamond(cx, 696, 300, 100, "支付是否成功？")
c.node(X, 790, W, 78, "订单状态：已付定金 / 待安装", "sys", sz=17)
c.node(X, 898, W, 78, "生成安装工单，推送给所选师傅", "sys", sz=17)
c.pill(cx, 1004, 460, 54, "进入安装履约环节（见图 3）", "end")

for a, b in [(78, 108), (186, 216), (294, 324), (402, 403), (509, 552),
             (630, 646), (868, 898), (976, 1004)]:
    c.arrow([(cx, a), (cx, b)])

# 判断 1 分支
c.arrow([(cx + 170, 456), (RC, 456)], label="否", lpos=(600, 436))
c.node(RC, 418, RW, 78, "提示不可用：改期 / 改派 / 候补", "note", sz=16)
c.arrow([(RC, 457), (RC - 40, 457), (RC - 40, 362), (X + W, 362)], dash=True, color=LINE, width=2)

c.arrow([(cx, 746), (cx, 790)], label="是", lpos=(418, 768))
c.arrow([(cx + 150, 696), (RC, 696)], label="否", lpos=(600, 676))
c.node(RC, 664, RW, 64, "30 分钟内未支付", "warn", sz=16)
c.arrow([(RC + RW / 2, 728), (RC + RW / 2, 752)])
c.node(RC, 752, RW, 84, "订单自动关闭\n释放库存与师傅档期", "note", sz=15)

c.legend(150, 1160, [("客户操作", "client"), ("平台系统", "sys"), ("判断", "note")])
c.text(150, 1188, "注：定金比例、尾款触发时点均为可配置项；改期或改派将生成新的工单版本并保留历史记录。",
       sz=13, color=MUT, align="left", maxw=700)

c.save(os.path.join(os.path.dirname(__file__), "fig2-order-flow.png"))
print("ok fig2")
