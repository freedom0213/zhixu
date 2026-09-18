/**
 * markdown.js 的独立回归脚本（无测试框架依赖，直接跑即可）：
 *   node src/utils/markdown.test.mjs
 *
 * 用 data: URL 加载被测源文件（markdown.js 无外部依赖，因此可被直接加载），
 * 保证测的就是线上那一份。配合 markdown-it 校验「规范化后能否真的渲染成 HTML」。
 *
 * 覆盖的 bug：大模型输出的紧凑 Markdown（`##X` / `-X` / `1.X` / `>X` / `||X`）
 * 不被 CommonMark 识别，导致页面直接显示原始符号。
 */
import { readFileSync } from 'node:fs';
import MarkdownIt from 'markdown-it';

const SOURCE = new URL('./markdown.js', import.meta.url);
const sourceCode = readFileSync(SOURCE, 'utf8');
const { normalizeMarkdown } = await import(
  'data:text/javascript;base64,' + Buffer.from(sourceCode).toString('base64')
);

const md = new MarkdownIt({ breaks: true });
const html = (raw) => md.render(normalizeMarkdown(raw));

let pass = 0;
let fail = 0;
const ok = (name, cond, extra) => {
  console.log((cond ? 'PASS' : 'FAIL') + '  ' + name + (extra ? '  -> ' + extra : ''));
  if (cond) pass += 1; else fail += 1;
};

// ===== 1) 标题：##Java学习路线 -> 二级标题 =====
{
  const out = html('##Java学习路线（知序学堂当前在售课程版）');
  ok('1 标题缺空格被修复', out.includes('<h2>') && !out.includes('##Java'), out.trim().slice(0, 60));
}

// ===== 2) 无序列表：-《课》 -> <li> =====
{
  const out = html('-《计算机网络：从TCP/IP到HTTP》免费，9小节\n-《Java集合与并发编程》免费，8小节');
  ok('2 无序列表缺空格被修复', out.includes('<li>') && out.includes('计算机网络'), out.trim().slice(0, 60));
}

// ===== 2b) 分隔线与加粗不能被误伤 =====
{
  const hr = html('---');
  ok('2b 分隔线 --- 不被当成列表', hr.includes('<hr>'), hr.trim());
  const bold = html('**加粗**文字');
  ok('2b 行首 **加粗** 不被误伤', bold.includes('<strong>'), bold.trim().slice(0, 40));
}

// ===== 3) 有序列表：1.计算机 -> <ol><li> =====
{
  const out = html('1.计算机基础与算法打底\n2.Java核心进阶');
  ok('3 有序列表缺空格被修复', out.includes('<ol>') && out.includes('<li>'), out.trim().slice(0, 60));
}

// ===== 3b) 小数不能被误判成有序列表 =====
{
  const out = html('3.14是圆周率');
  ok('3b 行首小数不被当成列表', !out.includes('<ol>') && out.includes('3.14'), out.trim().slice(0, 60));
}

// ===== 4) 引用：>说明 不再吞掉后续内容 =====
{
  const out = html('>说明：当前目录暂无对应课程。\n###推荐顺序\n1.计算机基础');
  ok('4 引用后内容未被吞', out.includes('<h3>') && out.includes('<ol>'), out.trim().slice(0, 80));
  ok('4 引用符号已被解析为 blockquote', out.includes('<blockquote>'), out.trim().slice(0, 60));
}

// ===== 5) 表格：行首多余 | 被修掉 =====
{
  const raw = '|方向|推荐顺序|\n|---|---|\n||Java进阶|《Java集合与并发编程》|\n||Python路线|《Python编程基础》|';
  const out = html(raw);
  const thCount = (out.match(/<th>/g) || []).length;
  const firstTd = (out.match(/<td>([\s\S]*?)<\/td>/) || [])[1] || '';
  ok('5 表格被解析', out.includes('<table>') && thCount === 2, 'th=' + thCount);
  ok('5 首列不再是空单元格', firstTd.trim() === 'Java进阶', JSON.stringify(firstTd.trim()));
}

// ===== 5b) 表格紧跟正文行也能解析 =====
{
  const out = html('下面是推荐组合：\n|方向|课程|\n|---|---|\n|Java|集合与并发|');
  ok('5b 表格前自动补空行', out.includes('<table>') && out.includes('<p>下面是推荐组合：</p>'), out.trim().slice(0, 80));
}

// ===== 5c) 表格内部不被空行拆散 =====
{
  const out = html('|a|b|\n|---|---|\n|c|d|\n|e|f|');
  const rows = (out.match(/<tr>/g) || []).length;
  ok('5c 表格行数保持 3 行', rows === 3, 'rows=' + rows);
}

// ===== 6) 幂等 =====
{
  const raw = '##标题\n>说明\n-《课》\n1.第一\n|a|b|\n|---|---|\n|c|d|';
  const once = normalizeMarkdown(raw);
  const twice = normalizeMarkdown(once);
  ok('6 幂等（跑两次结果一致）', once === twice);
}

// ===== 7) 边界：空值 / 已规范文本不破坏 =====
{
  ok('7 null 安全', normalizeMarkdown(null) === '');
  ok('7 undefined 安全', normalizeMarkdown(undefined) === '');
  const good = '## 标题\n\n- 列表项\n\n1. 有序项\n\n> 引用\n';
  ok('7 已规范文本保持不变', normalizeMarkdown(good) === good, JSON.stringify(normalizeMarkdown(good)));
}

console.log('\n结果: ' + pass + ' 通过 / ' + fail + ' 失败');
process.exit(fail ? 1 : 0);
