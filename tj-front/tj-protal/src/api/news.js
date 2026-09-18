// 学术新闻接口封装
// 当前实现：前端静态 JSON 数据源（src/config/news.json）
// 以后后端提供新闻接口时，仅需替换 getNewsList / getNewsById 的实现，页面无需改动
import newsData from '@/config/news.json';

export const getNewsList = () =>
  Promise.resolve({
    code: 200,
    data: {
      updatedAt: newsData.updatedAt,
      categories: newsData.categories,
      items: newsData.items,
    },
  });

export const getNewsById = (id) =>
  Promise.resolve({
    code: 200,
    data: newsData.items.find((it) => it.id === id) || null,
  });
