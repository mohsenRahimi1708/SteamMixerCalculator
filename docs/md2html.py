#!/usr/bin/env python
"""Convert USER_GUIDE.md to print-styled HTML for PDF generation."""
import html
import re

SRC = "docs/USER_GUIDE.md"
OUT = "docs/USER_GUIDE.html"

with open(SRC, encoding="utf-8") as f:
    lines = f.read().splitlines()


def inline(text):
    """Inline markdown -> HTML (escape first)."""
    t = html.escape(text, quote=False)
    t = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", t)
    t = re.sub(r"\*(.+?)\*", r"<em>\1</em>", t)
    t = re.sub(r"`(.+?)`", r"<code>\1</code>", t)
    t = re.sub(r"\[(.+?)\]\((.+?)\)", r'<a href="\2">\1</a>', t)
    return t


out = []
i = 0
in_code = False
code_buf = []
list_stack = []  # 'ul' | 'ol'

def close_list():
    while list_stack:
        out.append("</%s>" % list_stack.pop())

while i < len(lines):
    line = lines[i]

    if line.strip().startswith("```"):
        if in_code:
            out.append("<pre><code>%s</code></pre>" % html.escape("\n".join(code_buf)))
            code_buf = []
            in_code = False
        else:
            close_list()
            in_code = True
        i += 1
        continue
    if in_code:
        code_buf.append(line)
        i += 1
        continue

    stripped = line.strip()

    # tables
    if stripped.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[\s\-|:]+\|$", lines[i + 1].strip()):
        close_list()
        header = [c.strip() for c in stripped.strip("|").split("|")]
        out.append("<table><thead><tr>" + "".join("<th>%s</th>" % inline(c) for c in header) + "</tr></thead><tbody>")
        i += 2
        while i < len(lines) and lines[i].strip().startswith("|"):
            cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
            out.append("<tr>" + "".join("<td>%s</td>" % inline(c) for c in cells) + "</tr>")
            i += 1
        out.append("</tbody></table>")
        continue

    m = re.match(r"^(#{1,4})\s+(.*)", stripped)
    if m:
        close_list()
        level = len(m.group(1))
        out.append("<h%d>%s</h%d>" % (level, inline(m.group(2)), level))
        i += 1
        continue

    if stripped == "---":
        close_list()
        out.append("<hr/>")
        i += 1
        continue

    m = re.match(r"^[-*]\s+(.*)", stripped)
    if m:
        if not list_stack or list_stack[-1] != "ul":
            close_list()
            list_stack.append("ul")
            out.append("<ul>")
        out.append("<li>%s</li>" % inline(m.group(1)))
        i += 1
        continue

    m = re.match(r"^(\d+)\.\s+(.*)", stripped)
    if m:
        if not list_stack or list_stack[-1] != "ol":
            close_list()
            list_stack.append("ol")
            out.append("<ol>")
        out.append("<li>%s</li>" % inline(m.group(2)))
        i += 1
        continue

    if not stripped:
        close_list()
        i += 1
        continue

    # paragraph
    close_list()
    out.append("<p>%s</p>" % inline(stripped))
    i += 1

close_list()

CSS = """
@page { size: A4; margin: 18mm 16mm; }
body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 10.5pt; color: #1a1a2e; line-height: 1.5; }
h1 { font-size: 20pt; color: #0b3d66; border-bottom: 3px solid #0b3d66; padding-bottom: 6px; margin-top: 24px; page-break-after: avoid; }
h2 { font-size: 15pt; color: #10558a; border-bottom: 1px solid #c8d8e8; padding-bottom: 3px; margin-top: 20px; page-break-after: avoid; }
h3 { font-size: 12pt; color: #17629c; margin-top: 14px; page-break-after: avoid; }
h4 { font-size: 11pt; color: #17629c; page-break-after: avoid; }
p, li { orphans: 3; widows: 3; }
table { border-collapse: collapse; width: 100%; margin: 8px 0; page-break-inside: avoid; font-size: 9.5pt; }
th { background: #0b3d66; color: white; text-align: left; padding: 5px 8px; }
td { border: 1px solid #ccd8e4; padding: 4px 8px; vertical-align: top; }
tr:nth-child(even) td { background: #f2f6fa; }
pre { background: #f4f4f8; border: 1px solid #d8d8e4; border-left: 4px solid #0b3d66; padding: 8px 10px; font-family: Consolas, monospace; font-size: 9pt; overflow-x: hidden; page-break-inside: avoid; }
code { font-family: Consolas, monospace; font-size: 9pt; background: #eef1f6; padding: 1px 3px; border-radius: 3px; }
pre code { background: none; padding: 0; }
hr { border: none; border-top: 1px solid #c8d8e8; margin: 16px 0; }
a { color: #10558a; text-decoration: none; }
strong { color: #0b3d66; }
"""

doc = "<!DOCTYPE html><html><head><meta charset='utf-8'><style>%s</style></head><body>\n%s\n</body></html>" % (CSS, "\n".join(out))
with open(OUT, "w", encoding="utf-8") as f:
    f.write(doc)
print("wrote", OUT, "with", len(out), "blocks")
