/**
 * 后端流式片段的解码工具。
 *
 * 背景
 * ----
 * 后端把每个文本片段做了 **JSON 编码**后再放进 SSE 的 data 字段，原因是 SSE 协议本身
 * 无法安全承载换行与前导空格：
 *
 * 1. `data:` 后紧跟的一个空格会被解析器移除 —— 模型单独输出的空格片段会变成空字符串；
 * 2. 事件内多条 `data:` 行才用 `\n` 拼接，裸换行会被当成「空 data 行」丢弃；
 * 3. 前端解析库按 `data ? data + '\n' + value : value` 拼接，**首个 data 行为空时前导换行整段丢失**，
 *    而模型恰好常把 `\n` 单独作为一个片段输出。
 *
 * 未编码时的症状：模型依次输出 `"##"`、`" "`、`"Java"`，前端只能拼出 `"##Java"`，
 * Markdown 标题/列表/表格全部失效，页面上直接显示 `##`、`-`、`>`、`|` 等原始符号。
 *
 * 编码后片段恒为单行且以 `"` 开头，`JSON.parse` 即可无损还原；
 * 其余 payload（如结束标记 `[DONE]`、引用来源事件）不以 `"` 开头，原样返回。
 */

/** JSON 字符串的第一个字符（双引号）的码点，用于快速判断是否需要解码。 */
const QUOTE = 34;

/**
 * 解码一个 SSE data 片段。
 * @param {string} data fetchEventSource 回调中的 `event.data`
 * @returns {string} 解码后的原文；非 JSON 编码的 payload 原样返回
 */
export const decodeSseData = (data) => {
  if (typeof data !== 'string' || data.length === 0) return data;
  if (data.charCodeAt(0) !== QUOTE) return data;
  try {
    const value = JSON.parse(data);
    return typeof value === 'string' ? value : data;
  } catch (e) {
    // 不是合法 JSON —— 按原文处理，不能让解码失败影响回答正文
    return data;
  }
};

export default decodeSseData;
