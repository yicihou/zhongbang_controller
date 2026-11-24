#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""零依赖 Mock 服务：按 openapi.yaml 的示例返回数据，订单/报修有内存状态。

    python3 api/mock_server.py 8080

小程序端把 config.js 的 USE_MOCK 改为 false、baseUrl 指向 http://127.0.0.1:8080，
并在微信开发者工具里勾选「不校验合法域名」即可直连。
"""
import json
import os
import re
import sys
import time
import uuid
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

BASE = os.path.dirname(os.path.abspath(__file__))
LATENCY = float(os.environ.get("MOCK_LATENCY", "0.25"))  # 秒，模拟网络延迟

# ---------------------------------------------------------------- 静态数据
PRODUCTS = {
    101: dict(id=101, name="电脑平缝机 A5", model="A5-5000", coverImage="", price=328000,
              depositRate=30, stock=12, tags=["含上门安装", "现货"],
              subtitle="直驱 · 自动剪线 · 5000 针/分", brand="标准牌", categoryId=2,
              skus=[dict(id=1011, name="标准台板 + 伺服电机", specs={"台板": "标准", "电机": "伺服"},
                         price=328000, stock=8),
                    dict(id=1012, name="加厚台板 + 伺服电机", specs={"台板": "加厚", "电机": "伺服"},
                         price=336000, stock=4),
                    dict(id=1013, name="标准台板 + 直驱电机", specs={"台板": "标准", "电机": "直驱"},
                         price=354000, stock=0)]),
    102: dict(id=102, name="家用多功能机 M3", model="M3", coverImage="", price=158000,
              depositRate=30, stock=20, tags=["含上门教学"],
              subtitle="锁边 · 绣花 · 家用", brand="标准牌", categoryId=1,
              skus=[dict(id=1021, name="标准套装", specs={"套装": "标准"}, price=158000, stock=20)]),
    103: dict(id=103, name="四线包缝机 E4", model="E4-7000", coverImage="", price=690000,
              depositRate=30, stock=3, tags=["现货 3 台"],
              subtitle="超高速 · 低噪", brand="标准牌", categoryId=3,
              skus=[dict(id=1031, name="标准配置", specs={"配置": "标准"}, price=690000, stock=3)]),
    104: dict(id=104, name="电脑绣花机 12 头", model="EM-12", coverImage="", price=6800000,
              depositRate=30, stock=2, tags=["可分期"],
              subtitle="适用帽子 / 成衣", brand="标准牌", categoryId=4,
              skus=[dict(id=1041, name="12 头标准型", specs={"头数": "12"}, price=6800000, stock=2)]),
}

TECHS = [
    dict(id=88, name="王师傅", avatar="", rating=4.9, orderCount=186, distanceKm=2.4,
         skills=["平缝", "包缝", "电控调试"], intro="8 年安装经验，擅长流水线调试", recommended=True,
         freeSlots=[dict(date="2025-11-28", slot="AM", text="11-28 上午"),
                    dict(date="2025-11-28", slot="PM", text="11-28 下午")]),
    dict(id=89, name="李师傅", avatar="", rating=4.7, orderCount=243, distanceKm=5.1,
         skills=["绣花机", "特种机"], intro="擅长绣花机与特种机", recommended=False,
         freeSlots=[dict(date="2025-11-27", slot="AM", text="11-27 上午"),
                    dict(date="2025-11-28", slot="PM", text="11-28 下午")]),
    dict(id=90, name="赵师傅", avatar="", rating=4.6, orderCount=97, distanceKm=8.7,
         skills=["家用机", "安装教学"], intro="擅长家用机安装与操作培训", recommended=False,
         freeSlots=[dict(date="2025-11-27", slot="AM", text="11-27 上午"),
                    dict(date="2025-11-28", slot="AM", text="11-28 上午")]),
]

ADDRESSES = [
    dict(id=55801, name="张建国", mobile="138****8000", province="浙江省", city="杭州市",
         district="余杭区", detail="某某街道 128 号 3 楼", isDefault=True),
    dict(id=55802, name="张建国", mobile="138****8000", province="浙江省", city="杭州市",
         district="萧山区", detail="某某工业园 B 栋", isDefault=False),
]

ASSETS = [
    dict(id=7781, model="电脑平缝机 A5", sn="A5-2024-008671", buyDate="2024-05-20",
         warrantyEnd="2026-05-20", warrantyStatus="IN", warrantyText="保内，至 2026-05-20",
         lastServiceAt="2025-11-23"),
    dict(id=7782, model="四线包缝机 E4", sn="E4-2023-002114", buyDate="2023-03-11",
         warrantyEnd="2024-03-11", warrantyStatus="OUT", warrantyText="已过保", lastServiceAt=""),
]

STATUS_TEXT = {
    "UNPAID": "待付款", "DEPOSIT_PAID": "待安装", "INSTALLING": "安装中",
    "PENDING_BALANCE": "待付尾款", "COMPLETED": "已完成", "CANCELLED": "已取消",
    "REFUNDING": "退款中", "REFUNDED": "已退款",
    "PENDING": "待接单", "ACCEPTED": "已接单", "ON_SITE": "服务中", "DONE": "已完成",
}

ORDERS = {}       # orderId -> order
WORKORDERS = {}   # id -> workorder
_seq = {"order": 0, "work": 0}


def now(fmt="%Y-%m-%d %H:%M:%S"):
    return datetime.now().strftime(fmt)


def ok(data, msg="ok"):
    return dict(code=0, msg=msg, data=data, traceId=uuid.uuid4().hex[:8])


def err(code, msg):
    return dict(code=code, msg=msg, data=None, traceId=uuid.uuid4().hex[:8])


def find_sku(sku_id):
    for p in PRODUCTS.values():
        for s in p["skus"]:
            if s["id"] == sku_id:
                return p, s
    return None, None


def make_timeline(order):
    st = order["status"]
    seq = ["UNPAID", "DEPOSIT_PAID", "INSTALLING", "PENDING_BALANCE", "COMPLETED"]
    idx = seq.index(st) if st in seq else -1
    nodes = []
    texts = {
        "UNPAID": ("提交订单", "等待支付定金"),
        "DEPOSIT_PAID": ("已付定金", "已派单给师傅"),
        "INSTALLING": ("安装中", "师傅已上门施工"),
        "PENDING_BALANCE": ("安装完成待付尾款", "客户已验收，尾款待支付"),
        "COMPLETED": ("订单完成", "已结清并归档"),
    }
    for i, s in enumerate(seq):
        title, desc = texts[s]
        nodes.append(dict(status=s, title=title, desc=desc,
                          time=order["times"].get(s, ""),
                          done=i <= idx, current=(i == idx)))
    return nodes


def order_brief(o):
    return dict(id=o["id"], orderNo=o["orderNo"], status=o["status"],
                statusText=STATUS_TEXT.get(o["status"], o["status"]),
                totalAmount=o["totalAmount"], depositAmount=o["depositAmount"],
                paidAmount=o["paidAmount"],
                balanceAmount=o["balanceAmount"], createdAt=o["createdAt"],
                installDate=o["installDate"], technicianName=o.get("technicianName", ""),
                items=o["items"])


def seed():
    _seq["order"] = 2
    for i, st in enumerate(["PENDING_BALANCE", "INSTALLING"]):
        oid = 910233001 + i
        tech = TECHS[0] if i == 0 else TECHS[1]
        prod = PRODUCTS[101] if i == 0 else PRODUCTS[103]
        sku = prod["skus"][0]
        deposit = sku["price"] * 30 // 100
        ORDERS[oid] = dict(
            id=oid, orderNo="SO2025112300012%d" % i, status=st,
            totalAmount=sku["price"], depositAmount=deposit, paidAmount=deposit,
            balanceAmount=sku["price"] - deposit,
            createdAt="2025-11-23 20:14:0%d" % i, installDate="2025-11-2%d" % (8 - i),
            technician=dict(id=tech["id"], name=tech["name"], mobile="138****8000", rating=tech["rating"]),
            technicianName=tech["name"], installSlot="AM", remark="厂房在 3 楼，有货梯",
            items=[dict(skuId=sku["id"], name=prod["name"], skuName=sku["name"],
                        coverImage="", price=sku["price"], quantity=1)],
            times={"UNPAID": "2025-11-23 20:14:0%d" % i, "DEPOSIT_PAID": "2025-11-23 20:16:22"},
            address=ADDRESSES[0],
            payments=[dict(id=501, type="DEPOSIT", typeText="定金", amount=deposit,
                           status="SUCCESS", paidAt="2025-11-23 20:16:22")],
            expireAt="")

    _seq["work"] = 12
    WORKORDERS[660012] = dict(
        id=660012, workNo="WO20251123000045", type="REPAIR", status="ACCEPTED",
        faultType="THREAD_BREAK", faultDesc="高速运转时断线，低速正常，已换过机针",
        inWarranty=True, warrantyDesc="整机保修至 2026-05-20，本次故障在保修范围内",
        quoteAmount=0, actualAmount=0, appointmentAt="2025-11-25 09:30:00",
        createdAt="2025-11-23 20:14:00", customerName="张建国",
        address="余杭区某某街道 128 号 3 楼", distanceKm=2.4,
        technician=dict(id=89, name="李师傅", mobile="138****8000", rating=4.7),
        images=[],
        timeline=[dict(status="PENDING", title="提交报修", desc="已判定保内，等待派单",
                       time="2025-11-23 20:14:00", done=True, current=False),
                  dict(status="ACCEPTED", title="师傅已接单", desc="约定 11-25 上午上门",
                       time="2025-11-24 09:02:00", done=True, current=True),
                  dict(status="DONE", title="维修完成", desc="", time="", done=False, current=False)])


# ---------------------------------------------------------------- 路由
ROUTES = []


def route(method, pattern):
    def deco(fn):
        ROUTES.append((method, re.compile("^" + pattern + "$"), fn))
        return fn
    return deco


@route("POST", r"/api/auth/login")
def _(h, m, body, q):
    return ok(dict(token="mock-jwt-" + uuid.uuid4().hex[:12], expiresIn=2592000,
                   hasMobile=False,
                   customer=dict(id=1001, nickname="", avatar="", mobile="", level=1,
                                 levelText="普通客户")))


@route("POST", r"/api/auth/phone")
def _(h, m, body, q):
    return ok(dict(token="mock-jwt-" + uuid.uuid4().hex[:12], expiresIn=2592000,
                   hasMobile=True,
                   customer=dict(id=1001, nickname="张建国", avatar="", mobile="138****8000",
                                 level=3, levelText="金卡客户")))


@route("GET", r"/api/home")
def _(h, m, body, q):
    return ok(dict(
        banners=[dict(id=1, image="", title="新机到家，师傅上门",
                      subtitle="下单可选安装师傅 · 验收后再付尾款",
                      linkType="PRODUCT_LIST", linkValue="0")],
        quickEntries=[dict(code="BUY", title="买机器", icon="机", badge=""),
                      dict(code="REPAIR", title="一键报修", icon="修", badge=""),
                      dict(code="ASSET", title="我的设备", icon="档", badge="3"),
                      dict(code="INVOICE", title="发票/对账", icon="票", badge="")],
        categories=[dict(id=c, name=n, count=k) for c, n, k in
                    [(1, "家用多功能", 12), (2, "工业平缝", 9), (3, "包缝绷缝", 6), (4, "特种机", 5)]],
        hotProducts=[dict(id=p["id"], name=p["name"], model=p["model"],
                          coverImage=p["coverImage"], price=p["price"],
                          depositAmount=p["price"] * p["depositRate"] // 100,
                          stock=p["stock"], tags=p["tags"]) for p in list(PRODUCTS.values())[:4]]))


@route("GET", r"/api/categories")
def _(h, m, body, q):
    return ok([dict(id=1, name="家用多功能", count=12), dict(id=2, name="工业平缝", count=9),
               dict(id=3, name="包缝绷缝", count=6), dict(id=4, name="特种机", count=5)])


@route("GET", r"/api/products")
def _(h, m, body, q):
    cat = (q.get("categoryId") or [""])[0]
    kw = (q.get("keyword") or [""])[0]
    items = list(PRODUCTS.values())
    if cat:
        items = [p for p in items if str(p["categoryId"]) == cat]
    if kw:
        items = [p for p in items if kw in p["name"] or kw in p["model"]]
    lst = [dict(id=p["id"], name=p["name"], model=p["model"], coverImage=p["coverImage"],
                price=p["price"], depositAmount=p["price"] * p["depositRate"] // 100,
                stock=p["stock"], tags=p["tags"]) for p in items]
    return ok(dict(total=len(lst), page=1, size=20, list=lst))


@route("GET", r"/api/products/(\d+)")
def _(h, m, body, q, pid=None):
    p = PRODUCTS.get(int(pid))
    if not p:
        return err(1001, "机型不存在")
    data = {k: v for k, v in p.items() if k != "skus"}
    data["depositAmount"] = p["price"] * p["depositRate"] // 100
    data["images"] = []
    data["videoUrl"] = ""
    data["warranty"] = dict(months=12, motorMonths=24,
                            desc="整机 12 个月，电机电控 24 个月，以安装完成日起算")
    data["services"] = ["含上门安装调试", "含首次操作培训", "可开增值税专用发票"]
    data["specs"] = [dict(name="最高转速", value="5000 针/分"),
                     dict(name="适用场景", value="服装厂流水线 / 加工坊"),
                     dict(name="电压", value="220V"), dict(name="整机重量", value="32 kg")]
    data["skus"] = [dict(id=s["id"], name=s["name"], specs=s["specs"], price=s["price"],
                         depositAmount=s["price"] * p["depositRate"] // 100, stock=s["stock"])
                    for s in p["skus"]]
    return ok(data)


@route("GET", r"/api/technicians/available")
def _(h, m, body, q):
    return ok(TECHS)


@route("POST", r"/api/orders")
def _(h, m, body, q):
    rid = body.get("requestId", "")
    if rid:
        for o in ORDERS.values():
            if o.get("requestId") == rid:
                return ok(dict(orderId=o["id"], orderNo=o["orderNo"],
                               totalAmount=o["totalAmount"], depositAmount=o["depositAmount"],
                               balanceAmount=o["balanceAmount"], status=o["status"],
                               expireAt=o["expireAt"]))
    prod, sku = find_sku(body.get("skuId"))
    if not sku:
        return err(1001, "SKU 不存在")
    qty = int(body.get("quantity") or 1)
    total = sku["price"] * qty
    deposit = total * 30 // 100
    tech = next((t for t in TECHS if t["id"] == body.get("technicianId")), None)
    if body.get("technicianId") and not tech:
        return err(3002, "所选师傅档期已满，请重新选择")
    _seq["order"] += 1
    oid = 910233000 + _seq["order"]
    addr = next((a for a in ADDRESSES if a["id"] == body.get("addressId")), ADDRESSES[0])
    o = dict(id=oid, orderNo="SO%s%04d" % (now("%Y%m%d"), _seq["order"]),
             requestId=rid, status="UNPAID", totalAmount=total, depositAmount=deposit,
             paidAmount=0, balanceAmount=total, createdAt=now(),
             installDate=body.get("installDate", ""), installSlot=body.get("installSlot", "AM"),
             technician=dict(id=tech["id"], name=tech["name"], mobile="138****8000",
                             rating=tech["rating"]) if tech else None,
             technicianName=tech["name"] if tech else "",
             remark=body.get("remark", ""),
             items=[dict(skuId=sku["id"], name=prod["name"], skuName=sku["name"],
                         coverImage="", price=sku["price"], quantity=qty)],
             times={"UNPAID": now()}, address=addr, payments=[],
             invoice=body.get("invoice") or dict(need=False),
             expireAt=(datetime.now() + timedelta(minutes=30)).strftime("%Y-%m-%d %H:%M:%S"))
    ORDERS[oid] = o
    return ok(dict(orderId=oid, orderNo=o["orderNo"], totalAmount=total,
                   depositAmount=deposit, balanceAmount=total - deposit,
                   status="UNPAID", expireAt=o["expireAt"]))


@route("GET", r"/api/orders")
def _(h, m, body, q):
    st = (q.get("status") or ["ALL"])[0]
    items = list(ORDERS.values())
    if st and st != "ALL":
        items = [o for o in items if o["status"] == st]
    items.sort(key=lambda o: o["id"], reverse=True)
    return ok(dict(total=len(items), page=1, size=20, list=[order_brief(o) for o in items]))


@route("GET", r"/api/orders/(\d+)")
def _(h, m, body, q, oid=None):
    o = ORDERS.get(int(oid))
    if not o:
        return err(1001, "订单不存在")
    d = order_brief(o)
    d.update(installSlot=o.get("installSlot", "AM"), remark=o.get("remark", ""),
             expireAt=o.get("expireAt", ""), technician=o.get("technician"),
             address=o.get("address"), invoice=o.get("invoice", {}),
             payments=o.get("payments", []), timeline=make_timeline(o))
    acts = []
    if o["status"] == "UNPAID":
        acts = ["PAY_DEPOSIT", "CANCEL"]
    elif o["status"] == "PENDING_BALANCE":
        acts = ["PAY_BALANCE", "CALL_TECHNICIAN", "REPAIR"]
    elif o["status"] == "DEPOSIT_PAID":
        acts = ["CALL_TECHNICIAN"]
    elif o["status"] == "COMPLETED":
        acts = ["EVALUATE", "REPAIR"]
    d["actions"] = acts
    return ok(d)


@route("POST", r"/api/orders/(\d+)/cancel")
def _(h, m, body, q, oid=None):
    o = ORDERS.get(int(oid))
    if not o:
        return err(1001, "订单不存在")
    if o["status"] != "UNPAID":
        return err(3003, "当前状态不允许取消")
    o["status"] = "CANCELLED"
    o["times"]["CANCELLED"] = now()
    return ok(True)


@route("POST", r"/api/orders/(\d+)/accept")
def _(h, m, body, q, oid=None):
    o = ORDERS.get(int(oid))
    if not o:
        return err(1001, "订单不存在")
    if body.get("pass") is False:
        return ok(dict(status="INSTALLING", balanceAmount=0))
    o["status"] = "PENDING_BALANCE"
    o["times"]["PENDING_BALANCE"] = now()
    return ok(dict(status="PENDING_BALANCE", balanceAmount=o["balanceAmount"]))


@route("POST", r"/api/pay/prepay")
def _(h, m, body, q):
    o = ORDERS.get(body.get("orderId"))
    if not o:
        return err(1001, "订单不存在")
    typ = body.get("type", "DEPOSIT")
    amt = o["depositAmount"] if typ == "DEPOSIT" else o["balanceAmount"]
    return ok(dict(paymentId=501, outTradeNo=o["orderNo"] + ("-D" if typ == "DEPOSIT" else "-B"),
                   amount=amt,
                   payParams=dict(timeStamp=str(int(time.time())), nonceStr=uuid.uuid4().hex[:16],
                                  package="prepay_id=mock" + uuid.uuid4().hex[:12], signType="RSA",
                                  paySign="mock-sign")))


@route("POST", r"/api/pay/mock-success")
def _(h, m, body, q):
    """Mock 专用：模拟支付成功回调，推进订单状态。生产不存在此接口。"""
    o = ORDERS.get(body.get("orderId"))
    if not o:
        return err(1001, "订单不存在")
    typ = body.get("type", "DEPOSIT")
    if typ == "DEPOSIT":
        o["status"] = "DEPOSIT_PAID"
        o["paidAmount"] = o["depositAmount"]
        o["balanceAmount"] = o["totalAmount"] - o["paidAmount"]
        o["times"]["DEPOSIT_PAID"] = now()
        o["payments"].append(dict(id=501, type="DEPOSIT", typeText="定金",
                                  amount=o["depositAmount"], status="SUCCESS", paidAt=now()))
    else:
        amt = o["balanceAmount"]
        o["paidAmount"] = o["totalAmount"]
        o["balanceAmount"] = 0
        o["status"] = "COMPLETED"
        o["times"]["COMPLETED"] = now()
        o["payments"].append(dict(id=502, type="BALANCE", typeText="尾款",
                                  amount=amt, status="SUCCESS", paidAt=now()))
    return ok(dict(status=o["status"]))


@route("GET", r"/api/customer/profile")
def _(h, m, body, q):
    return ok(dict(customer=dict(id=1001, nickname="张建国", avatar="", mobile="138****8000",
                                 level=3, levelText="金卡客户"),
                   orderCount=len(ORDERS), totalPaid=4260000, pendingAmount=229600,
                   assetCount=len(ASSETS), couponCount=2))


@route("GET", r"/api/customer/addresses")
def _(h, m, body, q):
    return ok(ADDRESSES)


@route("POST", r"/api/customer/addresses")
def _(h, m, body, q):
    nid = max(a["id"] for a in ADDRESSES) + 1
    body = dict(body, id=nid)
    ADDRESSES.append(body)
    return ok(dict(id=nid))


@route("GET", r"/api/assets")
def _(h, m, body, q):
    return ok(ASSETS)


@route("POST", r"/api/assets/bind")
def _(h, m, body, q):
    sn = body.get("sn", "")
    for a in ASSETS:
        if a["sn"] == sn:
            return ok(a)
    return err(1001, "未查询到该机身码，请联系经销商")


@route("POST", r"/api/repairs")
def _(h, m, body, q):
    _seq["work"] += 1
    wid = 660000 + _seq["work"]
    asset = next((a for a in ASSETS if a["id"] == body.get("assetId")), ASSETS[0])
    fault_map = dict(THREAD_BREAK="断线 / 跳针", NOISE="噪音异响", OIL="不上油",
                     ELECTRIC="电控故障", FEED="送布异常", OTHER="其他")
    w = dict(id=wid, workNo="WO%s%04d" % (now("%Y%m%d"), _seq["work"]), type="REPAIR",
             status="PENDING", faultType=body.get("faultType", "OTHER"),
             faultDesc=body.get("faultDesc", ""),
             inWarranty=asset["warrantyStatus"] == "IN",
             warrantyDesc=("整机保修至 %s，本次故障在保修范围内" % asset["warrantyEnd"])
             if asset["warrantyStatus"] == "IN" else "已过保，需先检测报价",
             quoteAmount=0, actualAmount=0, appointmentAt="", createdAt=now(),
             customerName="张建国", address="余杭区某某街道 128 号 3 楼", distanceKm=2.4,
             technician=None, images=body.get("images", []),
             timeline=[dict(status="PENDING", title="提交报修", desc="等待派单",
                            time=now(), done=True, current=True),
                       dict(status="ACCEPTED", title="师傅接单", desc="", time="",
                            done=False, current=False),
                       dict(status="DONE", title="维修完成", desc="", time="",
                            done=False, current=False)])
    WORKORDERS[wid] = w
    return ok(dict(workOrderId=wid, workNo=w["workNo"], inWarranty=w["inWarranty"],
                   warrantyDesc=w["warrantyDesc"], status="PENDING", statusText="待派单"))


@route("GET", r"/api/repairs/(\d+)")
def _(h, m, body, q, wid=None):
    w = WORKORDERS.get(int(wid))
    if not w:
        return err(1001, "工单不存在")
    d = dict(w)
    d["typeText"] = "维修"
    d["statusText"] = STATUS_TEXT.get(w["status"], w["status"])
    d["faultText"] = dict(THREAD_BREAK="断线 / 跳针", NOISE="噪音异响", OIL="不上油",
                          ELECTRIC="电控故障", FEED="送布异常", OTHER="其他").get(w["faultType"], "其他")
    return ok(d)


@route("POST", r"/api/repairs/(\d+)/quote/confirm")
def _(h, m, body, q, wid=None):
    w = WORKORDERS.get(int(wid))
    if not w:
        return err(1001, "工单不存在")
    w["status"] = "ACCEPTED" if body.get("accept") else "CLOSED"
    return ok(dict(status=w["status"]))


@route("POST", r"/api/evaluations")
def _(h, m, body, q):
    return ok(True)


@route("GET", r"/api/upload/policy")
def _(h, m, body, q):
    return ok(dict(host="http://127.0.0.1:8080", dir="mock/",
                   expireAt=int(time.time()) + 600, policy="mock-policy",
                   signature="mock-signature", accessKeyId="mock-key"))


@route("GET", r"/api/tech/workorders")
def _(h, m, body, q):
    st = (q.get("status") or [""])[0]
    items = [w for w in WORKORDERS.values() if not st or w["status"] == st]
    lst = []
    for w in items:
        lst.append(dict(id=w["id"], workNo=w["workNo"], type=w["type"], typeText="维修",
                        status=w["status"], statusText=STATUS_TEXT.get(w["status"], ""),
                        faultText=dict(THREAD_BREAK="断线 / 跳针", NOISE="噪音异响",
                                       OIL="不上油", ELECTRIC="电控故障",
                                       FEED="送布异常", OTHER="其他").get(w["faultType"], ""),
                        inWarranty=w["inWarranty"], appointmentAt=w["appointmentAt"],
                        customerName=w["customerName"], address=w["address"],
                        distanceKm=w["distanceKm"]))
    return ok(dict(total=len(lst), page=1, size=20, list=lst))


@route("POST", r"/api/tech/workorders/(\d+)/accept")
def _(h, m, body, q, wid=None):
    w = WORKORDERS.get(int(wid))
    if not w:
        return err(1001, "工单不存在")
    w["status"] = "ACCEPTED"
    return ok(dict(status="ACCEPTED"))


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type,Authorization")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")

    def _send(self, obj, code=200):
        raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self._cors()
        self.end_headers()
        self.wfile.write(raw)

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _handle(self, method):
        u = urlparse(self.path)
        path, q = u.path, parse_qs(u.query)
        length = int(self.headers.get("Content-Length") or 0)
        body = {}
        if length:
            try:
                body = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
            except Exception:
                return self._send(err(1001, "请求体不是合法 JSON"), 400)
        for m, rx, fn in ROUTES:
            if m != method:
                continue
            hit = rx.match(path)
            if hit:
                time.sleep(LATENCY)
                try:
                    res = fn(self, method, body, q, *hit.groups())
                except Exception as e:  # pragma: no cover
                    import traceback
                    traceback.print_exc()
                    return self._send(err(5000, "Mock 内部错误: %s" % e), 500)
                print("  %-6s %-38s -> %s" % (method, path, res.get("code")))
                return self._send(res)
        print("  %-6s %-38s -> 404" % (method, path))
        self._send(err(404, "Mock 未实现该接口: %s %s" % (method, path)), 404)

    def do_GET(self):
        self._handle("GET")

    def do_POST(self):
        self._handle("POST")

    def do_PUT(self):
        self._handle("PUT")

    def do_DELETE(self):
        self._handle("DELETE")

    def log_message(self, *a):
        pass


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    seed()
    srv = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print("Mock API 已启动: http://127.0.0.1:%d" % port)
    print("接口示例: curl http://127.0.0.1:%d/api/home" % port)
    print("延迟 %.2fs，Ctrl+C 退出" % LATENCY)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")


if __name__ == "__main__":
    main()
