/**
 * sseData.js 的独立回归脚本（无测试框架依赖，直接跑即可）：
 *   node src/utils/sseData.test.mjs
 *
 * 用 data: URL 加载被测源文件（sseData.js 无外部依赖），保证测的就是线上那一份。
 * 同时**直接引用前端真实使用的 SSE 解析库**（@microsoft/fetch-event-source 的 parse.js），
 * 端到端复现「SSE 传输 → 解析 → 前端拼接」的全过程，因此结论与线上一致。
 *
 * 覆盖的 bug：模型流式输出的换行与前导空格被 SSE 协议吞掉，导致回答塌成一行、
 * Markdown 全部失效，页面直接显示 `##`、`-`、`>`、`|` 等原始符号。
 */
import { readFileSync } from 'node:fs';
import { getLines, getMessages } from '@microsoft/fetch-event-source/lib/esm/parse.js';

const SOURCE = new URL('./sseData.js', import.meta.url);
const sourceCode = readFileSync(SOURCE, 'utf8');
const { decodeSseData } = await import(
  'data:text/javascript;base64,' + Buffer.from(sourceCode).toString('base64')
);

let pass = 0;
let fail = 0;
const ok = (name, cond, extra) => {
  console.log((cond ? 'PASS' : 'FAIL') + '  ' + name + (extra !== undefined ? '  -> ' + JSON.stringify(extra) : ''));
  if (cond) pass += 1; else fail += 1;
};

/** 模拟 Spring SseEmitter 的输出：`data:` + payload + 空行 */
const wire = (payloads) => payloads.map((p) => 'data:' + p + '\n\n').join('');

/** 用真实解析库把一段 SSE 报文解析成消息数组 */
const parseSse = (raw) => {
  const messages = [];
  const handleLine = getMessages(() => {}, () => {}, (msg) => messages.push(msg));
  const handleChunk = getLines((line, fieldLength) => handleLine(line, fieldLength));
  handleChunk(new TextEncoder().encode(raw));
  return messages;
};

// 模型真实输出的片段序列：`##` 与 `Java` 之间的空格是独立片段，换行也是独立片段
const TOKENS = ['##', ' ', 'Java', '学习路线', '\n', '-', ' ', '《Java集合与并发编程》', '\n', '1.', '基础'];

// ===== 1) 旧写法（不编码）：复现 bug =====
{
  const merged = parseSse(wire(TOKENS))
    .filter((m) => m.data !== '[DONE]')
    .map((m) => m.data)
    .join('');
  ok('1 未编码时前导空格被吞（复现 bug）', merged.startsWith('##Java'), merged);
  ok('1 未编码时换行丢失', merged.indexOf('\n') === -1, merged);
}

// ===== 2) 新写法（JSON 编码）：修复 =====
{
  const merged = parseSse(wire(TOKENS.map((t) => JSON.stringify(t))))
    .map((m) => decodeSseData(m.data))
    .join('');
  const expected = TOKENS.join('');
  ok('2 JSON 编码后内容完全还原', merged === expected, merged);
  ok('2 换行被保留', merged.indexOf('\n') !== -1);
  ok('2 前导空格被保留', merged.startsWith('## Java'), merged);
}

// ===== 3) 空行 / 纯换行片段（解析库会丢前导空行，必须靠编码兜住）=====
{
  const tokens = ['\n', '\n', '-', ' ', '第一项'];
  const merged = parseSse(wire(tokens.map((t) => JSON.stringify(t))))
    .map((m) => decodeSseData(m.data))
    .join('');
  ok('3 单独输出换行的片段不丢失', merged === '\n\n- 第一项', merged);
}

// ===== 4) 单独的空格片段 =====
{
  const merged = parseSse(wire(['a', ' ', 'b'].map((t) => JSON.stringify(t))))
    .map((m) => decodeSseData(m.data))
    .join('');
  ok('4 单独的空格片段不丢失', merged === 'a b', merged);
}

// ===== 5) 结束标记 [DONE] 与引用来源事件不受影响 =====
{
  const merged = parseSse(wire(['x', '[DONE]'])).map((m) => decodeSseData(m.data));
  ok('5 [DONE] 原样返回', merged[1] === '[DONE]', merged);
  const marker = '[[ZX_SOURCES]][{"docName":"a.md"}]';
  ok('5 引用来源 payload 不被误解码', decodeSseData(marker) === marker);
}

// ===== 6) 健壮性：非法 JSON / 空值 / 非字符串 =====
{
  ok('6 非法 JSON 原样返回', decodeSseData('"未闭合') === '"未闭合');
  ok('6 非引号开头原样返回', decodeSseData('普通文本') === '普通文本');
  ok('6 空串安全', decodeSseData('') === '');
  ok('6 null 安全', decodeSseData(null) === null);
  ok('6 undefined 安全', decodeSseData(undefined) === undefined);
  ok('6 JSON 数组不被当成片段', decodeSseData('[1,2]') === '[1,2]');
}

console.log('\n结果: ' + pass + ' 通过 / ' + fail + ' 失败');
process.exit(fail ? 1 : 0);
