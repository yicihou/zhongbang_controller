# -*- coding: utf-8 -*-
"""把本项目的 Markdown 转成排版良好的 DOCX（红白配色、表格、代码块、图片题注、页码）。"""
import os
import re
import sys
from PIL import Image
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

RED = RGBColor(0xC8, 0x10, 0x2E)
RED_D = RGBColor(0x96, 0x06, 0x1F)
INK = RGBColor(0x1A, 0x1E, 0x24)
MUT = RGBColor(0x6C, 0x74, 0x80)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)

LATIN = "Arial"
EA = "宋体"
EAH = "黑体"
MONO = "Menlo"
USABLE = 16.6  # cm


# ---------------------------------------------------------------- 基础工具
def set_font(run, size=None, bold=None, color=None, ea=EA, latin=LATIN):
    if size:
        run.font.size = Pt(size)
    if bold is not None:
        run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color
    run.font.name = latin
    rPr = run._element.get_or_add_rPr()
    rf = rPr.find(qn("w:rFonts"))
    if rf is None:
        rf = OxmlElement("w:rFonts")
        rPr.append(rf)
    rf.set(qn("w:ascii"), latin)
    rf.set(qn("w:hAnsi"), latin)
    rf.set(qn("w:eastAsia"), ea)


def shade(el, color):
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), color)
    el.append(shd)


def para_shade(p, color):
    pPr = p._p.get_or_add_pPr()
    shade(pPr, color)


def para_border(p, edge="bottom", color="C8102E", sz=8, space=4):
    pPr = p._p.get_or_add_pPr()
    pBdr = pPr.find(qn("w:pBdr"))
    if pBdr is None:
        pBdr = OxmlElement("w:pBdr")
        pPr.append(pBdr)
    el = OxmlElement("w:" + edge)
    el.set(qn("w:val"), "single")
    el.set(qn("w:sz"), str(sz))
    el.set(qn("w:space"), str(space))
    el.set(qn("w:color"), color)
    pBdr.append(el)


def cell_shade(cell, color):
    tcPr = cell._tc.get_or_add_tcPr()
    shade(tcPr, color)


def table_borders(table, color="E5E8ED", sz=4):
    tbl = table._tbl
    tblPr = tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        el = OxmlElement("w:" + edge)
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), str(sz))
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), color)
        borders.append(el)
    tblPr.append(borders)


def cell_margins(table, top=50, bottom=50, left=90, right=90):
    tblPr = table._tbl.tblPr
    mar = OxmlElement("w:tblCellMar")
    for k, v in (("top", top), ("left", left), ("bottom", bottom), ("right", right)):
        el = OxmlElement("w:" + k)
        el.set(qn("w:w"), str(v))
        el.set(qn("w:type"), "dxa")
        mar.append(el)
    tblPr.append(mar)


def keep_with_next(p):
    pPr = p._p.get_or_add_pPr()
    el = OxmlElement("w:keepNext")
    pPr.append(el)


def add_field(paragraph, instr, size=8.5, color=MUT):
    run = paragraph.add_run()
    fld = OxmlElement("w:fldChar")
    fld.set(qn("w:fldCharType"), "begin")
    run._r.append(fld)
    run2 = paragraph.add_run()
    it = OxmlElement("w:instrText")
    it.set(qn("xml:space"), "preserve")
    it.text = instr
    run2._r.append(it)
    run3 = paragraph.add_run()
    fld2 = OxmlElement("w:fldChar")
    fld2.set(qn("w:fldCharType"), "separate")
    run3._r.append(fld2)
    run4 = paragraph.add_run("1")
    run5 = paragraph.add_run()
    fld3 = OxmlElement("w:fldChar")
    fld3.set(qn("w:fldCharType"), "end")
    run5._r.append(fld3)
    for r in (run, run2, run3, run4, run5):
        set_font(r, size, False, color)


def rich(p, text, size=10.5, color=INK, bold=False, ea=EA, latin=LATIN):
    """解析 **粗体** 与 `代码`。"""
    for tok in re.split(r"(\*\*.+?\*\*|`[^`]+`)", text):
        if not tok:
            continue
        if tok.startswith("**") and tok.endswith("**") and len(tok) > 4:
            r = p.add_run(tok[2:-2])
            set_font(r, size, True, color, ea, latin)
        elif tok.startswith("`") and tok.endswith("`") and len(tok) > 2:
            r = p.add_run(tok[1:-1])
            set_font(r, size - 0.8, False, RGBColor(0x96, 0x06, 0x1F), MONO, MONO)
        else:
            r = p.add_run(tok)
            set_font(r, size, bold, color, ea, latin)


# ---------------------------------------------------------------- 文档骨架
def new_document(meta):
    doc = Document()
    st = doc.styles["Normal"]
    st.font.size = Pt(10.5)
    st.font.name = LATIN
    st.element.rPr.rFonts.set(qn("w:eastAsia"), EA)
    st.paragraph_format.line_spacing = 1.5
    st.paragraph_format.space_after = Pt(4)

    sec = doc.sections[0]
    sec.page_width, sec.page_height = Cm(21), Cm(29.7)
    sec.top_margin, sec.bottom_margin = Cm(2.2), Cm(2.0)
    sec.left_margin, sec.right_margin = Cm(2.2), Cm(2.2)
    sec.header_distance, sec.footer_distance = Cm(1.1), Cm(1.0)

    # 页眉
    hp = sec.header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    r = hp.add_run(meta.get("title", "") + "　·　" + meta.get("tag", ""))
    set_font(r, 8, False, RGBColor(0x9A, 0xA1, 0xAB))
    para_border(hp, "bottom", "E5E8ED", 4, 2)

    # 页脚页码
    fp = sec.footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r0 = fp.add_run("— ")
    set_font(r0, 8.5, False, MUT)
    add_field(fp, " PAGE ")
    r1 = fp.add_run(" —")
    set_font(r1, 8.5, False, MUT)
    return doc


def cover(doc, meta):
    for _ in range(3):
        doc.add_paragraph()
    p = doc.add_paragraph()
    r = p.add_run(meta.get("tag", ""))
    set_font(r, 10, True, RED, EAH)
    p.paragraph_format.space_after = Pt(10)

    p = doc.add_paragraph()
    r = p.add_run(meta.get("title", ""))
    set_font(r, 30, True, INK, EAH)
    p.paragraph_format.space_after = Pt(6)

    p = doc.add_paragraph()
    r = p.add_run(meta.get("subtitle", ""))
    set_font(r, 12, False, MUT, EAH)
    p.paragraph_format.space_after = Pt(18)
    para_border(p, "bottom", "C8102E", 16, 18)

    doc.add_paragraph()
    info = [("文档版本", meta.get("version", "v1.0")),
            ("发布日期", meta.get("date", "")),
            ("适用范围", "缝纫机经销商小程序（客户端 / 师傅端 / 商家后台）"),
            ("阅读对象", "产品负责人、Java 开发、前端开发、测试、门店管理者")]
    t = doc.add_table(rows=len(info), cols=2)
    t.autofit = False
    t.alignment = WD_TABLE_ALIGNMENT.LEFT
    for i, (k, v) in enumerate(info):
        c0, c1 = t.rows[i].cells
        c0.width, c1.width = Cm(3.2), Cm(10.5)
        p0 = c0.paragraphs[0]
        rich(p0, k, 9.5, MUT)
        p1 = c1.paragraphs[0]
        rich(p1, v, 9.5, INK)
        for c in (c0, c1):
            c.paragraphs[0].paragraph_format.space_after = Pt(4)
    table_borders(t, "FFFFFF", 2)

    for _ in range(6):
        doc.add_paragraph()
    p = doc.add_paragraph()
    r = p.add_run("本文件为项目交付文档，配套流程图见 diagrams/ 目录，原文见 docs/ 目录。")
    set_font(r, 8.5, False, RGBColor(0x9A, 0xA1, 0xAB))


def toc(doc, chapters):
    p = doc.add_paragraph()
    p.paragraph_format.page_break_before = True
    r = p.add_run("目　录")
    set_font(r, 16, True, RED, EAH)
    p.paragraph_format.space_after = Pt(12)
    para_border(p, "bottom", "C8102E", 10, 6)
    for i, (title, subs) in enumerate(chapters):
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(6)
        r = p.add_run("%02d" % (i + 1))
        set_font(r, 9.5, True, RED)
        r = p.add_run("　" + title)
        set_font(r, 10.5, True, INK, EAH)
        if subs:
            p2 = doc.add_paragraph()
            p2.paragraph_format.left_indent = Cm(1.0)
            p2.paragraph_format.space_after = Pt(8)
            r = p2.add_run("　".join(subs))
            set_font(r, 8.5, False, MUT)


# ---------------------------------------------------------------- 块级渲染
def h1(doc, text, brk=False):
    p = doc.add_paragraph()
    if brk:
        p.paragraph_format.page_break_before = True
    p.paragraph_format.space_before = Pt(16)
    p.paragraph_format.space_after = Pt(10)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    set_font(r, 15.5, True, RED, EAH)
    para_border(p, "bottom", "F0BEC6", 8, 5)
    return p


def h2(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    set_font(r, 12.5, True, INK, EAH)
    return p


def h3(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(9)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.keep_with_next = True
    r = p.add_run(text)
    set_font(r, 11, True, RED_D, EAH)
    return p


def body(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(5)
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    rich(p, text)
    return p


def bullet(doc, text, level=0):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.65 + level * 0.55)
    p.paragraph_format.first_line_indent = Cm(-0.45)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.45
    r = p.add_run("•  " if level == 0 else "–  ")
    set_font(r, 10.5, True, RED if level == 0 else MUT)
    rich(p, text)
    return p


def numbered(doc, num, text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.75)
    p.paragraph_format.first_line_indent = Cm(-0.55)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.45
    r = p.add_run(num + "  ")
    set_font(r, 10.5, True, RED_D)
    rich(p, text)
    return p


def code_block(doc, lines):
    t = doc.add_table(rows=1, cols=1)
    t.autofit = False
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = t.rows[0].cells[0]
    cell.width = Cm(USABLE)
    cell_shade(cell, "F8F9FB")
    table_borders(t, "E5E8ED", 4)
    cell_margins(t, 80, 80, 140, 100)
    first = True
    for ln in lines:
        p = cell.paragraphs[0] if first else cell.add_paragraph()
        first = False
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.line_spacing = 1.12
        r = p.add_run(ln if ln else " ")
        set_font(r, 8, False, RGBColor(0x2A, 0x2E, 0x36), MONO, MONO)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def quote(doc, text):
    t = doc.add_table(rows=1, cols=1)
    t.autofit = False
    cell = t.rows[0].cells[0]
    cell.width = Cm(USABLE)
    cell_shade(cell, "FFF5F6")
    table_borders(t, "F0BEC6", 4)
    cell_margins(t, 90, 90, 140, 120)
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.4
    rich(p, text, 9.5, RED_D)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def image(doc, path, caption):
    if not os.path.isabs(path):
        path = os.path.join(BASE, path)
    w, h = Image.open(path).size
    width = min(16.2, w / 300 * 2.54)  # 300dpi 原生宽度，最大 16.2cm
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(4)
    keep_with_next(p)
    p.add_run().add_picture(path, width=Cm(width))
    if caption:
        c = doc.add_paragraph()
        c.alignment = WD_ALIGN_PARAGRAPH.CENTER
        c.paragraph_format.space_after = Pt(12)
        r = c.add_run(caption)
        set_font(r, 8.5, False, MUT)


def table(doc, header, rows):
    ncol = len(header)
    # 依据内容长度分配列宽
    weights = []
    for i in range(ncol):
        lens = [len(header[i])] + [len(r[i]) if i < len(r) else 0 for r in rows]
        weights.append(max(6, min(34, int(sum(lens) / max(1, len(lens)) * 1.5))))
    total = sum(weights)
    widths = [max(1.9, USABLE * w / total) for w in weights]
    scale = USABLE / sum(widths)
    widths = [w * scale for w in widths]

    t = doc.add_table(rows=len(rows) + 1, cols=ncol)
    t.autofit = False
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_borders(t, "DDE1E7", 4)
    cell_margins(t, 55, 55, 90, 90)

    for j, txt in enumerate(header):
        cell = t.rows[0].cells[j]
        cell.width = Cm(widths[j])
        cell_shade(cell, "C8102E")
        p = cell.paragraphs[0]
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.2
        r = p.add_run(txt)
        set_font(r, 8.6, True, WHITE, EAH)

    # 表头跨页重复
    trPr = t.rows[0]._tr.get_or_add_trPr()
    th = OxmlElement("w:tblHeader")
    trPr.append(th)

    for i, row in enumerate(rows):
        for j in range(ncol):
            cell = t.rows[i + 1].cells[j]
            cell.width = Cm(widths[j])
            if i % 2 == 1:
                cell_shade(cell, "FAFAFB")
            p = cell.paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            p.paragraph_format.line_spacing = 1.25
            rich(p, row[j] if j < len(row) else "", 8.6, INK)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


# ---------------------------------------------------------------- 主流程
def parse(md_path):
    with open(md_path, encoding="utf-8") as f:
        raw = f.read().split("\n")
    meta = {}
    i = 0
    if raw and raw[0].strip() == "---":
        i = 1
        while i < len(raw) and raw[i].strip() != "---":
            if ":" in raw[i]:
                k, v = raw[i].split(":", 1)
                meta[k.strip()] = v.strip()
            i += 1
        i += 1
    return meta, raw[i:]


def build(md_path, docx_path):
    meta, lines = parse(md_path)
    doc = new_document(meta)

    chapters = []
    cur = None
    for ln in lines:
        if ln.startswith("## "):
            cur = (ln[3:].strip(), [])
            chapters.append(cur)
        elif ln.startswith("### ") and cur is not None:
            cur[1].append(ln[4:].strip())

    cover(doc, meta)
    toc(doc, chapters)

    i = 0
    n = len(lines)
    first_h1 = True
    while i < n:
        ln = lines[i]
        s = ln.strip()

        if not s:
            i += 1
            continue

        if s.startswith("```"):
            j = i + 1
            buf = []
            while j < n and not lines[j].strip().startswith("```"):
                buf.append(lines[j].rstrip())
                j += 1
            code_block(doc, buf)
            i = j + 1
            continue

        m = re.match(r"^!\[(.*?)\]\((.*?)\)", s)
        if m:
            image(doc, m.group(2), m.group(1))
            i += 1
            continue

        if s.startswith("#### "):
            h3(doc, s[5:].strip())
            i += 1
            continue
        if s.startswith("### "):
            h2(doc, s[4:].strip())
            i += 1
            continue
        if s.startswith("## "):
            h1(doc, s[3:].strip(), brk=first_h1)
            first_h1 = False
            i += 1
            continue
        if s.startswith("# "):
            h1(doc, s[2:].strip())
            i += 1
            continue

        if s.startswith("|") and i + 1 < n and re.match(r"^\|[\s:\-|]+\|$", lines[i + 1].strip()):
            header = [c.strip() for c in s.strip("|").split("|")]
            rows = []
            j = i + 2
            while j < n and lines[j].strip().startswith("|"):
                cells = [c.strip() for c in lines[j].strip().strip("|").split("|")]
                rows.append(cells)
                j += 1
            table(doc, header, rows)
            i = j
            continue

        if s.startswith("> "):
            buf = []
            while i < n and lines[i].strip().startswith(">"):
                buf.append(lines[i].strip().lstrip(">").strip())
                i += 1
            quote(doc, " ".join(buf))
            continue

        if re.match(r"^[-*] ", s):
            level = 1 if ln.startswith("  ") else 0
            bullet(doc, s[2:].strip(), level)
            i += 1
            continue

        m = re.match(r"^(\d+)\.\s+(.*)$", s)
        if m:
            numbered(doc, m.group(1) + ".", m.group(2))
            i += 1
            continue

        # 普通段落：合并连续非空、非特殊行
        buf = [s]
        j = i + 1
        while j < n:
            t = lines[j].strip()
            if (not t or t.startswith(("#", "|", ">", "```", "!["))
                    or re.match(r"^[-*] ", t) or re.match(r"^\d+\.\s", t)):
                break
            buf.append(t)
            j += 1
        body(doc, "".join(buf) if len(buf) > 1 else buf[0])
        i = j

    doc.core_properties.title = meta.get("title", "")
    doc.core_properties.author = "缝纫机经销小程序项目组"
    doc.core_properties.subject = meta.get("tag", "")
    doc.save(docx_path)
    return docx_path


BASE = os.path.dirname(os.path.abspath(__file__))

if __name__ == "__main__":
    md, out = sys.argv[1], sys.argv[2]
    print("saved:", build(md, out))
