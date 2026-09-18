# -*- coding: utf-8 -*-
"""
知序学堂 - 课程封面批量生成脚本
--------------------------------
1. 解析 deploy/mysql/init/ 下的课程种子 SQL（20-demo-mvp.sql / 30-demo-features.sql 等），
   提取课程 id、名称、二级分类 id。
2. 按「分类 -> 配色/装饰主题」批量生成统一风格的 SVG 封面（640x360, 16:9）。
3. 输出到 tj-front/tj-protal/public/covers/<课程id>.svg

用法：
    python deploy/generate_covers.py            # 生成全部封面
    python deploy/generate_covers.py 1001 1002  # 只生成指定课程

分类样式映射支持两层匹配：
  a) 按二级分类 ID（与种子 SQL 一致）
  b) 按分类名称关键词（兼容"知序学堂分类体系改造"后的新分类体系）
均未命中时使用领域级默认样式，保证任何新分类都有可用封面。
"""
import os
import re
import sys
import html

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SQL_DIR = os.path.join(ROOT, "deploy", "mysql", "init")
OUT_DIR = os.path.join(ROOT, "tj-front", "tj-protal", "public", "covers")

W, H = 640, 360

# ----------------------------------------------------------------------
# 主题样式：渐变双色 + 强调色 + 装饰图形 key
# ----------------------------------------------------------------------
def theme(c1, c2, accent, deco="dots"):
    return {"c1": c1, "c2": c2, "accent": accent, "deco": deco}

# 按二级分类 ID 映射（当前种子数据）
THEME_BY_CATE_ID = {
    2:  theme("#1e3a8a", "#7c3aed", "#fbbf24", "code"),    # Java
    4:  theme("#0f766e", "#0ea5e9", "#a7f3d0", "net"),     # Spring Cloud / 微服务
    6:  theme("#1d4ed8", "#0c4a6e", "#93c5fd", "db"),      # MySQL
    8:  theme("#b91c1c", "#ea580c", "#fed7aa", "bolt"),    # Redis
    10: theme("#1e40af", "#eab308", "#fef08a", "py"),      # Python
}

# 按分类名称关键词映射（兼容新分类体系：5 大一级领域及其二级方向）
# key: (正则, 主题)
THEME_BY_NAME = [
    # ---- IT互联网 ----
    (r"java",            theme("#1e3a8a", "#7c3aed", "#fbbf24", "code")),
    (r"python",          theme("#1e40af", "#eab308", "#fef08a", "py")),
    (r"\bgo\b|golang",   theme("#0e7490", "#0891b2", "#cffafe", "code")),
    (r"spring|微服务|后端", theme("#0f766e", "#0ea5e9", "#a7f3d0", "net")),
    (r"mysql|数据库|sql",  theme("#1d4ed8", "#0c4a6e", "#93c5fd", "db")),
    (r"redis",           theme("#b91c1c", "#ea580c", "#fed7aa", "bolt")),
    (r"\bgo\b|golang|并发", theme("#0e7490", "#0891b2", "#cffafe", "code")),
    (r"操作系统|linux",    theme("#334155", "#0f172a", "#94a3b8", "chip")),
    (r"网络|tcp|http",    theme("#312e81", "#4338ca", "#c7d2fe", "net")),
    (r"数据结构|算法|排序", theme("#134e4a", "#0f766e", "#5eead4", "graph")),
    (r"计算机|编程语言",   theme("#1e293b", "#334155", "#7dd3fc", "chip")),
    # ---- 设计创意 ----
    (r"ui|figma|界面|交互", theme("#7e22ce", "#c026d3", "#f5d0fe", "pen")),
    (r"ai|prompt|绘画",   theme("#4c1d95", "#9333ea", "#e9d5ff", "spark")),
    (r"设计|创意",        theme("#86198f", "#db2777", "#fbcfe8", "pen")),
    # ---- 办公职场 ----
    (r"excel|word|ppt|powerpoint|wps|办公|文档", theme("#166534", "#16a34a", "#bbf7d0", "doc")),
    (r"时间管理|任务管理|gtd|效率|职场|知识管理|拖延", theme("#065f46", "#059669", "#a7f3d0", "clock")),
    # ---- 语言学习 ----
    (r"英语|english|口语|听力|音标|语法", theme("#1d4ed8", "#38bdf8", "#dbeafe", "globe")),
    # ---- 考试认证 ----
    (r"cet|四六级|考试|认证|软考|等级|云计算", theme("#92400e", "#d97706", "#fde68a", "medal")),
    # ---- 领域级兜底 ----
    (r"it|互联网",       theme("#1e293b", "#475569", "#7dd3fc", "chip")),
    (r"语言",            theme("#1d4ed8", "#38bdf8", "#dbeafe", "globe")),
]

DEFAULT_THEME = theme("#1e293b", "#475569", "#7dd3fc", "chip")


# 二级/三级分类 id -> 名称（用于标签显示与名称匹配）
CATE_NAMES = {
    # 旧体系二级
    1: "后端开发", 2: "Java", 3: "微服务", 4: "Spring Cloud",
    5: "Spring Cloud 实战", 6: "MySQL", 7: "MySQL 数据库", 8: "Redis",
    9: "Redis 高级应用", 10: "Python", 11: "Python 编程", 12: "Java 核心",
    # 新体系一级（领域）
    101: "IT互联网", 102: "设计创意", 103: "办公职场", 104: "语言学习", 105: "考试认证",
    # 新体系二级
    111: "计算机基础", 112: "编程语言", 141: "UI设计", 142: "AI创意",
    161: "办公软件", 162: "工作效率", 181: "英语", 191: "大学考试", 192: "IT认证",
    # 新体系三级（2xx 段，标签显示优先用三级）
    211: "操作系统", 212: "计算机网络", 213: "数据结构", 214: "算法",
    221: "Java", 222: "Python", 223: "Go",
    241: "UI基础", 242: "Figma", 243: "界面设计", 244: "交互设计",
    251: "AI绘画", 252: "AI设计", 253: "Prompt", 254: "AI工作流",
    261: "Word", 262: "Excel", 263: "PowerPoint", 264: "WPS",
    271: "时间管理", 272: "任务管理", 273: "知识管理", 274: "AI办公",
    281: "英语基础", 282: "听力", 283: "口语", 284: "商务英语",
    291: "CET-4", 292: "CET-6", 293: "计算机等级考试", 294: "软考", 295: "云计算",
}


# ----------------------------------------------------------------------
# 解析课程 SQL
# ----------------------------------------------------------------------

# ----------------------------------------------------------------------
# SVG 生成
# ----------------------------------------------------------------------
DECO = {
    "code":  '<g font-family="monospace" font-size="26" fill="#ffffff" opacity="0.14">'
             '<text x="470" y="90">{ }</text><text x="520" y="140">&lt;/&gt;</text>'
             '<text x="450" y="310" font-size="40">;</text></g>',
    "net":   '<g stroke="#ffffff" opacity="0.16" fill="none" stroke-width="2">'
             '<circle cx="520" cy="90" r="46"/><circle cx="580" cy="150" r="26"/>'
             '<path d="M520 136 L556 150 M474 90 L520 124"/></g>',
    "db":    '<g stroke="#ffffff" opacity="0.16" fill="none" stroke-width="3">'
             '<ellipse cx="530" cy="80" rx="60" ry="18"/>'
             '<path d="M470 80 V150 a60 18 0 0 0 120 0 V80"/>'
             '<path d="M470 115 a60 18 0 0 0 120 0"/></g>',
    "bolt":  '<path d="M540 50 l-40 70 h30 l-25 60 65 -85 h-30 l28 -35 z" '
             'fill="#ffffff" opacity="0.18"/>',
    "py":    '<g fill="#ffffff" opacity="0.16">'
             '<rect x="490" y="60" width="44" height="110" rx="22"/>'
             '<rect x="510" y="120" width="44" height="110" rx="22" opacity="0.6"/></g>',
    "chip":  '<g stroke="#ffffff" opacity="0.16" fill="none" stroke-width="3">'
             '<rect x="490" y="60" width="90" height="90" rx="10"/>'
             '<path d="M510 40 v20 M535 40 v20 M560 40 v20 M510 150 v20 M535 150 v20 M560 150 v20'
             ' M470 80 h20 M470 105 h20 M470 130 h20 M580 80 h20 M580 105 h20 M580 130 h20"/></g>',
    "graph": '<g stroke="#ffffff" opacity="0.16" fill="#ffffff">'
             '<circle cx="500" cy="80" r="8"/><circle cx="560" cy="110" r="8"/>'
             '<circle cx="530" cy="170" r="8"/><circle cx="470" cy="140" r="8"/>'
             '<g fill="none" stroke-width="2">'
             '<path d="M500 80 L560 110 L530 170 L470 140 Z"/></g></g>',
    "pen":   '<g transform="rotate(40 540 110)" fill="#ffffff" opacity="0.16">'
             '<rect x="532" y="40" width="18" height="120" rx="4"/>'
             '<path d="M532 160 l9 26 9 -26 z"/></g>',
    "spark": '<g fill="#ffffff" opacity="0.2">'
             '<path d="M520 50 l10 30 30 10 -30 10 -10 30 -10 -30 -30 -10 30 -10 z"/>'
             '<path d="M580 140 l6 16 16 6 -16 6 -6 16 -6 -16 -16 -6 16 -6 z" opacity="0.7"/></g>',
    "doc":   '<g stroke="#ffffff" opacity="0.16" fill="none" stroke-width="3">'
             '<rect x="490" y="50" width="86" height="120" rx="8"/>'
             '<path d="M505 80 h56 M505 100 h56 M505 120 h40"/></g>',
    "clock": '<g stroke="#ffffff" opacity="0.16" fill="none" stroke-width="4">'
             '<circle cx="535" cy="105" r="48"/>'
             '<path d="M535 105 L535 72 M535 105 L560 118"/></g>',
    "globe": '<g stroke="#ffffff" opacity="0.16" fill="none" stroke-width="3">'
             '<circle cx="535" cy="105" r="50"/>'
             '<ellipse cx="535" cy="105" rx="22" ry="50"/>'
             '<path d="M487 90 h96 M487 122 h96"/></g>',
    "medal": '<g fill="#ffffff" opacity="0.18">'
             '<circle cx="535" cy="130" r="36"/>'
             '<path d="M510 105 l-14 -55 h28 l11 40 11 -40 h28 l-14 55 z"/></g>',
    "bolt2": None,
}


def wrap_title(title, per_line=11, max_lines=2):
    lines = []
    while title and len(lines) < max_lines:
        lines.append(title[:per_line])
        title = title[per_line:]
    if title:
        lines[-1] = lines[-1][:-1] + "…"
    return lines


def svg_cover(course):
    t = pick_theme(course)
    lines = wrap_title(course["name"])
    fs = 40 if max(len(l) for l in lines) <= 9 else 34
    y0 = 200 if len(lines) == 2 else 226

    title_svg = "".join(
        f'<text x="44" y="{y0 + i * (fs + 12)}" font-size="{fs}" font-weight="700" '
        f'fill="#ffffff" font-family="\'PingFang SC\',\'Microsoft YaHei\',sans-serif">{html.escape(l)}</text>'
        for i, l in enumerate(lines)
    )
    deco = DECO.get(t["deco"], DECO["code"])
    deco_id = hashlib_deco(t["deco"])
    tag_x = 40 + len(t["tag"]) * 16 + 32

    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{t['c1']}"/>
      <stop offset="1" stop-color="{t['c2']}"/>
    </linearGradient>
  </defs>
  <rect width="{W}" height="{H}" fill="url(#bg)"/>
  <circle cx="{W - 60}" cy="-30" r="180" fill="#ffffff" opacity="0.06"/>
  <circle cx="{W - 140}" cy="{H + 40}" r="140" fill="#ffffff" opacity="0.05"/>
  {deco.replace("<g ", f'<g id="{deco_id}" ')}
  <rect x="40" y="88" width="{tag_x}" height="36" rx="18" fill="{t['accent']}" opacity="0.92"/>
  <text x="{tag_x / 2 + 40}" y="112" font-size="19" fill="{t['c2']}" text-anchor="middle"
        font-weight="600" font-family="'PingFang SC','Microsoft YaHei',sans-serif">{html.escape(t['tag'])}</text>
  {title_svg}
  <text x="40" y="{H - 28}" font-size="18" fill="#ffffff" opacity="0.75"
        font-family="'PingFang SC','Microsoft YaHei',sans-serif">知序学堂 · {t['free_label']}</text>
</svg>'''


def hashlib_deco(name):
    return "deco-" + re.sub(r"[^a-z0-9]", "", name or "x")


def pick_theme(course):
    t = pick_theme_raw(course)
    tag = (CATE_NAMES.get(course.get("cate3"))
           or CATE_NAMES.get(course["cate2"]) or "精选课程")
    t = dict(t)
    t["tag"] = tag
    t["free_label"] = "免费课程" if course.get("free") else "精品课程"
    return t


# course dict 需带 free 字段；解析 SQL 时补充
# course_type=2；cover_url 可为空串或 '/covers/x.svg'；三级分类 ID 位数不限
ROW_RE_FULL = re.compile(
    r"\((\d+),\s*'([^']+)',\s*2,\s*'[^']*',\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d),"
)


def load_courses_full():
    courses = {}
    for fn in sorted(os.listdir(SQL_DIR)):
        if not fn.endswith(".sql"):
            continue
        text = open(os.path.join(SQL_DIR, fn), encoding="utf-8").read()
        for m in ROW_RE_FULL.finditer(text):
            cid, name, _c1, c2, c3, free = m.groups()
            courses[int(cid)] = {
                "id": int(cid), "name": name,
                "cate2": int(c2), "cate3": int(c3), "free": free == "1",
            }
    return [courses[k] for k in sorted(courses)]


def pick_theme_raw(course):
    if course["cate2"] in THEME_BY_CATE_ID:
        return THEME_BY_CATE_ID[course["cate2"]]
    # 标签/关键词优先用三级分类名，回退二级
    name = CATE_NAMES.get(course.get("cate3")) or CATE_NAMES.get(course["cate2"], "")
    for pat, t in THEME_BY_NAME:
        if re.search(pat, name.lower()):
            return t
    # 再用课程名关键词兜底
    for pat, t in THEME_BY_NAME:
        if re.search(pat, course["name"].lower()):
            return t
    return DEFAULT_THEME


def main():
    ids = {int(a) for a in sys.argv[1:] if a.isdigit()}
    os.makedirs(OUT_DIR, exist_ok=True)
    courses = load_courses_full()
    if ids:
        courses = [c for c in courses if c["id"] in ids]
    if not courses:
        print("未在 SQL 中解析到课程数据，请检查 deploy/mysql/init/*.sql")
        return
    for c in courses:
        path = os.path.join(OUT_DIR, f"{c['id']}.svg")
        with open(path, "w", encoding="utf-8") as f:
            f.write(svg_cover(c))
        print(f"  生成 {path}  <-  {c['name']}")
    print(f"\n完成：共生成 {len(courses)} 张封面 -> {OUT_DIR}")


if __name__ == "__main__":
    main()
