/**
 * aiSources.js 的独立回归脚本（无测试框架依赖，直接跑即可）：
 *   node src/utils/aiSources.test.mjs
 *
 * 用 data: URL 直接加载被测源文件，因此测的就是线上那一份，不存在副本漂移。
 * 覆盖的是本次修复的 bug：后端推送的 `[[ZX_SOURCES]]` 元数据事件被误当作正文拼接。
 */
import { readFileSync } from 'node:fs';

const SOURCE = new URL('./aiSources.js', import.meta.url);
const sourceCode = readFileSync(SOURCE, 'utf8');
const { parseSourcesEvent, sourceDocNames } = await import(
  'data:text/javascript;base64,' + Buffer.from(sourceCode).toString('base64')
);

// 真实后端帧（curl 抓包自 /ct/chat/stream）
const realPayload = '[[ZX_SOURCES]][{"docName":"知序学堂平台使用指南.md","section":"一、平台简介","snippet":"一、平台简介"},{"docName":"知序学堂平台使用指南.md","section":"三、如何购买课程","snippet":"三、如何购买课程"},{"docName":"知序学堂平台使用指南.md","section":"知序学堂平台使用指南","snippet":"知序学堂平台使用指南"}]';

let pass = 0;
let fail = 0;
const eq = (name, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  console.log((ok ? 'PASS' : 'FAIL') + '  ' + name + '  -> ' + JSON.stringify(got));
  if (ok) pass += 1; else fail += 1;
};

// 1) 具名事件 sources（后端实际形态）
const r1 = parseSourcesEvent({ event: 'sources', data: realPayload });
eq('1 具名事件被识别', r1.isSources, true);
eq('1 解析出 3 条来源', r1.list.length, 3);
eq('1 文档名去重后只剩 1 个', sourceDocNames(r1.list), ['知序学堂平台使用指南.md']);

// 2) 仅靠标记前缀也能识别（兼容未带 event 名的情况）
eq('2 靠 ZX_SOURCES 前缀识别', parseSourcesEvent({ data: realPayload }).isSources, true);

// 3) 普通正文 token 不得被误判、不得被丢弃
eq('3 普通 token 不误判', parseSourcesEvent({ data: '如何购买课程' }).isSources, false);
eq('3 普通 token 不丢弃', parseSourcesEvent({ data: '如何购买课程' }).list.length, 0);

// 4) 空值安全
eq('4 空 data 安全', parseSourcesEvent({ data: '' }).isSources, false);
eq('4 undefined 安全', parseSourcesEvent(undefined).isSources, false);

// 5) [DONE] 与坏 JSON
eq('5 DONE 不误判', parseSourcesEvent({ data: '[DONE]' }).isSources, false);
eq('5 坏 JSON 仍标记为来源(需剔除)', parseSourcesEvent({ event: 'sources', data: '[[ZX_SOURCES]]{坏' }).isSources, true);
eq('5 坏 JSON 返回空列表', parseSourcesEvent({ event: 'sources', data: '[[ZX_SOURCES]]{坏' }).list.length, 0);

// 6) 整条流回放：正文里不能残留标记
const frames = [
  { event: 'sources', data: realPayload },
  { data: '如何' }, { data: '购买' }, { data: '课程' }, { data: '[DONE]' }
];
let body = '';
let cites = [];
for (const frame of frames) {
  const { isSources, list } = parseSourcesEvent(frame);
  if (isSources) { cites = list; continue; }
  if (frame.data === '[DONE]') continue;
  body += frame.data;
}
eq('6 正文无标记残留', body.indexOf('ZX_SOURCES') === -1, true);
eq('6 正文内容正确', body, '如何购买课程');
eq('6 引用已单独收集', sourceDocNames(cites), ['知序学堂平台使用指南.md']);

console.log('\n结果: ' + pass + ' 通过 / ' + fail + ' 失败');
if (fail) process.exit(1);
