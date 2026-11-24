# -*- coding: utf-8 -*-
"""读 openapi.yaml，生成可读的接口文档（单文件 HTML）与接口清单（Markdown）。"""
import os
import re
import json
import html
import yaml

BASE = os.path.dirname(os.path.abspath(__file__))
SPEC = os.path.join(BASE, "openapi.yaml")

ERRORS = [
    ("0", "成功", "—"),
    ("1001", "参数错误", "提示具体字段，不弹通用错误"),
    ("2001", "未登录 / token 过期", "静默重新 wx.login，再重放原请求"),
    ("2003", "无权限", "提示后返回上一页"),
    ("3001", "库存不足", "引导改选机型或「到货通知」"),
    ("3002", "师傅档期冲突", "展示可选日期与备选师傅"),
    ("3003", "订单状态不允许该操作", "刷新订单详情"),
    ("3004", "金额校验失败", "刷新订单金额后重试"),
    ("3005", "打卡距离超限", "提示「请在客户地址 500 米内打卡」"),
    ("4001", "重复请求（幂等命中）", "直接使用返回结果，不重复提示"),
    ("5001", "支付下单失败", "重试 + 客服入口"),
]

METHOD_COLOR = {
    "get": ("#0F7B57", "#E8F5F0"),
    "post": ("#C8102E", "#FFF0F2"),
    "put": ("#B45309", "#FEF6E7"),
    "delete": ("#96061F", "#FDECEE"),
    "patch": ("#6C7480", "#F2F4F7"),
}


def md(text):
    """极简 markdown → html：**粗体**、`代码`、换行。"""
    if not text:
        return ""
    t = html.escape(text)
    t = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", t)
    t = re.sub(r"`([^`]+)`", r"<code>\1</code>", t)
    t = t.replace("\n", "<br>")
    return t


def ref_name(node):
    if isinstance(node, dict) and "$ref" in node:
        return node["$ref"].split("/")[-1]
    return None


def resolve(spec, node):
    n = ref_name(node)
    if n:
        return spec["components"]["schemas"].get(n, {})
    return node or {}


def props(spec, schema):
    """展开 allOf，返回 (properties, required)。"""
    out, req = {}, set()
    schema = resolve(spec, schema)
    for part in schema.get("allOf", []) + ([schema] if "properties" in schema else []):
        part = resolve(spec, part)
        out.update(part.get("properties", {}))
        req |= set(part.get("required", []))
    return out, req


def type_of(spec, s):
    if not isinstance(s, dict):
        return "any"
    if "$ref" in s:
        return ref_name(s)
    t = s.get("type", "any")
    if t == "array":
        it = s.get("items", {})
        return "array&lt;%s&gt;" % type_of(spec, it)
    if t == "object" and s.get("properties"):
        return "object"
    if s.get("enum"):
        return "%s enum" % t
    return t


def flatten(spec, schema, depth=0):
    """把 schema 展平成 [(name, type, required, desc, depth)]。"""
    p, req = props(spec, schema)
    rows = []
    for name, sub in p.items():
        sub_r = resolve(spec, sub)
        rows.append((name, type_of(spec, sub), name in req, sub_r.get("description", "")
                     or sub.get("description", ""), depth))
    return rows


def example_of(spec, op):
    try:
        content = op["responses"]["200"]["content"]["application/json"]
    except KeyError:
        return ""
    if "example" in content:
        return json.dumps(content["example"], ensure_ascii=False, indent=2)
    return ""


def endpoint_html(spec, path, method, op):
    fg, bg = METHOD_COLOR.get(method, METHOD_COLOR["post"])
    eid = (method + path).replace("/", "-").replace("{", "").replace("}", "")
    out = ['<section class="ep" id="%s">' % eid]
    out.append('<div class="ep-head"><span class="m" style="color:%s;background:%s">%s</span>'
               '<code class="p">%s</code></div>' % (fg, bg, method.upper(), path))
    out.append("<h3>%s</h3>" % html.escape(op.get("summary", "")))
    if op.get("description"):
        out.append('<p class="desc">%s</p>' % md(op["description"]))

    params = op.get("parameters", [])
    if params:
        out.append('<h4>请求参数</h4><table><thead><tr><th>名称</th><th>位置</th>'
                   '<th>类型</th><th>必填</th><th>说明</th></tr></thead><tbody>')
        for p in params:
            if "$ref" in p:
                p = spec["components"]["parameters"][p["$ref"].split("/")[-1]]
            sch = p.get("schema", {})
            out.append("<tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
                html.escape(p.get("name", "")), p.get("in", ""), type_of(spec, sch),
                "是" if p.get("required") else "否", md(p.get("description", ""))))
        out.append("</tbody></table>")

    rb = op.get("requestBody")
    if rb:
        sch = rb["content"]["application/json"]["schema"]
        rows = flatten(spec, sch)
        out.append("<h4>请求体（application/json）</h4><table><thead><tr><th>字段</th>"
                   "<th>类型</th><th>必填</th><th>说明</th></tr></thead><tbody>")
        for name, t, reqd, desc, _ in rows:
            out.append("<tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
                html.escape(name), t, "是" if reqd else "否", md(desc)))
        out.append("</tbody></table>")
        ex = rb["content"]["application/json"].get("example")
        if ex:
            out.append("<h4>请求示例</h4><pre>%s</pre>" % html.escape(
                json.dumps(ex, ensure_ascii=False, indent=2)))

    out.append("<h4>响应</h4>")
    for code, r in op["responses"].items():
        out.append('<p class="rc"><span class="code">%s</span> %s</p>' % (
            code, md(r.get("description", ""))))
    ex = example_of(spec, op)
    if ex:
        out.append("<pre>%s</pre>" % html.escape(ex))
    out.append("</section>")
    return "\n".join(out)


def build():
    spec = yaml.safe_load(open(SPEC, encoding="utf-8"))
    info = spec["info"]
    groups = {}
    for path, item in spec["paths"].items():
        for method, op in item.items():
            if method not in ("get", "post", "put", "delete", "patch"):
                continue
            tag = (op.get("tags") or ["其它"])[0]
            groups.setdefault(tag, []).append((path, method, op))

    order = [t["name"] for t in spec.get("tags", [])] + ["其它"]
    groups = {k: groups[k] for k in order if k in groups}

    nav, body, flat = [], [], []
    for tag, ops in groups.items():
        nav.append('<div class="grp"><b>%s</b>' % html.escape(tag))
        for path, method, op in ops:
            eid = (method + path).replace("/", "-").replace("{", "").replace("}", "")
            nav.append('<a href="#%s"><i>%s</i>%s</a>' % (
                eid, method.upper(), html.escape(op.get("summary", path))))
            flat.append((tag, method.upper(), path, op.get("summary", "")))
        nav.append("</div>")
        body.append('<h2 id="tag-%s">%s</h2>' % (tag, html.escape(tag)))
        for path, method, op in ops:
            body.append(endpoint_html(spec, path, method, op))

    models = []
    for name, schema in spec["components"]["schemas"].items():
        if name.startswith("R") or name in ("Result", "Page"):
            continue
        rows = flatten(spec, schema)
        if not rows:
            continue
        models.append('<div class="model"><h3>%s</h3><table><thead><tr><th>字段</th>'
                      '<th>类型</th><th>必填</th><th>说明</th></tr></thead><tbody>' % name)
        for f, t, reqd, desc, _ in rows:
            models.append("<tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
                html.escape(f), t, "是" if reqd else "否", md(desc)))
        models.append("</tbody></table></div>")

    err_rows = "".join("<tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>" % e
                       for e in ERRORS)
    servers = "".join('<div class="srv"><span>%s</span>%s</div>' % (
        html.escape(s.get("description", "")), html.escape(s["url"])) for s in spec["servers"])

    doc = """<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>%(title)s · 接口文档</title>
<style>
*{box-sizing:border-box}
body{margin:0;font:14px/1.7 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif;
     color:#1A1E24;background:#fff}
a{color:inherit;text-decoration:none}
.layout{display:flex;min-height:100vh}
aside{width:300px;flex:none;border-right:1px solid #E5E8ED;background:#FCFCFD;position:sticky;top:0;
      height:100vh;overflow:auto;padding:22px 18px 60px}
aside h1{font-size:16px;margin:0 0 2px;color:#C8102E;letter-spacing:.5px}
aside .v{font-size:11px;color:#9AA1AB;margin-bottom:14px}
aside input{width:100%%;height:34px;border:1px solid #E5E8ED;border-radius:8px;padding:0 10px;
      font-size:12.5px;margin-bottom:16px;outline:none;background:#fff}
aside input:focus{border-color:#C8102E}
.grp{margin-bottom:14px}
.grp b{display:block;font-size:11px;color:#9AA1AB;letter-spacing:1px;margin:0 0 6px 2px;font-weight:600}
.grp a{display:block;padding:5px 8px;border-radius:6px;font-size:12.5px;color:#3A4048;line-height:1.5}
.grp a:hover{background:#FFF0F2;color:#C8102E}
.grp a i{display:inline-block;font-style:normal;font-size:9.5px;font-weight:700;color:#B9BFC9;
      width:38px;letter-spacing:.3px}
main{flex:1;min-width:0;padding:40px 48px 90px;max-width:1000px}
h1.title{font-size:26px;margin:0 0 6px}
.sub{color:#6C7480;font-size:13px;margin-bottom:26px}
h2{font-size:19px;margin:44px 0 14px;padding-bottom:8px;border-bottom:2px solid #C8102E;color:#C8102E}
h3{font-size:15.5px;margin:0 0 6px}
h4{font-size:12.5px;margin:18px 0 6px;color:#6C7480;font-weight:600;letter-spacing:.5px}
.ep{border:1px solid #E5E8ED;border-radius:12px;padding:18px 20px;margin:16px 0 22px;
    box-shadow:0 2px 10px rgba(17,24,39,.03)}
.ep:target{border-color:#C8102E;box-shadow:0 0 0 3px #FFF0F2}
.ep-head{display:flex;align-items:center;gap:10px;margin-bottom:6px}
.m{font-size:11px;font-weight:700;padding:2px 8px;border-radius:5px;letter-spacing:.5px}
.p{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:13px;background:#F7F8FA;padding:3px 8px;
   border-radius:5px;color:#1A1E24}
.desc{color:#4A5058;font-size:13px;margin:8px 0 0}
table{width:100%%;border-collapse:collapse;font-size:12.5px;margin:6px 0 4px}
th{background:#FFF5F6;color:#96061F;text-align:left;font-weight:600;font-size:11.5px}
th,td{border:1px solid #EEF0F4;padding:6px 9px;vertical-align:top}
td code,td code{font-family:ui-monospace,Menlo,monospace;font-size:11.5px;color:#96061F;background:none}
pre{background:#F8F9FB;border:1px solid #E5E8ED;border-radius:8px;padding:12px 14px;overflow:auto;
    font-family:ui-monospace,Menlo,Consolas,monospace;font-size:11.5px;line-height:1.6;color:#2A2E36;margin:6px 0 10px}
code{font-family:ui-monospace,Menlo,monospace;background:#FFF0F2;color:#96061F;padding:1px 5px;
     border-radius:4px;font-size:12px}
.rc .code{display:inline-block;background:#0F7B57;color:#fff;font-size:11px;padding:1px 7px;
     border-radius:4px;margin-right:6px;font-weight:600}
.note{background:#FFF5F6;border-left:3px solid #C8102E;padding:10px 14px;border-radius:0 8px 8px 0;
      font-size:13px;color:#4A5058;margin:14px 0}
.srv{font-size:12.5px;color:#4A5058;margin:3px 0}
.srv span{display:inline-block;width:120px;color:#9AA1AB;font-size:11.5px}
.model{border-top:1px solid #F1F3F6;padding-top:12px;margin-top:16px}
.model:first-of-type{border-top:none}
@media print{aside{display:none}main{max-width:none;padding:0}.ep{page-break-inside:avoid}}
</style></head><body><div class="layout">
<aside>
  <h1>%(title)s</h1>
  <div class="v">v%(ver)s · OpenAPI 3.0.3</div>
  <input id="q" placeholder="搜索接口…" oninput="filter(this.value)">
  <div id="nav">%(nav)s</div>
</aside>
<main>
  <h1 class="title">%(title)s · 接口文档</h1>
  <div class="sub">%(title)s 小程序 / 师傅端 / 商家后台共用的 HTTP 接口契约，共 %(nops)d 个接口、%(nmodels)d 个数据模型。</div>

  <div class="note">金额字段一律为<b>整数「分」</b>；响应统一为 <code>{code, msg, data, traceId}</code>，
  <code>code = 0</code> 为成功。鉴权用 <code>Authorization: Bearer &lt;JWT&gt;</code>。</div>

  <h2>环境</h2>
  %(servers)s
  <p class="desc">本地联调：<code>python3 api/mock_server.py 8080</code> 即可启动 Mock 服务，
  小程序端把 <code>config.js</code> 的 <code>USE_MOCK</code> 设为 <code>false</code> 并打开开发者工具
  「不校验合法域名」即可直连。</p>
  <div class="note">仅 Mock 环境额外提供 <code>POST /api/pay/mock-success</code>：模拟微信支付成功回调，
  用于在开发者工具里跑通「支付 → 状态推进」而不需要真实商户号。<b>生产环境不存在该接口</b>，
  订单状态只认微信异步通知。</div>

  <h2>错误码</h2>
  <table><thead><tr><th>code</th><th>含义</th><th>前端处理建议</th></tr></thead>
  <tbody>%(errors)s</tbody></table>

  %(body)s

  <h2 id="models">数据模型</h2>
  %(models)s
</main></div>
<script>
function filter(k){k=k.trim().toLowerCase();
  document.querySelectorAll('#nav a').forEach(function(a){
    a.style.display=(!k||a.textContent.toLowerCase().indexOf(k)>=0)?'block':'none';});
  document.querySelectorAll('#nav .grp').forEach(function(g){
    var any=[].some.call(g.querySelectorAll('a'),function(a){return a.style.display!=='none';});
    g.style.display=any?'block':'none';});}
</script></body></html>""" % {
        "title": info["title"], "ver": info["version"], "nav": "\n".join(nav),
        "body": "\n".join(body), "servers": servers, "errors": err_rows,
        "nops": sum(len(v) for v in groups.values()),
        "models": "\n".join(models), "nmodels": len(models),
    }

    out_html = os.path.join(BASE, "接口文档.html")
    open(out_html, "w", encoding="utf-8").write(doc)
    print("wrote", out_html, len(doc), "bytes")

    md_lines = ["# 接口清单\n",
                "共 %d 个接口。完整字段与示例见 `接口文档.html`，机器可读契约见 `openapi.yaml`。\n" % len(flat),
                "| 模块 | 方法 | 路径 | 说明 |", "| --- | --- | --- | --- |"]
    for tag, method, path, summary in flat:
        md_lines.append("| %s | `%s` | `%s` | %s |" % (tag, method, path, summary))
    open(os.path.join(BASE, "接口清单.md"), "w", encoding="utf-8").write("\n".join(md_lines) + "\n")
    print("wrote 接口清单.md", len(flat), "rows")


if __name__ == "__main__":
    build()
