"""
知序学堂课程资料构建脚本（batch2：IT互联网分类）

输入：同目录下 courses-batch2-*.json
输出：
  1) ../<mdFile>                       课程知识库 Markdown 文档（供导入 AI 知识库）
  2) ../course-catalog-batch2.sql       课程目录 / 详情 / 小节数 的更新 SQL

目录 id 规则（与 batch1 保持一致）：
  章  id = courseId * 1000 + 章序号 * 10
  节  id = courseId * 1000 + 章序号 * 10 + 节序号
"""

import glob
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.abspath(os.path.join(HERE, ".."))


def sql_str(value):
    """转义为 SQL 字符串字面量。"""
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def build_markdown(course):
    lines = []
    lines.append(f"# {course['name']}")
    lines.append("")
    lines.append("> 本文件为「知序学堂」课程知识库资料，供课程 AI 助教检索与问答使用。")
    lines.append("")
    lines.append("## 课程简介")
    lines.append("")
    lines.append(course["intro"])
    lines.append("")
    lines.append(course["detail"])
    lines.append("")

    for ci, chapter in enumerate(course["chapters"], start=1):
        lines.append("---")
        lines.append("")
        lines.append(f"## {chapter['name']}")
        lines.append("")
        if chapter.get("summary"):
            lines.append(chapter["summary"])
            lines.append("")

        for si, section in enumerate(chapter["sections"], start=1):
            lines.append(f"### {section['name']}")
            lines.append("")
            if section.get("goal"):
                lines.append(f"**学习目标**：{section['goal']}")
                lines.append("")
            if section.get("points"):
                lines.append("**核心知识点**")
                lines.append("")
                for point in section["points"]:
                    lines.append(f"- {point}")
                lines.append("")
            if section.get("example"):
                lines.append("**示例**")
                lines.append("")
                lang = section.get("exampleLang", "text")
                body = "\n".join(section["example"]) if isinstance(section["example"], list) else str(section["example"])
                if lang and lang != "text":
                    lines.append(f"```{lang}")
                    lines.append(body)
                    lines.append("```")
                else:
                    lines.append(body)
                lines.append("")
            if section.get("pitfall"):
                lines.append(f"**易错点**：{section['pitfall']}")
                lines.append("")

    questions = course.get("questions") or []
    if questions:
        lines.append("---")
        lines.append("")
        lines.append("## 综合练习")
        lines.append("")

        singles = [q for q in questions if q.get("type") == "single"]
        bools = [q for q in questions if q.get("type") == "bool"]

        if singles:
            lines.append("### 单项选择题")
            lines.append("")
            for idx, q in enumerate(singles, start=1):
                lines.append(f"**{idx}. {q['stem']}**")
                lines.append("")
                for oi, option in enumerate(q.get("options") or []):
                    lines.append(f"- {chr(65 + oi)}. {option}")
                lines.append("")
                lines.append(f"**答案**：{chr(65 + int(q.get('answer', 0)))}")
                lines.append("")
                lines.append(f"**解析**：{q.get('explanation', '')}")
                lines.append("")
                if q.get("point"):
                    lines.append(f"**对应知识点**：{q['point']}")
                    lines.append("")

        if bools:
            lines.append("### 判断题")
            lines.append("")
            for idx, q in enumerate(bools, start=1):
                lines.append(f"**{idx}. {q['stem']}**")
                lines.append("")
                answer = "正确" if int(q.get("answer", 0)) == 0 else "错误"
                lines.append(f"**答案**：{answer}")
                lines.append("")
                lines.append(f"**解析**：{q.get('explanation', '')}")
                lines.append("")
                if q.get("point"):
                    lines.append(f"**对应知识点**：{q['point']}")
                    lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def build_sql(courses):
    cids = ", ".join(str(c["id"]) for c in courses)
    lines = []
    lines.append("-- 知序学堂课程目录补充（Demo · batch2 · IT互联网分类）")
    lines.append("-- 目标课程：" + " / ".join(f"{c['id']} {c['name']}" for c in courses))
    lines.append("-- 说明：将占位目录替换为与课程知识库文档一致的章节结构。")
    lines.append("-- 回滚参考：.workbuddy/backup/tj_course_backup_20260911.sql")
    lines.append("")
    lines.append("SET NAMES utf8mb4;")
    lines.append("")
    lines.append(f"-- 1. 清除这些课程的旧占位目录")
    lines.append(f"DELETE FROM course_catalogue WHERE course_id IN ({cids});")
    lines.append("")

    for course in courses:
        base = course["id"] * 1000
        rows = []
        for ci, chapter in enumerate(course["chapters"], start=1):
            chapter_id = base + ci * 10
            trailer = 1 if ci == 1 else 0
            rows.append(
                f"({chapter_id}, {sql_str(chapter['name'])}, {trailer}, {course['id']}, 1, 0, 0, 3600, {ci}, 1, 1)"
            )
            for si, section in enumerate(chapter["sections"], start=1):
                section_id = chapter_id + si
                rows.append(
                    f"({section_id}, {sql_str(section['name'])}, {trailer}, {course['id']}, 2, {chapter_id}, 0, 900, {si}, 1, 1)"
                )
        lines.append(f"-- ========== {course['id']} {course['name']} ==========")
        lines.append(
            "INSERT INTO course_catalogue (id, name, trailer, course_id, type, parent_catalogue_id, play_back, media_duration, c_index, creater, updater) VALUES"
        )
        lines.append(",\n".join(rows) + ";")
        lines.append("")

    lines.append("-- 2. 同步更新课程简介与详情")
    for course in courses:
        detail = f"<p>{course['detail']}</p>"
        lines.append(
            f"UPDATE course_content SET course_introduce = {sql_str(course['intro'])}, "
            f"course_detail = {sql_str(detail)} WHERE id = {course['id']};"
        )
    lines.append("")

    lines.append("-- 3. 同步小节数量")
    lines.append(
        "UPDATE course SET section_num = (SELECT COUNT(*) FROM course_catalogue cc "
        f"WHERE cc.course_id = course.id AND cc.type = 2 AND cc.deleted = 0) WHERE id IN ({cids});"
    )
    lines.append("")

    return "\n".join(lines)


def main():
    files = sorted(glob.glob(os.path.join(HERE, "courses-batch2-*.json")))
    if not files:
        raise SystemExit("未找到 courses-batch2-*.json")

    courses = []
    seen = set()
    for path in files:
        with open(path, "r", encoding="utf-8") as fh:
            data = json.load(fh)
        for course in data:
            cid = course["id"]
            if cid in seen:
                print(f"  跳过重复课程 {cid}")
                continue
            seen.add(cid)
            courses.append(course)

    courses.sort(key=lambda c: c["id"])
    print(f"读取 {len(files)} 个内容文件，共 {len(courses)} 门课程")

    total_chapters = 0
    total_sections = 0
    for course in courses:
        md = build_markdown(course)
        md_path = os.path.join(OUT_DIR, course["mdFile"])
        with open(md_path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(md)
        chapters = len(course["chapters"])
        sections = sum(len(ch["sections"]) for ch in course["chapters"])
        total_chapters += chapters
        total_sections += sections
        print(f"  [{course['id']}] {course['name']}  ->  {course['mdFile']} "
              f"({chapters} 章 / {sections} 节 / {len(course.get('questions') or [])} 题, {len(md)} 字符)")

    sql_path = os.path.join(OUT_DIR, "course-catalog-batch2.sql")
    with open(sql_path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(build_sql(courses))

    print(f"\n合计：{len(courses)} 门课，{total_chapters} 章，{total_sections} 节")
    print(f"SQL 已写入：{sql_path}")


if __name__ == "__main__":
    main()
