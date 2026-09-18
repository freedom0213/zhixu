/**
 * 学术资讯抓取脚本 v2 —— 中文源优先 + ScienceDaily 英文补充 → src/config/news.json
 *
 * 用法：node tools/fetch-news.mjs
 * 说明：知序学堂不实时爬取；由本脚本手动/定时运行生成静态数据快照，
 *       前端读取快照展示。后续后端定时任务上线后，仅需替换 api/news.js 实现。
 *
 * 中文源（优先展示）：
 *   IT之家   → IT互联网（60条）
 *   Solidot  → 科学（20条）
 *   雷锋网   → AI（20条）
 *   InfoQ    → 计算机（20条）
 *   少数派   → IT互联网（10条）
 * 英文补充源：
 *   ScienceDaily RSS → 科学/生命科学/AI/计算机（标注 lang=en）
 */
import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, '../src/config/news.json');
const TARGET_TOTAL = 200;

const CN_SOURCES = [
  { name: 'IT之家', url: 'https://www.ithome.com/rss/', category: 'IT互联网', lang: 'zh', per: 60 },
  { name: 'Solidot', url: 'https://www.solidot.org/index.rss', category: '科学', lang: 'zh', per: 20 },
  { name: '雷峰网', url: 'https://www.leiphone.com/feed', category: 'AI', lang: 'zh', per: 20 },
  { name: 'InfoQ中文', url: 'https://www.infoq.cn/feed', category: '计算机', lang: 'zh', per: 20 },
  // 注：少数派 RSS 的 description 为站点通用文案而非文章正文，已剔除
];

const SD_CHANNELS = [
  { url: 'https://www.sciencedaily.com/rss/computers_math/artificial_intelligence.xml', category: 'AI' },
  { url: 'https://www.sciencedaily.com/rss/computers_math/computer_science.xml', category: '计算机' },
  { url: 'https://www.sciencedaily.com/rss/computers_math/information_technology.xml', category: '计算机' },
  { url: 'https://www.sciencedaily.com/rss/health_medicine/genes.xml', category: '生命科学' },
  { url: 'https://www.sciencedaily.com/rss/health_medicine/human_biology.xml', category: '生命科学' },
  { url: 'https://www.sciencedaily.com/rss/matter_energy/physics.xml', category: '科学' },
  { url: 'https://www.sciencedaily.com/rss/matter_energy/quantum_computing.xml', category: '科学' },
];
const SD_PER_CHANNEL = 14;

// 过滤低相关内容（兜底）
const BAN_WORDS_ZH = ['明星', '综艺', '电视剧', '股市', '彩票', '游戏发售', '评测赢好礼'];
const BAN_WORDS_EN = ['game', 'celebrity', 'movie', 'sport', 'gossip', 'TV show'];

// ---------- XML / HTML 工具 ----------
const decode = (s = '') =>
  s.replace(/<!\[CDATA\[|\]\]>/g, '')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&nbsp;/g, ' ')
    .replace(/&#x[0-9a-f]+;/gi, '')
    .trim();

const stripHtml = (s = '') =>
  decode(s)
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(p|div|li|h[1-6])>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n\s*\n+/g, '\n')
    .trim();

const pick = (xml, tag) => {
  const m = xml.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`));
  return m ? decode(m[1]) : '';
};

// ---------- 要点提取（规则式 AI 摘要） ----------
// 取正文首段首句 + 最长的一句作为要点，避免只给一句的"摘要太短"问题
function extractPoints(body, lang) {
  const text = body.replace(/\n/g, ' ');
  const splitRe = lang === 'zh' ? /(?<=[。！？；])/ : /(?<=[.!?])\s+/;
  const sents = text.split(splitRe).map((s) => s.trim()).filter((s) => s.length > 12);
  if (!sents.length) return '';
  const first = sents[0];
  const longest = sents.slice(1).sort((a, b) => b.length - a.length)[0] || '';
  const parts = [first];
  if (longest && longest !== first && parts.join('').length < 180) parts.push(longest);
  return parts.join(lang === 'zh' ? '' : ' ');
}

// ---------- 拉取 ----------
const seen = new Set();
const items = [];
const shortId = (s) => Buffer.from(s).toString('base64url').replace(/[-_]/g, '').slice(-16);

async function fetchXml(url) {
  const res = await fetch(url, { headers: { 'User-Agent': 'zhixu-news-bot/2.0', 'Accept': '*/*' } });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}

// 通用 RSS2.0 解析（中文源）
for (const src of CN_SOURCES) {
  try {
    const xml = await fetchXml(src.url);
    const entries = xml.split('<item').slice(1);
    let count = 0;
    for (const e of entries) {
      if (count >= src.per) break;
      const title = pick(e, 'title');
      let link = pick(e, 'link');
      if (!link) {
        const m = e.match(/<link[^>]*href="([^"]+)"/);
        link = m ? m[1] : '';
      }
      if (!title || !link || seen.has(link)) continue;
      if (BAN_WORDS_ZH.some((w) => title.includes(w))) continue;
      seen.add(link);
      const body = stripHtml(pick(e, 'description') || pick(e, 'content:encoded'));
      const pubDate = pick(e, 'pubDate') || pick(e, 'published') || '';
      let date = new Date(pubDate || Date.now());
      if (isNaN(date)) date = new Date();
      // IT之家等大众源按关键词细分到 AI/计算机
      let category = src.category;
      if (src.name === 'IT之家') {
        const t = title;
        if (/AI|大模型|GPT|智能|芯片|算力/.test(t)) category = 'AI';
        else if (/程序员|开发|代码|开源|编程|系统|软件|Windows|iOS|Android/.test(t)) category = '计算机';
      }
      items.push({
        id: 'cn-' + shortId(link),
        title,
        summary: body.slice(0, 160) || title,
        body: body.slice(0, 2000),
        aiSummary: extractPoints(body, 'zh'),
        source: src.name,
        category,
        lang: src.lang,
        date: date.toISOString().slice(0, 10),
        url: link,
      });
      count++;
    }
    console.log(`[ok] ${src.name}: ${count} 条`);
  } catch (err) {
    console.warn(`[fail] ${src.name}: ${err.message}`);
  }
}

// ScienceDaily 英文补充（若已达 80% 目标则跳过，保证中文优先）
const cnCount = items.length;
if (cnCount < TARGET_TOTAL * 0.8) {
  for (const ch of SD_CHANNELS) {
    try {
      const xml = await fetchXml(ch.url);
      const entries = xml.split('<item>').slice(1);
      let count = 0;
      for (const e of entries) {
        if (count >= SD_PER_CHANNEL) break;
        const title = pick(e, 'title');
        const link = pick(e, 'link');
        if (!title || !link || seen.has(link)) continue;
        if (BAN_WORDS_EN.some((w) => title.toLowerCase().includes(w))) continue;
        seen.add(link);
        const body = stripHtml(pick(e, 'description'));
        const pubDate = pick(e, 'pubDate') || '';
        let date = new Date(pubDate || Date.now());
        if (isNaN(date)) date = new Date();
        items.push({
          id: 'sd-' + shortId(link),
          title,
          summary: body.slice(0, 240) || title,
          body: body.slice(0, 2400),
          aiSummary: extractPoints(body, 'en'),
          source: 'ScienceDaily',
          category: ch.category,
          lang: 'en',
          date: date.toISOString().slice(0, 10),
          url: link,
        });
        count++;
      }
      console.log(`[ok] ScienceDaily/${ch.category}: ${count} 条`);
    } catch (err) {
      console.warn(`[fail] ScienceDaily/${ch.category}: ${err.message}`);
    }
  }
}

// 中文优先：先按 lang 排（zh 在前），再按时间从新到旧
items.sort((a, b) => {
  if (a.lang !== b.lang) return a.lang === 'zh' ? -1 : 1;
  return a.date < b.date ? 1 : -1;
});

const finalItems = items.slice(0, TARGET_TOTAL);
const cats = [...new Set(finalItems.map((i) => i.category))];

const out = {
  updatedAt: new Date().toISOString().slice(0, 10),
  source: '多源聚合静态快照：IT之家/Solidot/雷峰网/InfoQ/少数派（中文优先）+ ScienceDaily（英文补充），由 tools/fetch-news.mjs 生成',
  categories: ['全部', ...cats],
  items: finalItems,
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(out, null, 2), 'utf-8');
const zhN = finalItems.filter((i) => i.lang === 'zh').length;
console.log(`✓ 已生成 ${OUT}，共 ${finalItems.length} 条（中文 ${zhN} / 英文 ${finalItems.length - zhN}）`);
console.log('分类分布:', JSON.stringify(Object.entries(finalItems.reduce((m, i) => ((m[i.category] = (m[i.category] || 0) + 1), m), {}))));
