// =============================================================================
// 签到标记解析（P28）
// -----------------------------------------------------------------------------
// `GET /ls/sign-records` 返回的**不是**签到记录对象数组，而是
// 「本月每天的打卡标记」：长度 = 本月天数，下标 0 = 1 号，值为 0 / 1。
//
//   实际返回示例：[0,0,0,0,0,0,0,0,0,0,0,0,1,0,0,0,0,1,1,1,1,1,1,0,0,1,0,0,0,0]
//
// ⚠️ 以前前端把它当对象数组用（读 `r.createTime`）——数字上没有这个字段，
//    `moment(1)` 会把"1"当成时间戳毫秒解析、结果落在今天附近，
//    于是**每个标记都被算成"今天"** → 连续打卡天数恒为 1。
//
// 返回：已打卡日期的 `Set<'YYYY-MM-DD'>`（**忽略未来日期**：本月的脏位或位序错位
// 不该被当成本月已打卡，否则连续天数会算出一个不可能的值）。
// =============================================================================
import moment from 'moment';

export function parseSignMarks(marks) {
  const set = new Set();
  const today = moment().format('YYYY-MM-DD');
  const base = moment();
  (Array.isArray(marks) ? marks : []).forEach((v, i) => {
    if (!v) return;
    const day = base.clone().date(i + 1).format('YYYY-MM-DD');
    if (day <= today) set.add(day);
  });
  return set;
}

/** 从打卡日期集合算「连续打卡天数」：今天没打就从昨天起算 */
export function calcStreak(signedDays) {
  if (!signedDays || !signedDays.size) return 0;
  let d = moment();
  if (!signedDays.has(d.format('YYYY-MM-DD'))) d = d.subtract(1, 'days');
  let n = 0;
  while (signedDays.has(d.format('YYYY-MM-DD'))) {
    n += 1;
    d = d.subtract(1, 'days');
  }
  return n;
}
