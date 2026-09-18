// =============================================================================
// 教师端 · 我的资料（个人资料页）
// -----------------------------------------------------------------------------
// 读：GET /us/users/me         → UserDetailVO（name/icon/gender/email/qq/intro/province/city/...）
// 写：PUT /us/teachers/profile ← UserDTO（**后端把 id 强制成登录用户**，传别的 id 无效）
//
// ⚠️ 手机号刻意不放进表单：user 表里 username 与 cell_phone 同源，
//    改手机会连带改登录名 —— 资料页不该顺手改掉别人的登录方式。
// =============================================================================
import request from '@/utils/request.js';

const U = '/us';

/**
 * 统一请求：成功返回裸 JSON；失败抛出**服务端的中文原因**。
 * user-service 出错是 HTTP 400 + text/plain，axios 默认只给「status code 400」。
 */
const call = async (config, fallback = '请求失败') => {
  let res;
  try {
    res = await request(config);
  } catch (e) {
    const body = e?.response?.data;
    if (typeof body === 'string' && body.trim()) throw new Error(body.trim());
    throw new Error(e?.message || fallback);
  }
  if (res && typeof res === 'object' && !Array.isArray(res) && 'code' in res) {
    if (Number(res.code) !== 200) throw new Error(res.msg || fallback);
    return res.data;
  }
  return res;
};

/** 读取当前登录讲师的资料 */
export const getMyProfile = () =>
  call({ url: `${U}/users/me`, method: 'get', timeout: 10000 }, '读取资料失败');

/** 保存资料（只提交表单里出现的字段；手机号不动） */
export const saveMyProfile = (data) =>
  call({ url: `${U}/teachers/profile`, method: 'put', data, timeout: 10000 }, '保存失败');
