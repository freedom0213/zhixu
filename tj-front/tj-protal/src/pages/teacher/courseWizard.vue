<!--
 * 教师端 · 新建课程五步向导（设计稿 A1 + A2 + A3 + A5 + A6）
 * -----------------------------------------------------------------------------
 * 与出卷向导同一套骨架：左步骤导航 + 右卡片，每一步单独保存，随时退出再回来。
 * 与设计稿的两处**有意偏离**（都已确认过的既定决策）：
 *   ① A5 的题型分布只会有「单选 / 多选」—— 题库本期只做选择题，判断 / 主观不进表单；
 *   ② A2 的拖动排序降级为 ↑↓ 按钮 —— 键盘可达、无头可测，语义不变。
 * 视频上传是「UI + 本地模拟进度」，真上传待后端签发腾讯云 VOD 凭证（契约 §13）。
-->
<template>
  <div class="cw">
    <!-- 页头 -->
    <div class="cw-head">
      <div class="cw-head__text">
        <button class="q-btn" type="button" @click="goBack">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M9.6 3.4 5.2 8l4.4 4.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          返回我的课程
        </button>
        <h2 class="s-h2">{{ isEdit ? '编辑课程' : '新建课程' }}</h2>
        <p class="cw-head__sub">共 {{ STEPS.length }} 步 · 当前第 {{ step }} 步「{{ STEPS[step - 1].label }}」</p>
      </div>
      <div class="cw-head__actions">
        <button class="q-btn" type="button" :disabled="busy" @click="saveCurrent">存草稿</button>
        <button v-if="step < STEPS.length" class="q-btn q-btn--primary" type="button" @click="goStep(step + 1)">下一步</button>
      </div>
    </div>

    <p v-if="loading" class="cw-state">加载中…</p>

    <div v-else class="cw-body">
      <!-- 左：步骤导航 -->
      <aside class="cw-nav s-card">
        <button
          v-for="(s, i) in STEPS"
          :key="s.no"
          class="cw-nav__item"
          :class="{ 'is-on': step === i + 1, 'is-done': step > i + 1 }"
          type="button"
          @click="goStep(i + 1)"
        >
          <span class="cw-nav__dot">{{ step > i + 1 ? '✓' : s.no }}</span>
          <span class="cw-nav__label">{{ s.label }}</span>
        </button>
        <div class="cw-nav__hr"></div>
        <p class="cw-nav__cap">完成度</p>
        <p class="cw-nav__pct">{{ percent }}%</p>
        <div class="cw-nav__track"><span class="cw-nav__fill" :style="{ width: percent + '%' }"></span></div>
        <p class="cw-nav__hint">每一步都会单独保存，随时可以退出再回来接着填。</p>
      </aside>

      <!-- 右：内容卡 -->
      <section class="s-card cw-card">
        <!-- ============ ① 基本信息（A1） ============ -->
        <template v-if="step === 1">
          <h3 class="cw-card__title">基本信息</h3>
          <p class="cw-card__desc">带 * 为必填项。填完点右上角「下一步」，本步内容立即保存。</p>
          <div class="cw-hr"></div>

          <div class="cw-field">
            <p class="cw-label">课程名称 *</p>
            <div class="cw-row">
              <input v-model.trim="draft.basic.name" class="cw-input" style="max-width: 548px" type="text" placeholder="例如：Spring Boot 快速上手" aria-label="课程名称" />
              <span v-if="draft.basic.name" class="cw-chip is-ok">名称可用</span>
            </div>
          </div>

          <div class="cw-field">
            <p class="cw-label">课程分类 *</p>
            <div class="cw-row">
              <select v-model="catL1" class="cw-select cw-select--third" aria-label="一级分类" @change="onCatChange(1)">
                <option :value="null">一级分类</option>
                <option v-for="c in l1Options" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
              <select
                v-model="catL2"
                class="cw-select cw-select--third"
                aria-label="二级分类"
                :disabled="!catL1"
                @change="onCatChange(2)"
              >
                <option :value="null">二级分类</option>
                <option v-for="c in l2Options" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
              <select
                v-model="catL3"
                class="cw-select cw-select--third"
                aria-label="三级分类"
                :disabled="!catL2"
                @change="onCatChange(3)"
              >
                <option :value="null">三级分类</option>
                <option v-for="c in l3Options" :key="c.id" :value="c.id">{{ c.label || c.name }}</option>
              </select>
              <span v-if="draft.basic.thirdCateId" class="cw-chip is-ok">已选：{{ catPathText }}</span>
              <span v-else-if="catError" class="cw-chip">{{ catError }}</span>
            </div>
            <p class="cw-note">分类取自平台的课程分类树，必须选到三级 —— 学生端按三级分类筛选课程。</p>
          </div>

          <div class="cw-grid3">
            <div class="cw-field">
              <p class="cw-label">课程价格（元）</p>
              <input v-model.number="draft.basic.price" class="cw-input" type="number" min="0" aria-label="课程价格" />
            </div>
            <div class="cw-field">
              <p class="cw-label">有效期</p>
              <select v-model.number="draft.basic.validDays" class="cw-select" aria-label="有效期">
                <option :value="90">90 天</option>
                <option :value="180">180 天</option>
                <option :value="365">365 天</option>
                <option :value="-1">长期有效</option>
              </select>
            </div>
          </div>

          <div class="cw-field">
            <p class="cw-label">课程封面</p>
            <div class="cw-row" style="align-items: flex-start">
              <label class="cw-cover">
                <img v-if="coverUrl" :src="coverUrl" alt="封面预览" />
                <span v-else class="cw-cover__ph">
                  {{ draft.basic.cover?.name ? '已选择：' + draft.basic.cover.name : '＋ 上传封面' }}
                </span>
                <input type="file" accept="image/jpeg,image/png" aria-label="选择封面" @change="onCoverPick" />
              </label>
              <p class="cw-note">支持 JPG / PNG，不超过 2 MB。封面会出现在课程中心与课程详情页。</p>
            </div>
          </div>

          <div class="cw-field">
            <p class="cw-label">课程简介</p>
            <textarea v-model.trim="draft.basic.intro" class="cw-textarea" rows="4" aria-label="课程简介" placeholder="一句话说清这门课讲什么、适合谁、学完能得到什么。"></textarea>
          </div>

          <div class="cw-hr"></div>
          <div class="cw-foot">
            <span class="cw-foot__note">下一步：搭课程目录。章 → 小节两层结构，学生看到的学习路径就是这个顺序。</span>
            <button class="q-btn q-btn--primary" type="button" @click="goStep(2)">下一步</button>
          </div>
        </template>

        <!-- ============ ② 课程目录（A2） ============ -->
        <template v-else-if="step === 2">
          <div class="cw-secHead">
            <div>
              <h3 class="cw-card__title">课程目录</h3>
              <p class="cw-card__desc">章 → 小节两层结构。用 ↑↓ 调整顺序；学生看到的学习路径就是这个顺序。</p>
            </div>
            <button class="q-btn" type="button" @click="addChapter">＋ 添加章</button>
          </div>
          <div class="cw-hr"></div>

          <p v-if="!draft.chapters.length" class="cw-state">还没有章。点右上角「＋ 添加章」开始搭目录。</p>

          <div v-for="(ch, ci) in draft.chapters" :key="ch.id" class="cw-chapter">
            <div class="cw-chapter__head">
              <span class="cw-mini" aria-hidden="true">≡</span>
              <input v-if="editChapterId === ch.id" v-model.trim="ch.title" class="cw-input cw-input--inline" aria-label="章名" @keyup.enter="editChapterId = null" @blur="editChapterId = null" />
              <span v-else class="cw-chapter__title" @click="editChapterId = ch.id">{{ ch.title }}</span>
              <span class="cw-chapter__ops">
                <button class="w-mini" type="button" :disabled="ci === 0" aria-label="上移" @click="moveChapter(ci, -1)">↑</button>
                <button class="w-mini" type="button" :disabled="ci === draft.chapters.length - 1" aria-label="下移" @click="moveChapter(ci, 1)">↓</button>
                <!-- 两步确认：自动保存的向导里误删一章代价太大，单击只标红，再击才删 -->
                <button
                  class="q-act"
                  :class="confirmDelId === ch.id ? 'is-danger' : 'q-act--muted'"
                  type="button"
                  @click="askRemoveChapter(ch)"
                >
                  {{ confirmDelId === ch.id ? '确认删除？' : '删除章' }}
                </button>
              </span>
            </div>

            <div v-for="(s, si) in ch.sections" :key="s.id" class="cw-section">
              <span class="cw-mini" aria-hidden="true">≡</span>
              <span class="cw-section__title">{{ s.title }}</span>
              <span class="cw-section__dur">{{ s.duration || '—' }}</span>
              <span class="cw-chip" :class="s.video.status === 'done' ? 'is-ok' : ''">{{ s.video.status === 'done' ? '已配视频' : '未配视频' }}</span>
              <span class="cw-section__ops">
                <button class="q-act" type="button" @click="renameSection(s)">编辑</button>
                <button class="q-act q-act--muted" type="button" @click="removeSection(ch, si)">删除</button>
              </span>
            </div>

            <div class="cw-section cw-section--add">
              <template v-if="addSecCh === ci">
                <input
                  v-model.trim="newSecTitle"
                  class="cw-input cw-input--inline"
                  style="max-width: 460px"
                  placeholder="小节名称，如 1.5　实战演练"
                  aria-label="新小节名称"
                  @keyup.enter="confirmAddSection(ch)"
                />
                <button class="q-act" type="button" @click="confirmAddSection(ch)">保存</button>
                <button class="q-act q-act--muted" type="button" @click="cancelAddSection">取消</button>
              </template>
              <button v-else class="q-act" type="button" @click="startAddSection(ci)">＋ 添加小节</button>
            </div>
          </div>

          <div class="cw-hr"></div>
          <div class="cw-foot">
            <span class="cw-foot__note">目录会自动保存。下一步给小节配视频。</span>
          </div>
        </template>

        <!-- ============ ③ 课时与视频（A3，A4 的三个状态在行内） ============ -->
        <template v-else-if="step === 3">
          <div class="cw-secHead">
            <div>
              <h3 class="cw-card__title">课时与视频</h3>
              <p class="cw-card__desc">给每个小节配一段视频。上传完成后自动登记时长；试看开关控制游客能看到的部分。</p>
            </div>
            <button class="q-btn" type="button" :disabled="!firstMissing.length" @click="pickBulk">批量上传</button>
            <input ref="bulkInput" type="file" accept="video/*" multiple style="display: none" @change="onBulkPick" />
          </div>
          <div class="cw-hr"></div>

          <div class="cw-table">
            <div class="cw-trow cw-trow--head">
              <span class="cw-tc cw-tc--sec">小节</span>
              <span class="cw-tc cw-tc--video">视频</span>
              <span class="cw-tc cw-tc--dur">时长</span>
              <span class="cw-tc cw-tc--prev">试看</span>
              <span class="cw-tc cw-tc--ops">操作</span>
            </div>
            <div v-for="row in flatSections" :key="row.s.id" class="cw-trow">
              <span class="cw-tc cw-tc--sec">{{ row.s.title }}</span>

              <!-- 状态一：已上传（A4 · 完成） -->
              <span v-if="row.s.video.status === 'done'" class="cw-tc cw-tc--video">
                <span class="cw-thumb" aria-hidden="true">▶</span>
                <span class="cw-videoText">
                  <span class="cw-videoText__name">{{ row.s.video.name }}</span>
                  <span class="cw-videoText__meta">已上传 · {{ row.s.video.sizeMB }} MB</span>
                </span>
              </span>
              <!-- 状态二：上传中（A4 · 进行） -->
              <span v-else-if="row.s.video.status === 'uploading'" class="cw-tc cw-tc--video">
                <span class="cw-videoText">
                  <span class="cw-videoText__name">{{ row.s.video.name }}</span>
                  <span class="cw-track"><span class="cw-track__fill" :style="{ width: row.s.video.progress + '%' }"></span></span>
                </span>
                <em class="cw-pct">{{ row.s.video.progress }}%</em>
              </span>
              <!-- 状态三：未上传（A4 · 待上传） -->
              <span v-else class="cw-tc cw-tc--video">
                <button class="q-btn q-btn--sm" type="button" @click="pickOne(row.s)">上传视频</button>
              </span>

              <span class="cw-tc cw-tc--dur">{{ row.s.duration || '—' }}</span>
              <span class="cw-tc cw-tc--prev">
                <button class="q-act" type="button" @click="togglePreview(row.s)">{{ row.s.preview ? '开' : '关' }}</button>
              </span>
              <span class="cw-tc cw-tc--ops">
                <template v-if="row.s.video.status === 'done'">
                  <button class="q-act" type="button" @click="pickOne(row.s)">替换</button>
                  <button class="q-act q-act--muted" type="button" @click="removeVideo(row.s)">删除</button>
                </template>
                <template v-else-if="row.s.video.status === 'uploading'">
                  <button class="q-act q-act--muted" type="button" @click="cancelUpload(row.s)">取消上传</button>
                </template>
                <span v-else class="cw-cw-muted">—</span>
              </span>
            </div>
          </div>
          <input ref="oneInput" type="file" accept="video/*" style="display: none" @change="onOnePick" />

          <div v-if="missingVideo" class="cw-alert">
            <strong>{{ missingVideo }}</strong> 个小节还没有视频。可以现在补齐，也可以先上架、之后替换（未配视频的小节对学生隐藏）。
          </div>
          <div class="cw-tip">
            上传说明：视频直传云点播，上传完成后自动识别时长与封面。当前为本地模拟进度 —— 真上传依赖后端签发点播凭证（后端待补，见契约 §13）。
          </div>

          <div class="cw-hr"></div>
          <div class="cw-foot">
            <span class="cw-foot__note">下一步：确认讲师并上架。</span>
            <button class="q-btn q-btn--primary" type="button" @click="goStep(4)">下一步</button>
          </div>
        </template>

        <!-- ============ ⑤ 讲师与发布（A6） ============ -->
        <template v-else>
          <h3 class="cw-card__title">讲师与发布</h3>
          <p class="cw-card__desc">
            这门课的主讲是你本人（谁建的课谁署名）。需要协作者就填对方在平台上的账号加进来，然后上架。
          </p>
          <div class="cw-hr"></div>

          <p class="cw-label">授课讲师</p>
          <p v-if="!draft.teachers.length" class="cw-state">还没有讲师。</p>
          <div v-for="t in draft.teachers" :key="t.id" class="cw-teacher">
            <span class="cw-teacher__avatar">{{ (t.name || '?').slice(0, 1) }}</span>
            <span class="cw-teacher__text">
              <span class="cw-teacher__name">
                {{ t.name || `用户 ${t.id}` }}
                <span v-if="isOwner(t)" class="cw-chip is-ok">主讲 · 你</span>
              </span>
              <!-- 老模型只存「职位 + 简介」（course_teacher 无角色列），如实显示，不虚构角色/认证 -->
              <span class="cw-teacher__meta">{{ teacherMeta(t) }}</span>
            </span>
            <button v-if="!isOwner(t)" class="q-act q-act--muted" type="button" @click="removeT(t)">移除</button>
          </div>

          <div class="cw-row" style="margin: 12px 0 0; align-items: flex-start">
            <input
              v-model.trim="newTeacherAccount"
              class="cw-input"
              style="max-width: 320px"
              type="text"
              placeholder="协作者的平台账号（用户名 / 手机号）"
              aria-label="协作者账号"
              @keyup.enter="addT"
            />
            <button class="q-btn" type="button" :disabled="!newTeacherAccount || addingTeacher" @click="addT">
              {{ addingTeacher ? '查询中…' : '添加协作者' }}
            </button>
          </div>
          <p class="cw-note" style="margin-top: 8px">
            按账号把对方关联进这门课（不是选名单）。对方必须是平台上的教师账号 —— 查不到或身份不符会明确提示。
          </p>

          <div class="cw-hr" style="margin-top: 20px"></div>
          <p class="cw-label">上架前校验</p>
          <div v-for="(c, k) in checkRows" :key="k" class="cw-check">
            <span class="cw-check__dot" :class="c.ok ? 'is-ok' : c.warn ? 'is-warn' : 'is-bad'"></span>
            <span class="cw-check__text">{{ c.text }}</span>
            <span class="cw-check__verdict" :class="c.ok ? 'is-ok' : c.warn ? 'is-warn' : 'is-bad'">
              {{ c.ok ? '通过' : c.warn ? '可后补' : '未通过' }}
            </span>
          </div>

          <div class="cw-hr"></div>
          <p class="cw-label">发布方式</p>
          <div class="cw-modes">
            <button
              v-for="m in PUBLISH_MODES"
              :key="m.value"
              class="cw-mode"
              :class="{ 'is-on': mode === m.value, 'is-off': isModeDisabled(m) }"
              type="button"
              :disabled="isModeDisabled(m)"
              @click="mode = m.value"
            >
              <span class="cw-mode__label">{{ m.label.replace('发布', '上架') }}</span>
              <span class="cw-mode__desc">{{ isModeDisabled(m) ? '后端尚未支持（课程无定时字段）' : m.desc }}</span>
            </button>
          </div>

          <div class="cw-hr"></div>
          <div class="cw-foot">
            <button class="q-btn" type="button" @click="goStep(4)">上一步</button>
            <button class="q-btn q-btn--primary" type="button" :disabled="busy" @click="publish">
              {{ busy ? '提交中…' : '提交上架' }}
            </button>
          </div>
        </template>
      </section>
    </div>

    <!-- 提交中（P32）：上架是个事务，本地返回很快，但按钮文字变化太轻，
         加一层轻遮罩明确"正在发生"，也顺手挡住重复点击 -->
    <div v-if="busy" class="cw-busy" role="status" aria-live="polite">
      <div class="cw-busy__box">
        <i class="cw-busy__spin" aria-hidden="true"></i>
        <span>{{ isDraftSave ? '正在保存草稿…' : '正在提交上架…' }}</span>
      </div>
    </div>

    <!-- 上架 / 保存结果（P32：让「成功了」这件事看得见 —— 原先这里没有任何样式，
         只是一段浮在页面上的裸文字，反馈太弱） -->
    <Transition name="cw-done">
      <div
        v-if="published"
        ref="doneMask"
        class="cw-done"
        tabindex="-1"
        role="dialog"
        aria-modal="true"
        :aria-label="isDraftSave ? '草稿已保存' : '课程已上架'"
        @keydown.esc="published = null"
        @click.self="published = null"
      >
        <div class="cw-done__panel">
          <div class="cw-done__badge" :class="isDraftSave ? 'is-draft' : 'is-live'">
            <svg v-if="isDraftSave" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 3.5h9.5L20 8v12.5H6z" stroke="currentColor" stroke-width="1.9"
                    stroke-linejoin="round" />
              <path d="M9 12h6M9 16h4" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M5 12.8l4.4 4.4L19 7.6" stroke="currentColor" stroke-width="2.6"
                    stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>

          <h3 class="cw-done__title">{{ isDraftSave ? '草稿已保存' : '课程已上架' }}</h3>
          <p class="cw-done__sub">《{{ draft.basic.name }}》</p>

          <div class="cw-done__facts">
            <div class="cw-done__fact">
              <b>{{ draft.chapters.length }}</b><span>章</span>
            </div>
            <i class="cw-done__sep"></i>
            <div class="cw-done__fact">
              <b>{{ sectionTotal }}</b><span>节</span>
            </div>
            <i class="cw-done__sep"></i>
            <div class="cw-done__fact" :class="{ 'is-warn': missingVideo > 0 }">
              <b>{{ sectionTotal - missingVideo }}<em>/{{ sectionTotal }}</em></b><span>已配视频</span>
            </div>
          </div>

          <!-- 四种状态（上架/草稿 × 视频齐/缺）文案各不相同：
               草稿态不能说「学生看不到」（整门课都还没上架），语义要如实（P32） -->
          <p class="cw-done__tip" :class="missingVideo > 0 ? 'is-warn' : 'is-ok'">
            <template v-if="missingVideo > 0 && isDraftSave">
              还有 <b>{{ missingVideo }}</b> 个小节没有视频 —— 上架后这些小节学生看不到。可以先补齐再上架。
            </template>
            <template v-else-if="missingVideo > 0">
              还有 <b>{{ missingVideo }}</b> 个小节没有视频：这些小节学生看不到。补上传后
              <b>再点一次「提交上架」</b>即可同步。
            </template>
            <template v-else-if="isDraftSave">
              草稿已保存，学生暂时看不到这门课。想让它上线，点「提交上架」。
            </template>
            <template v-else>
              全部小节都已配好视频，学生现在能看到完整的课程内容。
            </template>
          </p>

          <div class="cw-done__acts">
            <button class="q-btn" type="button" @click="published = null">留在本页</button>
            <button class="q-btn q-btn--primary" type="button" @click="$router.push('/teacher/courses')">
              去我的课程看看
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 轻提示 -->
    <div v-if="notice" class="cw-notice" role="status">{{ notice }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  getCourseDraft,
  getCurrentCourseId,
  saveBasic,
  saveCatalog,
  setSectionVideo,
  toggleSectionPreview,
  addTeacher as apiAddTeacher,
  removeTeacher as apiRemoveTeacher,
  publishCourse,
  uploadSectionVideo,
  probeVideoDuration,
  getCategoryTree,
  lookupTeacher,
} from '@/api/teacher/course';
import { PUBLISH_MODES } from '@/config/teacherDict';

const router = useRouter();
const route = useRoute();
// 带 ?id= 即编辑已有课程（与出卷向导同一约定）；query-only 跳转不触发 onMounted，需 watch
const isEdit = computed(() => !!route.query.id);

const STEPS = [
  { no: '①', label: '基本信息' },
  { no: '②', label: '课程目录' },
  { no: '③', label: '课时与视频' },
  { no: '④', label: '讲师与发布' },
];

// 「定时发布」后端没有对应字段（课程只有 purchase_start/end_time，没有定时公开语义）——
// 与其让它假装成功，不如显式置灰并写明原因。
const UNAVAILABLE_MODES = ['scheduled'];
const isModeDisabled = (m) => UNAVAILABLE_MODES.includes(m.value);

const loading = ref(true);
const busy = ref(false);
const step = ref(1);
const draft = reactive({
  basic: {
    courseId: null,
    name: '',
    thirdCateId: null,
    categoryName: '',
    price: 0,
    validDays: 365,
    cover: null,
    intro: '',
  },
  chapters: [],
  teachers: [],
  checks: {},
});
const mode = ref('now');
const published = ref(null);
/** 「存为草稿」与「上架」的成功态文案 / 徽标颜色不同（P32） */
const isDraftSave = computed(() => mode.value === 'draft');
/** 结果卡出现时把焦点移过去：键盘用户能立刻读到内容，Esc 也能直接关（无障碍） */
const doneMask = ref(null);
watch(published, async (v) => {
  if (v) {
    await nextTick();
    doneMask.value?.focus?.();
  }
});

// 当前页已加载的课程 id —— 新建流程拿到真实 id 后会 router.replace 写进地址栏，
// 这个变量让 watch 认出「是自己写的 id」，不会把向导重置回第 ① 步。
const loadedId = ref(null);

// ---- 课程分类（真实三级分类树，写死分类名的时代结束了）----
const catTree = ref([]);
const catError = ref('');
const catL1 = ref(null);
const catL2 = ref(null);
const catL3 = ref(null);
const catPathText = ref('');

const l1Options = computed(() => catTree.value);
const l2Options = computed(() => (catTree.value.find((x) => x.id === catL1.value)?.children) || []);

const isLeaf = (n) => !n.children || !n.children.length;

/**
 * 「三级分类」= 叶子节点。为什么按叶子而不是按 level 字段：
 * 本地库里的分类树存在**层级异常**的分支（101 IT互联网 → 1 后端开发 → 2 Java → 3 微服务，
 * 其中 3 的 level 仍标 3 但实际在第 4 层），而现有课程用的 third_cate_id 恰好都是叶子。
 * 所以第三个下拉列出「二级节点下的全部叶子」，深层分支拍平成「Java / 微服务」这样的相对路径，
 * 既不会选到有子节点的中间层，也不会因为层级异常漏掉真实在用的分类。
 */
const leavesUnder = (node, prefix = '') => {
  const out = [];
  for (const c of node.children || []) {
    const label = prefix ? `${prefix} / ${c.name}` : c.name;
    if (isLeaf(c)) out.push({ id: c.id, name: c.name, label });
    else out.push(...leavesUnder(c, label));
  }
  return out;
};
const l3Options = computed(() => {
  const node = l2Options.value.find((x) => x.id === catL2.value);
  if (!node) return [];
  return isLeaf(node) ? [{ id: node.id, name: node.name, label: node.name }] : leavesUnder(node);
});

/** 在整棵树里找出某个分类的完整祖先链（不限深度） */
const findNodePath = (list, id, trail = []) => {
  for (const n of list) {
    const next = [...trail, n];
    if (n.id === id) return next;
    const found = findNodePath(n.children || [], id, next);
    if (found) return found;
  }
  return null;
};
const fullPathText = (id) => {
  const p = findNodePath(catTree.value, id);
  return p ? p.map((n) => n.name).join(' / ') : '';
};

const loadCategories = async () => {
  try {
    catTree.value = await getCategoryTree();
    catError.value = '';
  } catch (e) {
    catTree.value = [];
    catError.value = e?.message || '分类加载失败';
  }
};
/** 已有课程回填：按 thirdCateId 还原三级路径（深度异常的分支也能还原） */
const applyCategoryPath = (thirdCateId) => {
  catL1.value = null;
  catL2.value = null;
  catL3.value = null;
  catPathText.value = '';
  if (!thirdCateId) return;
  const path = findNodePath(catTree.value, thirdCateId);
  if (!path || !path.length) return;
  catPathText.value = path.map((n) => n.name).join(' / ');
  catL1.value = path[0].id;
  if (path[1]) catL2.value = path[1].id;
  if (path.length > 1) catL3.value = path[path.length - 1].id;
};
const onCatChange = async (level) => {
  if (level === 1) {
    catL2.value = null;
    catL3.value = null;
  }
  if (level === 2) catL3.value = null;
  const third = l3Options.value.find((x) => x.id === catL3.value);
  draft.basic.thirdCateId = third ? third.id : null;
  catPathText.value = third ? fullPathText(third.id) : '';
  draft.basic.categoryName = third ? catPathText.value : '';
  // 分类是必填项，选到三级就立即落草稿（与封面同一纪律：不让「选了没保存」发生）。
  // 名称还没填时不提交 —— 后端 @NotBlank 会拒，白报一次错没意义。
  if (third && draft.basic.name) {
    try {
      const res = await saveBasic({ ...draft.basic });
      afterBasicSaved(res?.courseId);
    } catch (e) {
      toast(e?.message || '保存分类失败');
    }
  }
};

// ---- 基本信息 ----
const coverUrl = ref(null);
const coverError = ref('');
const onCoverPick = (e) => {
  const f = e.target.files?.[0];
  if (!f) return;
  if (!['image/jpeg', 'image/png'].includes(f.type)) {
    coverError.value = '只支持 JPG / PNG';
    return;
  }
  if (f.size > 2 * 1024 * 1024) {
    coverError.value = '封面不能超过 2 MB';
    return;
  }
  coverError.value = '';
  // 转成 dataURL 本地预览：重进编辑页还能看到封面（objectURL 一刷新就失效）
  const reader = new FileReader();
  reader.onload = async () => {
    draft.basic.cover = {
      name: f.name,
      sizeMB: +(f.size / 1048576).toFixed(2),
      dataUrl: String(reader.result),
    };
    coverUrl.value = String(reader.result);
    try {
      const res = await saveBasic({ ...draft.basic }); // 选完即落草稿，避免「选了没保存」的错觉
      afterBasicSaved(res?.courseId);
      // 库里 cover_url 是 varchar(500)，dataURL 存不下 —— 本地预览是真，正式上传还没有通道
      toast(res?.coverStored === false ? '封面已本地预览 · 正式上传待对象存储接入' : '封面已保存');
    } catch (e) {
      toast(e?.message || '保存封面失败');
    }
  };

  reader.readAsDataURL(f);
};

// ---- 目录 ----
const editChapterId = ref(null);
const addSecCh = ref(-1);
const newSecTitle = ref('');
const addChapter = () => {
  draft.chapters.push({ id: Date.now(), title: `第 ${draft.chapters.length + 1} 章　新章`, sections: [] });
  persistCatalog();
};
const moveChapter = (ci, d) => {
  const arr = draft.chapters;
  [arr[ci], arr[ci + d]] = [arr[ci + d], arr[ci]];
  persistCatalog();
};
const confirmDelId = ref(null);
let confirmTimer = null;
const askRemoveChapter = (ch) => {
  if (confirmDelId.value !== ch.id) {
    confirmDelId.value = ch.id;
    clearTimeout(confirmTimer);
    confirmTimer = setTimeout(() => (confirmDelId.value = null), 2600);
    return;
  }
  confirmDelId.value = null;
  draft.chapters = draft.chapters.filter((x) => x.id !== ch.id);
  persistCatalog();
};
const startAddSection = (ci) => {
  addSecCh.value = ci;
  newSecTitle.value = '';
};
const cancelAddSection = () => {
  addSecCh.value = -1;
};
const confirmAddSection = (ch) => {
  if (!newSecTitle.value) return;
  ch.sections.push({
    id: Date.now(),
    title: newSecTitle.value,
    duration: null,
    preview: false,
    video: { status: 'none', name: '', sizeMB: 0, progress: 0 },
    quiz: null,
  });
  newSecTitle.value = '';
  addSecCh.value = -1;
  persistCatalog();
};
const renameSection = (s) => {
  const t = prompt('修改小节名称', s.title);
  if (t && t.trim()) {
    s.title = t.trim();
    persistCatalog();
  }
};
const removeSection = (ch, si) => {
  ch.sections.splice(si, 1);
  persistCatalog();
};
/**
 * 目录保存。两件事不能漏：
 *   ① 后端给新章 / 新小节分配**真实 id**，必须写回本地 —— 否则第 ③④ 步登记视频、
 *      （历史上配题还要求「落库后采纳服务端 id」，那段随 P17 取消配题一起下线了。）
 *   ② 顺序以服务端 c_index 为准（↑↓ 调整过顺序后重进页面不会被 id 大小打乱）。
 * 回写只改 id、保留本地对象：上传进度这类临时 UI 状态不会被覆盖。
 */
const adoptServerIds = (serverChapters) => {
  if (!Array.isArray(serverChapters)) return;
  serverChapters.forEach((sch, ci) => {
    const local = draft.chapters[ci];
    if (!local) return;
    local.id = sch.id;
    (sch.sections || []).forEach((ssec, si) => {
      const lsec = local.sections[si];
      if (!lsec || lsec.id === ssec.id) return;
      // 正在上传的行是按 id 挂在 uploads 上的，换 id 时要一起搬，否则取消按钮会失效
      if (uploads[lsec.id]) {
        uploads[ssec.id] = uploads[lsec.id];
        delete uploads[lsec.id];
      }
      lsec.id = ssec.id;
    });
  });
};

const persistCatalog = async () => {
  try {
    const res = await saveCatalog(JSON.parse(JSON.stringify(draft.chapters)));
    adoptServerIds(res?.chapters);
    if (res?.checks) draft.checks = res.checks;
  } catch (e) {
    toast(e?.message || '保存课程目录失败');
  }
};

// ---- 视频 ----
const flatSections = computed(() => draft.chapters.flatMap((ch) => ch.sections.map((s) => ({ ch, s }))));
const sectionTotal = computed(() => flatSections.value.length);
const missingVideo = computed(() => flatSections.value.filter((r) => r.s.video.status !== 'done').length || 0);
const uploads = reactive({});
const oneInput = ref(null);
const bulkInput = ref(null);
let oneTarget = null;

const pickOne = (s) => {
  oneTarget = s;
  oneInput.value.value = '';
  oneInput.value.click();
};
/**
 * 真上传（P23）：先读视频元数据拿时长 → multipart 上传到 media-service（真进度）
 * → 上传成功后带着 mediaId 登记到小节。登记失败如实退回「未上传」，不留假状态。
 */
const startUpload = (s, file) => {
  if (uploads[s.id]) uploads[s.id].cancel();
  s.video = { status: 'uploading', name: file?.name || 'video.mp4', sizeMB: 0, progress: 0 };
  const task = probeVideoDuration(file).then((durationSec) =>
    uploadSectionVideo(file, durationSec, (p) => { s.video.progress = p; })
  );
  uploads[s.id] = { cancel: () => task.then((t) => t.cancel()).catch(() => {}) };
  task
    .then(async ({ mediaId, durationSec }) => {
      s.video = {
        status: 'done',
        name: file.name,
        sizeMB: Math.max(1, Math.round((file.size || 0) / 1024 / 1026) || 1),
        mediaId,
        durationMin: durationSec ? Math.max(1, Math.round(durationSec / 60)) : null,
        durationSec: durationSec ? Math.round(durationSec) : null,
        progress: 100,
      };
      try {
        const res = await setSectionVideo(s.id, s.video);
        if (res?.duration) s.duration = res.duration; // 时长由服务端登记后带回
      } catch (e) {
        // 登记失败就如实退回未上传，不能留一个「已完成」的假状态
        s.video = { status: 'none', name: '', sizeMB: 0, progress: 0 };
        toast(e?.message || '视频登记失败');
      }
    })
    .catch((e) => {
      if (e?.name === 'CanceledError' || /abort/i.test(String(e?.message))) return; // 主动取消
      s.video = { status: 'none', name: '', sizeMB: 0, progress: 0 };
      toast(e?.message || '视频上传失败');
    })
    .finally(() => { delete uploads[s.id]; });
};
const onOnePick = (e) => {
  const f = e.target.files?.[0];
  if (f && oneTarget) startUpload(oneTarget, f);
};
const firstMissing = computed(() => flatSections.value.filter((r) => r.s.video.status === 'none'));
const pickBulk = () => {
  bulkInput.value.value = '';
  bulkInput.value.click();
};
const onBulkPick = (e) => {
  const files = [...(e.target.files || [])];
  files.forEach((f, i) => {
    const row = firstMissing.value[i];
    if (row) startUpload(row.s, f);
  });
};
const cancelUpload = (s) => {
  uploads[s.id]?.cancel();
  delete uploads[s.id];
  s.video = { status: 'none', name: '', sizeMB: 0, progress: 0 };
};
const removeVideo = async (s) => {
  s.video = { status: 'none', name: '', sizeMB: 0, progress: 0 };
  s.duration = null;
  try {
    await setSectionVideo(s.id, s.video);
  } catch (e) {
    toast(e?.message || '删除视频失败');
  }
};
const togglePreview = async (s) => {
  try {
    const res = await toggleSectionPreview(s.id);
    s.preview = res.preview;
  } catch (e) {
    toast(e?.message || '切换试看失败');
  }
};

// ---- 讲师 / 发布 ----
// 知序学堂是面向**所有有授课能力的老师**的开放平台，没有「校内教师名册」——
// 所以这里没有下拉名单：主讲就是建课人本人（不可移除），协作者由老师**自己填对方账号**加进来。
const ownerId = ref(null);
const newTeacherAccount = ref('');
const addingTeacher = ref(false);
/** 老模型只有「职位 + 简介」，如实展示；没有就显示「—」，不虚构角色/院系/认证 */
const teacherMeta = (t) => [t.role, t.dept].filter(Boolean).join(' · ') || '—';
const isOwner = (t) => ownerId.value != null && String(t.id) === String(ownerId.value);

const addT = async () => {
  const account = newTeacherAccount.value;
  if (!account) return;
  addingTeacher.value = true;
  try {
    // 先把账号解析成真实用户（查不到 / 不是教师身份 → 后端直接给中文原因）
    const found = await lookupTeacher(account);
    if (draft.teachers.some((x) => String(x.id) === String(found.id))) {
      throw new Error(`「${found.name || found.username}」已经在这门课的讲师里了`);
    }
    await apiAddTeacher({ id: found.id, name: found.name, role: found.job || '' });
    draft.teachers.push({
      id: found.id,
      name: found.name || found.username || `用户 ${found.id}`,
      role: found.job || '',
      dept: found.intro || '',
      certified: false,
      isShow: true,
    });
    newTeacherAccount.value = '';
    toast(`已添加协作者：${found.name || found.username}`);
  } catch (e) {
    toast(e?.message || '添加协作者失败');
  } finally {
    addingTeacher.value = false;
  }
};
const removeT = async (t) => {
  try {
    await apiRemoveTeacher(t.id);
    draft.teachers = draft.teachers.filter((x) => String(x.id) !== String(t.id));
  } catch (e) {
    toast(e?.message || '移除讲师失败');
  }
};

const checkRows = computed(() => {
  const c = draft.checks || {};
  return [c.basic, c.catalog, c.video].filter(Boolean);
});

/**
 * 完成度 = **走到第几步**：第 1 步 25% / 第 2 步 50% / 第 3 步 75% / 第 4 步 100%。
 *
 * ⚠️ 原来这里是按「内容填了多少」加权（基本信息 20 + 目录 30 + 视频 40 + 讲师 10）。
 *    向导从 5 步收到 4 步之后那套权重就错位了 —— 编辑一门已建好的课程时，
 *    人还站在第 1 步，目录和视频的历史进度已经把数字抬到 60%，看着像算错了。
 *    而且「60%」对用户没有信息量：他不知道差的 40% 是什么。
 *
 * 现在这个数只承担一个信息：**流程走到哪了**（四个台阶，闭眼可预期）。
 * 「哪一步真的完成了」由左侧步骤项的 ✓ 表达，「这一步还差什么」由各步自己的
 * 校验清单 / 视频上传进度表达 —— 三件事各归各位，不再由一个百分比兼职。
 */
const percent = computed(() => Math.round((step.value / STEPS.length) * 100));

// ---- 步骤切换 / 保存 ----
/** 新建流程第一次保存成功后，把真实 id 写进地址栏；loadedId 让 watch 认出这是自己写的 */
const afterBasicSaved = (courseId) => {
  if (!courseId) return;
  loadedId.value = courseId;
  if (String(route.query.id || '') === String(courseId)) return;
  router.replace({ path: route.path, query: { ...route.query, id: courseId } });
};

const goStep = async (n) => {
  if (n === 2 || n > step.value) {
    try {
      await saveCurrent(true);
    } catch (e) {
      // 基本信息没保存成功就不能往下走（后面的接口都要 courseId 寻址）
      toast(e?.message || '保存失败，请检查基本信息');
      return;
    }
  }
  // 校验清单必须反映「此刻」的数据 —— 之前是挂载时的快照，传完视频数字也不动。
  // ⚠️ 必须带上当前课程 id：不传会被当成「新建」，把绑定切走（已踩过，2026-09-15）
  if (n === 4) {
    try {
      const res = await getCourseDraft(getCurrentCourseId() || route.query.id);
      draft.checks = res.checks;
    } catch (e) {
      toast(e?.message || '刷新校验清单失败');
    }
  }
  step.value = n;
};
const saveCurrent = async (silent = false) => {
  busy.value = true;
  try {
    if (step.value === 1) {
      const res = await saveBasic({ ...draft.basic });
      afterBasicSaved(res?.courseId);
    }
    // 其余步骤的变更都是即时保存的（目录每次增删改都落、视频登记即落）
  } finally {
    busy.value = false;
  }
  if (!silent) toast('草稿已保存');
};

const notice = ref('');
const toast = (text) => {
  notice.value = text;
  setTimeout(() => (notice.value = ''), 2400);
};

const publish = async () => {
  busy.value = true;
  const startedAt = Date.now();
  try {
    const res = await publishCourse(mode.value);
    // 上架是个事务、本地很快就返回 —— 但**太快反而让人看不见**，
    // 所以给提示一个最短展示时长（500ms），再切到结果卡（P32）。
    const rest = 500 - (Date.now() - startedAt);
    if (rest > 0) await new Promise((r) => setTimeout(r, rest));
    published.value = res;
  } catch (e) {
    toast(e?.message || '上架失败');
  } finally {
    busy.value = false;
  }
};
const goBack = () => router.push('/teacher/courses');

const loadDraft = async () => {
  loading.value = true;
  try {
    // 分类树与草稿并行拉（分类不依赖草稿，串行只是白等）
    const [res] = await Promise.all([getCourseDraft(route.query.id), loadCategories()]);
    Object.assign(draft.basic, res.basic);
    draft.chapters = res.chapters;
    draft.teachers = res.teachers;
    draft.checks = res.checks;
    mode.value = res.publishMode || 'now';
    step.value = 1;
    loadedId.value = res.courseId ?? null;
    ownerId.value = res.ownerId ?? null;
    // 恢复封面预览（后端存的是地址，就用它当预览）
    coverUrl.value = res.basic?.cover?.dataUrl || null;
    coverError.value = '';
    applyCategoryPath(res.basic?.thirdCateId);
    newTeacherAccount.value = '';
  } catch (e) {
    toast(e?.message || '读取课程草稿失败');
  } finally {
    loading.value = false;
  }
};

onMounted(loadDraft);
watch(() => route.query.id, (id) => {
  if (route.name !== 'teacherCourseWizard') return;
  // 自己刚 replace 上去的 id 不算「换了课程」，否则新建流程走到一半会被重置回第 ① 步
  if (loadedId.value != null && String(id) === String(loadedId.value)) return;
  loadDraft();
});
</script>

<style lang="scss" scoped>
.cw {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1096px;
}

// ---- 页头 ----
.cw-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;

  &__text {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
  &__sub {
    margin: 0;
    font-size: 13px;
    color: var(--s-ink-3);
  }
  &__actions {
    flex: 0 0 auto;
    margin-left: auto;
    display: flex;
    gap: 10px;
  }
}

.cw-body {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 16px;
  align-items: start;
}

// ---- 步骤导航 ----
.cw-nav {
  padding: 12px;
  display: flex;
  flex-direction: column;

  &__item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 10px 12px;
    border: 0;
    border-radius: 10px;
    background: transparent;
    cursor: pointer;
    text-align: left;

    &:hover {
      background: #f5f5f7;
    }
    &.is-on {
      background: rgba(0, 102, 204, 0.08);
      .cw-nav__label {
        color: var(--sa, #0066cc);
        font-weight: 600;
      }
    }
    &.is-done .cw-nav__dot {
      background: #eaf6ee;
      color: #1d8a43;
    }
  }
  &__dot {
    flex: 0 0 22px;
    height: 22px;
    border-radius: 50%;
    background: #f0f0f2;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: 600;
    color: #6e6e73;
  }
  &__label {
    font-size: 13px;
    color: var(--s-ink);
  }
  &__hr {
    height: 1px;
    background: #f0f0f2;
    margin: 12px 0;
  }
  &__cap {
    margin: 0 0 4px;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__pct {
    margin: 0 0 8px;
    font-size: 20px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__track {
    height: 6px;
    border-radius: 3px;
    background: #ededf0;
    overflow: hidden;
  }
  &__fill {
    display: block;
    height: 100%;
    border-radius: 3px;
    background: var(--sa, #0066cc);
    transition: width 0.2s;
  }
  &__hint {
    margin: 12px 0 0;
    font-size: 12px;
    line-height: 18px;
    color: var(--s-ink-3);
  }
}

// ---- 内容卡 ----
.cw-card {
  padding: 28px;
  min-height: 420px;

  &__title {
    margin: 0 0 6px;
    font-size: 17px;
    line-height: 24px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__desc {
    margin: 0;
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.cw-secHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;

  .cw-card__title {
    margin-bottom: 6px;
  }
}

.cw-hr {
  height: 1px;
  background: #f0f0f2;
  margin: 16px 0;
}

.cw-field {
  margin-bottom: 16px;
}

.cw-label {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--s-ink);
}

.cw-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cw-grid3 {
  display: grid;
  grid-template-columns: 228px 228px 1fr;
  gap: 32px;
}

.cw-input,
.cw-select,
.cw-textarea {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #e5e5ea;
  border-radius: 10px;
  font-size: 13px;
  color: var(--s-ink);
  outline: none;
  background: #fff;

  &:focus {
    border-color: var(--sa, #0066cc);
  }
  &--inline {
    max-width: 460px;
  }
}

.cw-select--sm {
  max-width: 200px;
}

// 三级分类联动：三个下拉并排，宽度一致，加起来不超过版心
.cw-select--third {
  max-width: 200px;
  flex: 0 0 auto;
}

.cw-note {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-ink-3);
}

.cw-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 240px;
  height: 135px;
  border-radius: 12px;
  background: #f5f5f7;
  overflow: hidden;
  cursor: pointer;
  position: relative;

  input {
    position: absolute;
    inset: 0;
    opacity: 0;
    cursor: pointer;
  }
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  &__ph {
    font-size: 13px;
    color: var(--s-ink-3);
  }
}

.cw-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  background: #f0f0f2;
  font-size: 11px;
  color: #6e6e73;

  &.is-ok {
    background: #eaf6ee;
    color: #1d8a43;
  }
}

.cw-textarea {
  resize: vertical;
}

.cw-foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;

  &__note {
    flex: 1 1 auto;
    margin: 0;
    font-size: 12px;
    color: var(--s-ink-3);
    text-align: left;
  }
}

.cw-mini {
  color: #c7c7cc;
  font-size: 13px;
}

// ---- 目录 ----
.cw-chapter {
  margin-bottom: 16px;
  border: 1px solid #f0f0f2;
  border-radius: 12px;
  overflow: hidden;

  &__head {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    background: #fafafc;

    .cw-mini {
      cursor: grab;
    }
  }
  &__title {
    flex: 1 1 auto;
    min-width: 0;
    font-size: 14px;
    font-weight: 600;
    color: var(--s-ink);
    cursor: text;
  }
  &__ops {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    gap: 6px;
  }
}

.cw-section {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px 10px 28px;
  border-top: 1px solid #f5f5f7;

  &__title {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    color: var(--s-ink);
  }
  &__dur {
    flex: 0 0 48px;
    font-size: 12px;
    color: var(--s-ink-3);
  }
  &__ops {
    flex: 0 0 auto;
    display: flex;
    gap: 4px;
  }
  &--add {
    padding: 10px 16px 10px 28px;
  }
}

// ---- 表格（课时与视频） ----
.cw-table {
  display: flex;
  flex-direction: column;
}

.cw-trow {
  display: grid;
  grid-template-columns: 260px 1fr 56px 48px 130px;
  gap: 12px;
  align-items: center;
  padding: 12px 0;

  & + & {
    border-top: 1px solid #f0f0f2;
  }
  &--head {
    padding: 0 0 8px;
    font-size: 12px;
    color: var(--s-ink-3);
    border-bottom: 1px solid #f0f0f2;
  }
}

.cw-trow:has(.cw-picker) {
  display: block;
}

.cw-tc {
  min-width: 0;
  font-size: 13px;
  color: var(--s-ink);
  display: flex;
  align-items: center;
  gap: 8px;

  &--sec {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  &--ops,
  &--ops2 {
    gap: 4px;
    justify-content: flex-end;
  }
}

.cw-cw-muted {
  color: var(--s-ink-3);
}

.cw-thumb {
  flex: 0 0 48px;
  height: 28px;
  border-radius: 6px;
  background: #1d1d1f;
  color: #fff;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cw-videoText {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;

  &__name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    color: var(--s-ink);
  }
  &__meta {
    font-size: 11px;
    color: var(--s-ink-3);
  }
}

.cw-track {
  height: 6px;
  border-radius: 3px;
  background: #ededf0;
  overflow: hidden;

  &__fill {
    display: block;
    height: 100%;
    background: var(--sa, #0066cc);
  }
}

.cw-pct {
  flex: 0 0 34px;
  font-size: 11px;
  font-style: normal;
  color: var(--s-ink-3);
}

.cw-alert {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 10px;
  background: #fff7ec;
  font-size: 12px;
  line-height: 18px;
  color: #8a5a10;
}

.cw-tip {
  margin-top: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  background: #f5f5f7;
  font-size: 12px;
  line-height: 18px;
  color: var(--s-ink-3);
}

// ---- 选题面板 ----
.cw-picker {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid #e5e5ea;
  border-radius: 12px;

  &__title {
    margin: 0 0 10px;
    font-size: 13px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__list {
    max-height: 260px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 4px;
    margin-bottom: 12px;
  }
  &__item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: 8px;
    cursor: pointer;

    &:hover {
      background: #f5f5f7;
    }
  }
  &__stem {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 13px;
    color: var(--s-ink);
  }
  &__meta {
    flex: 0 0 auto;
    font-size: 11px;
    color: var(--s-ink-3);
  }
}

// ---- 讲师 / 校验 / 发布方式 ----
.cw-teacher {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;

  &__avatar {
    flex: 0 0 32px;
    height: 32px;
    border-radius: 50%;
    background: #e8e8ed;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 600;
    color: #6e6e73;
  }
  &__text {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  &__name {
    font-size: 14px;
    font-weight: 500;
    color: var(--s-ink);
  }
  &__meta {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.cw-check {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;

  &__dot {
    flex: 0 0 10px;
    height: 10px;
    border-radius: 50%;

    &.is-ok {
      background: #1d8a43;
    }
    &.is-warn {
      background: #c98a2b;
    }
    &.is-bad {
      background: #c2543a;
    }
  }
  &__text {
    flex: 1 1 auto;
    font-size: 13px;
    color: var(--s-ink);
  }
  &__verdict {
    flex: 0 0 auto;
    font-size: 12px;
    font-weight: 500;

    &.is-ok {
      color: #1d8a43;
    }
    &.is-warn {
      color: #c98a2b;
    }
    &.is-bad {
      color: #c2543a;
    }
  }
}

.cw-modes {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.cw-mode {
  padding: 14px 16px;
  border: 1px solid #e5e5ea;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 4px;

  &.is-on {
    border-color: var(--sa, #0066cc);
    background: rgba(0, 102, 204, 0.05);
  }

  // 后端没有对应能力的选项：置灰但不隐藏 —— 隐藏会让人以为「本来就没这个功能」，
  // 写明原因才是如实告知
  &.is-off {
    opacity: 0.45;
    cursor: not-allowed;
  }

  &__label {
    font-size: 14px;
    font-weight: 600;
    color: var(--s-ink);
  }
  &__desc {
    font-size: 12px;
    color: var(--s-ink-3);
  }
}

.cw-state {
  margin: 0;
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: var(--s-ink-3);
}

.q-act.is-danger {
  color: #c2543a;
  font-weight: 600;
}

.cw-notice {
  position: fixed;
  left: 50%;
  bottom: 32px;
  transform: translateX(-50%);
  padding: 10px 18px;
  border-radius: 999px;
  background: rgba(29, 29, 31, 0.9);
  color: #fff;
  font-size: 13px;
}

/* ---------------------------------------------------------------------------
   提交中遮罩（P32）
   ---------------------------------------------------------------------------
   z-index 取 69：**低于**结果卡（70）—— 两者虽不同时出现，但万一同时在场，
   也让"结果"压在上面，不会被遮罩盖住。
--------------------------------------------------------------------------- */
.cw-busy {
  position: fixed;
  inset: 0;
  z-index: 69;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.66);
  backdrop-filter: saturate(180%) blur(4px);

  &__box {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 22px;
    border-radius: 14px;
    background: var(--s-card, #fff);
    box-shadow: var(--s-sh-pop, 0 14px 32px -6px rgba(0, 0, 0, 0.14));
    font-size: 13.5px;
    color: var(--s-ink-2, #6e6e73);
  }

  &__spin {
    width: 18px;
    height: 18px;
    border-radius: 50%;
    border: 2px solid var(--s-hairline, #e8e8ed);
    border-top-color: var(--sa, #0066cc);
    animation: cwSpin 0.7s linear infinite;
  }
}

@keyframes cwSpin {
  to {
    transform: rotate(360deg);
  }
}

/* ---------------------------------------------------------------------------
   上架 / 保存结果卡（P32）
   ---------------------------------------------------------------------------
   以前这一块**完全没有样式**：只是一段文字浮在页面上，用户反馈"上架成功的显示很薄弱"。
   这里给它一个明确的重心：成功徽标（绿=已上架 / 灰=存草稿）→ 课程名 → 三个事实数字
   → 一句针对当前数据的提醒（还有几节没配视频）→ 两个出口按钮。
   强调顺序按重要性排：先"成功了"，再"上了什么"，最后"下一步"。
--------------------------------------------------------------------------- */
.cw-done {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.32);
  outline: none;

  &__panel {
    width: 400px;
    max-width: 100%;
    padding: 30px 28px 22px;
    border-radius: 20px;
    background: var(--s-card, #fff);
    box-shadow: var(--s-sh-pop, 0 14px 32px -6px rgba(0, 0, 0, 0.14));
    text-align: center;
    animation: cwDonePanel 0.26s cubic-bezier(0.2, 0.9, 0.3, 1.05);
  }

  /* 成功徽标 */
  &__badge {
    width: 60px;
    height: 60px;
    margin: 0 auto 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    color: #fff;
    animation: cwDoneBadge 0.44s cubic-bezier(0.2, 1.1, 0.4, 1.05) 0.06s backwards;

    svg {
      width: 30px;
      height: 30px;
    }

    /* 绿 = 已上架（学生已可见）；灰 = 仅存草稿 */
    &.is-live {
      background: var(--s-ok, #34c759);
      box-shadow: 0 8px 20px -6px rgba(52, 199, 89, 0.6);
    }

    &.is-draft {
      background: #8e8e93;
      box-shadow: 0 8px 20px -6px rgba(142, 142, 147, 0.5);
    }
  }

  &__title {
    margin: 0;
    font-size: 19px;
    font-weight: 600;
    color: var(--s-ink, #1d1d1f);
    letter-spacing: 0.2px;
  }

  &__sub {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--s-ink-3, #86868b);
    word-break: break-all;
  }

  /* 三个事实数字 */
  &__facts {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 18px;
    margin: 20px 0 14px;
    padding: 14px 0;
    border-radius: 14px;
    background: var(--s-soft, #f5f5f7);
  }

  &__fact {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    min-width: 64px;

    b {
      font-size: 19px;
      font-weight: 600;
      color: var(--s-ink, #1d1d1f);
      font-variant-numeric: tabular-nums;

      em {
        font-style: normal;
        font-size: 12px;
        font-weight: 400;
        color: var(--s-ink-3, #86868b);
      }
    }

    span {
      font-size: 11px;
      color: var(--s-ink-3, #86868b);
    }

    /* 有没配视频的小节时，这个数字用暖色提醒 */
    &.is-warn b {
      color: var(--s-warn, #ff9f0a);
    }
  }

  &__sep {
    width: 1px;
    height: 26px;
    background: var(--s-hairline, #e8e8ed);
  }

  /* 针对当前数据的一句话 */
  &__tip {
    margin: 0 0 18px;
    padding: 10px 12px;
    border-radius: 10px;
    font-size: 12.5px;
    line-height: 18px;
    text-align: left;

    b {
      font-weight: 600;
    }

    &.is-warn {
      background: rgba(255, 159, 10, 0.1);
      color: #8a5a00;

      b {
        color: #b26a00;
      }
    }

    &.is-ok {
      background: rgba(52, 199, 89, 0.1);
      color: #1e7a3c;
    }
  }

  &__acts {
    display: flex;
    gap: 10px;
    justify-content: flex-end;
  }
}

@keyframes cwDonePanel {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes cwDoneBadge {
  from {
    opacity: 0;
    transform: scale(0.5);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.cw-done-enter-active,
.cw-done-leave-active {
  transition: opacity 0.18s ease;
}

.cw-done-enter-from,
.cw-done-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .cw-done__panel,
  .cw-done__badge {
    animation: none;
  }

  .cw-done-enter-active,
  .cw-done-leave-active {
    transition: none;
  }
}
</style>
