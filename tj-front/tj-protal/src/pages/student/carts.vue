<!--
 * 我的购物车（/student/carts）
 * 复用老项目 /pay/carts 的接口逻辑（getCarts / delCarts / 去结算跳 /pay/settlement），
 * 视觉换成学员端新风格：圆角 18 卡片 + 发丝线 + #0066CC 主色。
 * 数据：getCarts() → [{id, courseId, courseName, coverUrl, nowPrice, price, expired}]（价格单位：分）
 -->
<template>
  <div class="ct">
    <section class="s-card cartCard" v-loading="loading">
      <template v-if="carts.length">
        <!-- 表头 -->
        <div class="cartHead">
          <label class="checkAll">
            <input
              type="checkbox"
              :checked="checkAll"
              :indeterminate="isIndeterminate"
              aria-label="全选"
              @change="toggleAll"
            />
            <span>全选</span>
          </label>
          <span class="cartHead__course">课程</span>
          <span class="cartHead__price">价格</span>
          <span class="cartHead__ops">操作</span>
        </div>

        <!-- 行 -->
        <div v-for="item in carts" :key="item.id" class="cartRow" :class="{ 'is-expired': item.expired }">
          <label class="cartRow__check">
            <input
              type="checkbox"
              :checked="checkedList.includes(item.id)"
              :disabled="item.expired"
              :aria-label="`选择 ${item.courseName}`"
              @change="toggleOne(item.id)"
            />
          </label>
          <img class="cartRow__cover" :src="item.coverUrl" :alt="item.courseName" @click="goDetail(item.courseId)" />
          <div class="cartRow__info">
            <p class="cartRow__name" @click="goDetail(item.courseId)">
              {{ item.courseName }}<span v-if="item.expired" class="cartRow__expiredTag">（已失效）</span>
            </p>
            <p v-if="item.nowPrice < item.price" class="cartRow__drop">
              比加入时便宜了 ¥{{ fenToYuan(item.price - item.nowPrice) }}
            </p>
          </div>
          <span class="cartRow__price">¥{{ fenToYuan(item.nowPrice) }}</span>
          <button class="cartRow__del" type="button" @click="removeItems([item.id])">删除</button>
        </div>

        <!-- 结算栏 -->
        <div class="cartBar">
          <button class="cartBar__delSel" type="button" :disabled="!checkedList.length" @click="removeItems(checkedList)">
            删除所选
          </button>
          <div class="cartBar__right">
            <span class="cartBar__count">
              已选 <b>{{ checkedList.length }}</b> 门课程
            </span>
            <span class="cartBar__total">
              合计：<b>¥{{ fenToYuan(totalAmount) }}</b>
            </span>
            <button class="cartBar__settle" type="button" :disabled="!checkedList.length" @click="goSettlement">
              去结算
            </button>
          </div>
        </div>
      </template>

      <!-- 空态 -->
      <div v-else-if="!loading" class="emptyBox">
        <p class="emptyBox__title">购物车是空的</p>
        <p class="emptyBox__desc">去课程中心逛逛，把想学的课程加入购物车。</p>
        <button class="emptyBox__btn" type="button" @click="router.push('/student/courses')">去课程中心</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getCarts, delCarts } from '@/api/order.js';

const router = useRouter();

const carts = ref([]);
const loading = ref(false);
const checkedList = ref([]);

const fenToYuan = (fen) => ((Number(fen) || 0) / 100).toFixed(2);

const loadCarts = async () => {
  loading.value = true;
  try {
    const res = await getCarts();
    if (res?.code === 200 && Array.isArray(res.data)) {
      carts.value = res.data;
      // 清掉已不在购物车里的勾选项
      const ids = new Set(res.data.map((c) => c.id));
      checkedList.value = checkedList.value.filter((id) => ids.has(id));
    }
  } catch (e) {
    carts.value = [];
  } finally {
    loading.value = false;
  }
};

onMounted(loadCarts);

// 可勾选项（失效课程不可选）
const selectable = computed(() => carts.value.filter((c) => !c.expired));

const checkAll = computed(
  () => selectable.value.length > 0 && checkedList.value.length === selectable.value.length
);
const isIndeterminate = computed(
  () => checkedList.value.length > 0 && checkedList.value.length < selectable.value.length
);

const totalAmount = computed(() =>
  carts.value
    .filter((c) => checkedList.value.includes(c.id))
    .reduce((sum, c) => sum + (Number(c.nowPrice) || 0), 0)
);

const toggleAll = () => {
  checkedList.value = checkAll.value ? [] : selectable.value.map((c) => c.id);
};

const toggleOne = (id) => {
  const i = checkedList.value.indexOf(id);
  if (i > -1) checkedList.value.splice(i, 1);
  else checkedList.value.push(id);
};

const removeItems = (ids) => {
  if (!ids.length) {
    ElMessage.warning('请先选择要删除的课程');
    return;
  }
  delCarts(ids)
    .then((res) => {
      if (res?.code === 200) {
        ElMessage.success('已删除');
        loadCarts();
        window.dispatchEvent(new CustomEvent('cart:changed'));
      } else {
        ElMessage.error(res?.msg || '删除失败');
      }
    })
    .catch(() => ElMessage.error('删除失败，请稍后重试'));
};

const goSettlement = () => {
  if (!checkedList.value.length) {
    ElMessage.warning('请选择要结算的课程');
    return;
  }
  const list = carts.value.filter((c) => checkedList.value.includes(c.id));
  // 结算页仍为既有页面（下单链路未变）
  router.push({ path: '/student/settlement', query: { courseIds: list.map((c) => c.courseId).join() } });
};

const goDetail = (courseId) => {
  if (courseId) router.push({ path: '/student/courses/detail', query: { id: courseId } });
};
</script>

<style lang="scss" scoped>
.ct {
  display: flex;
  flex-direction: column;
}

.cartCard {
  padding: 8px 24px 20px;
  border-radius: 18px;
}

// ---- 表头 ----
.cartHead {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 110px 70px;
  align-items: center;
  gap: 16px;
  padding: 14px 0;
  font-size: 12px;
  color: #86868b;
  border-bottom: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));
}
.checkAll {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #1d1d1f;

  input { width: 16px; height: 16px; accent-color: #0066cc; cursor: pointer; }
}

// ---- 行 ----
.cartRow {
  display: grid;
  grid-template-columns: 64px 148px minmax(0, 1fr) 110px 70px;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--s-divider, rgba(0, 0, 0, 0.06));

  &.is-expired {
    .cartRow__cover, .cartRow__name { opacity: 0.55; }
  }

  &__check {
    display: flex;
    align-items: center;

    input { width: 16px; height: 16px; accent-color: #0066cc; cursor: pointer; }
    input:disabled { cursor: not-allowed; }
  }
  &__cover {
    width: 148px;
    height: 84px;
    border-radius: 10px;
    object-fit: cover;
    background: #eef1f6;
    cursor: pointer;
  }
  &__info { min-width: 0; }
  &__name {
    margin: 0 0 6px;
    font-size: 15px;
    font-weight: 600;
    line-height: 22px;
    color: #1d1d1f;
    cursor: pointer;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;

    &:hover { color: #0066cc; }
  }
  &__expiredTag {
    font-weight: 400;
    color: #86868b;
  }
  &__drop {
    margin: 0;
    font-size: 12px;
    line-height: 17px;
    color: #ff3b30;
  }
  &__price {
    font-size: 16px;
    font-weight: 700;
    color: #ff3b30;
    font-variant-numeric: tabular-nums;
  }
  &__del {
    height: 30px;
    padding: 0 12px;
    border: 0;
    border-radius: 15px;
    background: #f5f5f7;
    color: #6e6e73;
    font-size: 12px;
    font-family: inherit;
    cursor: pointer;
    transition: background-color 0.16s ease, color 0.16s ease;

    &:hover { background: #ffeceb; color: #ff3b30; }
  }
}

// ---- 结算栏 ----
.cartBar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 18px;

  &__delSel {
    height: 36px;
    padding: 0 18px;
    border: 0;
    border-radius: 18px;
    background: #f5f5f7;
    color: #6e6e73;
    font-size: 13px;
    font-family: inherit;
    cursor: pointer;

    &:hover:not(:disabled) { background: #ffeceb; color: #ff3b30; }
    &:disabled { opacity: 0.5; cursor: not-allowed; }
  }
  &__right {
    display: flex;
    align-items: center;
    gap: 20px;
  }
  &__count {
    font-size: 13px;
    color: #6e6e73;

    b { color: #1d1d1f; font-variant-numeric: tabular-nums; }
  }
  &__total {
    font-size: 14px;
    color: #1d1d1f;

    b {
      font-size: 20px;
      font-weight: 700;
      color: #ff3b30;
      font-variant-numeric: tabular-nums;
    }
  }
  &__settle {
    height: 42px;
    padding: 0 32px;
    border: 0;
    border-radius: 21px;
    background: #0066cc;
    color: #fff;
    font-size: 15px;
    font-weight: 600;
    font-family: inherit;
    cursor: pointer;
    transition: background-color 0.16s ease, opacity 0.16s ease;

    &:hover:not(:disabled) { background: #0071e3; }
    &:disabled { background: #d8d9de; cursor: not-allowed; }
  }
}

// ---- 空态 ----
.emptyBox {
  padding: 72px 0 64px;
  text-align: center;

  &__title {
    margin: 0 0 8px;
    font-size: 16px;
    font-weight: 600;
    color: #1d1d1f;
  }
  &__desc {
    margin: 0 0 22px;
    font-size: 13px;
    color: #86868b;
  }
  &__btn {
    height: 38px;
    padding: 0 26px;
    border: 0;
    border-radius: 19px;
    background: #0066cc;
    color: #fff;
    font-size: 14px;
    font-weight: 500;
    font-family: inherit;
    cursor: pointer;

    &:hover { background: #0071e3; }
  }
}

@media (max-width: 1180px) {
  .cartRow { grid-template-columns: 40px 120px minmax(0, 1fr) 90px 60px; gap: 10px; }
  .cartRow__cover { width: 120px; height: 68px; }
  .cartHead { grid-template-columns: 40px minmax(0, 1fr) 90px 60px; gap: 10px; }
}
</style>
