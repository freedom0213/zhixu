/**
 * AI 流式回答「引用来源」解析工具。
 *
 * 背景：后端 com.zhixu.ai.AiController 在首个 token 之前，会额外推送一条
 * 具名 SSE 事件 `sources`，其 data 形如 `[[ZX_SOURCES]][{"docName":"..."}]`。
 * 该事件是「元数据」而非正文，前端必须单独解析并剔除，否则标记会原样显示在回答里。
 *
 * 之前这段解析逻辑在 AiTutor.vue 里写对了一次，但 GlobalAssistant.vue 和 ai.vue
 * 各自又写了一份且漏了处理，导致 `[[ZX_SOURCES]]...` 泄漏进聊天气泡。
 * 这里统一收敛为单一实现，三个助手共用。
 */

// 与后端 AiController 的 SOURCES_EVENT / SOURCES_MARKER 常量保持一致
export const SOURCES_EVENT = 'sources';
export const SOURCES_MARKER = '[[ZX_SOURCES]]';

/**
 * 判断一个 SSE 事件是否为「引用来源」，是则解析出列表。
 * 兼容两种形态：具名事件 `event: sources`，或 data 以 `[[ZX_SOURCES]]` 开头。
 * 解析失败不抛错，只返回空列表 —— 引用标注失败不应影响正文回答。
 *
 * @param {{event?: string, data?: string}} event fetchEventSource 的 message 事件
 * @returns {{isSources: boolean, list: Array}} isSources=true 表示该事件应被剔除、不作为正文拼接
 */
export const parseSourcesEvent = (event) => {
  const data = event && event.data;
  if (!data) return { isSources: false, list: [] };
  const byName = event.event === SOURCES_EVENT;
  const byMarker = data.indexOf(SOURCES_MARKER) === 0;
  if (!byName && !byMarker) return { isSources: false, list: [] };

  const payload = byMarker ? data.slice(SOURCES_MARKER.length) : data;
  try {
    const list = JSON.parse(payload);
    return { isSources: true, list: Array.isArray(list) ? list : [] };
  } catch (e) {
    return { isSources: true, list: [] };
  }
};

/**
 * 从来源列表中提取去重后的文档名（需求：参考资料只显示 md 文档名即可）。
 * @param {Array<{docName?: string}>} list
 * @returns {string[]}
 */
export const sourceDocNames = (list) => {
  const names = [];
  const seen = new Set();
  for (const item of (list || [])) {
    const name = item && item.docName;
    if (name && !seen.has(name)) {
      seen.add(name);
      names.push(name);
    }
  }
  return names;
};
