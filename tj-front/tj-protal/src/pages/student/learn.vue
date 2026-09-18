<!--
 * 课程学习（/student/learn?id=&sectionId=）— 学员端新风格
 * 说明：本页 **script 逻辑与老项目 pages/learning/index.vue 完全一致**（播放器签名、播放日志、
 * 目录 / 练习题 / 问答 / 笔记 / AI助教 全部原样保留），仅重写模板结构与样式，
 * 使学习页并入学员端外壳（左侧栏 + 顶栏）并套用学员端设计语言。
 * 子组件（Catalogue / Question / Note / AiTutor / Practise）均为 scoped 样式，直接复用。
 * 版式：左视频区（自适应，16:9）+ 右信息与页签栏 380px。
 * 布局模式：meta.fullHeight = true（壳内自滚动），移动端右栏下沉。
 -->
<template>
  <div class="learn">
    <!-- 左：视频 / 练习 -->
    <div class="learn__stage">
      <header class="learn__bar">
        <button class="backBtn" type="button" @click="goBack">
          <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="m10 3-5 5 5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          返回
        </button>
        <span class="learn__title" :title="currentPlayData.sectionName">{{ currentPlayData.sectionName || '课程学习' }}</span>
      </header>

      <div class="videoBox" v-show="pageType == 1">
        <!-- controls：播放/暂停、拖进度、音量、全屏 —— 之前控制条由 TCPlayer 渲染，
             本地模式换成原生 <video> 后没人给控制条，导致「能播但不能暂停/拖不动」 -->
        <video
          id="videoRef"
          ref="videoRef"
          controls
          controlsList="nodownload"
          playsinline
          preload="metadata"
        ></video>
        <!-- 拿不到播放凭证时给一句人话，别留一块黑屏（媒资服务未启用时必现） -->
        <div v-if="mediaError" class="videoTip">
          <p class="videoTip__title">本节视频暂时无法播放</p>
          <p class="videoTip__desc">{{ mediaError }}</p>
        </div>
      </div>

      <Practise
        v-if="pageType == 2"
        @playHadle="playHadle"
        :examInfo="examInfo"
        :key="currentPlayData.sectionId"
      ></Practise>
    </div>

    <!-- 右：课程信息 + 目录/问答/笔记/AI助教 -->
    <aside class="learn__side">
      <div class="s-card courseRow">
        <img
          class="courseRow__cover"
          :src="learningClassDetails && learningClassDetails.coverUrl"
          alt=""
        />
        <div class="courseRow__col">
          <span class="courseRow__name">{{ (learningClassDetails && learningClassDetails.name) || '课程' }}</span>
          <span class="courseRow__teacher">讲师：{{ (learningClassDetails && learningClassDetails.teacherName) || '—' }}</span>
        </div>
      </div>

      <div class="s-card sideBody">
        <div class="tabs" role="tablist">
          <button
            v-for="t in tableBar"
            :key="t.id"
            class="tab"
            :class="{ 'is-on': actId === t.id }"
            type="button"
            role="tab"
            :aria-selected="actId === t.id"
            @click="changeTable(t.id)"
          >{{ t.name }}</button>
        </div>

        <div class="paneHost">
          <div class="pane pane--scroll" v-show="actId == 1" v-infinite-scroll="load">
            <Catalogue
              :data="chapters"
              :playId="playId"
              :finished="finished"
              @playHadle="playHadle"
              @openCatalogue="openCatalogue"
            ></Catalogue>
          </div>
          <div class="pane" v-if="actId == 2" v-infinite-scroll="load">
            <Question></Question>
          </div>
          <div class="pane" v-if="actId == 3" v-infinite-scroll="load">
            <Note :currentTime="currentPlayTime"></Note>
          </div>
          <div class="pane" v-if="actId == 4">
            <AiTutor
              :courseId="currentPlayData.courseId"
              :courseName="learningClassDetails && learningClassDetails.name"
              :sectionName="currentPlayData.sectionName"
            ></AiTutor>
          </div>
          <!-- P17 新增：这门课的试卷（讲师在考试管理里发布、关联到本课程的那些） -->
          <div class="pane" v-if="actId == 5">
            <CourseExamList :courseId="currentPlayData.courseId"></CourseExamList>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>

<script setup>
/** 数据导入 **/
import { onMounted, ref, onUnmounted, provide, h } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getMediasSignature, addPlayLog, getLearningClassDetails } from "@/api/class.js";
import { useRoute } from "vue-router";
import { dataCacheStore } from "@/store"
// 组件导入
import TableSwitchBar from "@/pages/learning/components/TableSwitchBar.vue";
import Catalogue from "@/pages/learning/components/Catalogue.vue";
import Question from "@/pages/learning/components/Question.vue";
import Practise from "@/pages/learning/components/Practise.vue";
import Note from "@/pages/learning/components/Note.vue";
import AiTutor from "@/pages/learning/components/AiTutor.vue";
// P17：课程维度的试卷（讲师发布 → 学生在这里直接考）。只读列表 + 点进答题页
import CourseExamList from '@/pages/student/components/CourseExamList.vue';
import icon from '@/assets/icon_good.png'

import router from "../../router";
import {reactive} from "@vue/reactivity";

const route = useRoute()
const store = dataCacheStore()

// 主展示区域 1 为视频 2 为练习 3 考试
const pageType = ref(1)
// 结果 - 详情Id
const detailsId = ref({})
// 课程信息及讲师信息
const tableBar = [{id: 1, name: '目录'}, {id: 2, name: '问答'}, {id: 3, name: '笔记'}, {id: 4, name: 'AI助教'}, {id: 5, name: '考试'}]
// 课程目录
const classListData = ref([])

const videoRef = ref(null)

/** 方法定义 **/
/*
* 本节有三种模式播放  
* 一、免费课程 - 先点击点击到学习计划然后进入这里学习
* 二、购买的课程 - 购买成功自动加入学习计划到这里学习
* 三、试听 - 无需加入学习计划直接学习
* 先加载当前课程信息
* 然后获取学习计划信息
* 通过课程计划自动调整到对应的小节 - 通过小节Id 获取视频签名（用于视频播放）
* 点击小节 - 通过小节Id 获取视频签名（用于视频播放）
*
*/

// 默认播放小节
const playId = ref('')
// 是否播完
const finished = ref(false);
// 记录播放相关参数
const fileId = ref('')
const signature = ref('')
const vodAppId = ref(null)
let timer = -1;
let playing = false;
// 当前播放小节信息缓存 
const currentPlayData = reactive({
  courseId: route.query.id,    // 课程Id
  lastPlaySectionId: '', // 上一次播放的小节Id
  prevSectionId: '', // 上一个小节的id
  sectionId: '',  // 小节Id
  sectionName: '',
  nestSectionId: '', // 下一个小节的id
  moment: '', // 播放时间
  duration: '', // 总时长
  type: '', // 小节类型
})

provide('currentPlayData', currentPlayData)
//当前播放课程的全部信息
const learningClassDetails = ref()
const chapters = ref()
const sectionMap = ref({})

onMounted(async () => {
  //TODO 详情 - 课程ID 小节ID  课程名称 小节名称 讲师的信息 课程图片 + 当前课程是否购买
  //TODO 判断课程是否有播放记录 - 如果课程没有看过 - 从第一节开始播放
  //TODO 如果课程已经看过了 需要最后一次播放的信息 小节ID 小节名称 小节播放时间 到哪里了
  detailsId.value = route.query.id  // 课程id

  // 使用课程id获取当前课程的细节
  await getLearningClassDetailsData()
  // 获取上次播放的小节及时间
  currentPlayData.sectionId = currentPlayData.sectionId || learningClassDetails.value.latestSectionId
  currentPlayData.moment =  currentPlayData.moment || learningClassDetails.value.moment
  // 落在「考试」小节（大纲里 type=3）→ 直接进试卷；不要向视频服务要播放凭证
  // （否则会白报一次错，且学生从课程详情页点考试小节进来时看不到卷子）
  const curSection = sectionMap.value[currentPlayData.sectionId]
  if (String((curSection && curSection.type) || currentPlayData.type) === '3') {
    startExaminationHandle(curSection || { id: currentPlayData.sectionId, type: 3 })
    return
  }
  // 通过课程的小节id获取视频的fileId
  await getMediasSignatureData(currentPlayData.sectionId);
})
// 使用课程id获取当前课程的细节
const getLearningClassDetailsData = async () => {
  await getLearningClassDetails(detailsId.value)
      .then((res) => {
        if (res.code === 200) {
          // P25：免费课程不存在「试看」—— 后端已对免费课全量放行，这里让目录也不再显示该标签
          const courseFree = res.data.free === 1 || res.data.free === true;
          // 将小节映射为id->小节的map
          res.data.chapters.forEach(c => c.sections.forEach(s => {
            if (courseFree) s.trailer = 0;
            sectionMap.value[s.id] = s
            s.hasTest=!!s.subjectNum;
          }));
          // 找到要播放的小节：优先是路径中指定的小节，如果没有则是最近学习的小节，如果还没有则是第一个小节
          let sId = route.query.sectionId || res.data.latestSectionId;
          let s = sectionMap.value[sId] || res.data.chapters[0].sections[0];
          res.data.latestSectionMoment = s.moment;
          res.data.latestSectionName = s.name;
          learningClassDetails.value = res.data;
          chapters.value = res.data.chapters;
          // 缓存当前播放内容
          currentPlayData.duration = s.mediaDuration
          currentPlayData.sectionId = s.id;  // 小节Id
          currentPlayData.moment = s.moment || 0; // 播放时间
          currentPlayData.sectionName = s.name // 小节名称
          currentPlayData.type = s.type // 小节类型
          currentPlayData.lessonId = res.data.lessonId // 小节类型
          // 默认展开对应的章
          playId.value = currentPlayData.sectionId || ""
        } else {
          ElMessage({
            message: res.msg,
            type: 'error'
          });
        }
      })
      .catch(() => {
        ElMessage({
          message: "请求出错！",
          type: 'error'
        });
      });
  store.setCurrentPlayData(currentPlayData)
};
// 课程上完了 弹窗 去个人中心
const classFinished = () => {
  ElMessageBox({
    title: '',
    message: h('div', null, [
      h('div', { style: 'display:flex' },
        [h('img', { src: icon, style: 'width:52px;height:52px;margin-right: 10px;' }),
        [h('div', null,
          [
            h('div', { style: 'font-size: 18px;font-weight: 500;' }, '你真棒！'),
            h('div', { style: 'line-height:30px' }, '所有内容全部学完，可以开始新的征程了~')
          ])
        ]
        ]
      ),

    ]),
    showCancelButton: false,
    confirmButtonText: '我知道了',
  })
    .then(() => {
      router.push('/student/courses')
    })
}
// 组件卸载的时候触发 - 页面跳转的时候触发
onUnmounted(() => {
  window.clearInterval(timer)
})
const currentPlayTime = ref(0)
// 初始化视频播放器并播放视频 视频ID、播放器签名
const player = ref(null)
const initPlay = (fileID, psign, appID) => {
  if (appID == null || appID === '') {
    ElMessage({
      message: '服务端未返回云点播 AppId，请检查媒资服务 tj.platform.media=TENCENT 与 tj.tencent.appId',
      type: 'error'
    })
    return
  }
  player.value = new TCPlayer(videoRef.value, {
    appID: String(appID),
    fileID,
    psign,
    posterImage: true,
    autoplay: true,
    preload: 'auto',
    hlsConfig: {},
  });
  player.value.on('timeupdate', function () {
    currentPlayData.moment = player.value.currentTime();
    currentPlayTime.value = currentPlayData.moment
  });
  player.value.on('pause', function () {
    // 每次视频暂停的时候 停止发送播放记录请求
    window.clearInterval(timer)
    playing = false;
  });
  player.value.on('play', function () {
    if(playing) return;
    playing = true;
    finished.value = false;
    if (!currentPlayData.lessonId) {
      // 免费试看，无需记录播放进度
      return;
    }
    addPlayLogHandle()
    // 每次视频播放的时候 开始 发送播放记录
    timer = window.setInterval(addPlayLogHandle, 15000)
  });
  player.value.on('ended',  () =>{
    // 播放结束时 停止计算器 并提交最后一次播放状态
    window.clearInterval(timer)
    //timer = 0;
    // 续播下一个
    finished.value = true;
  });
  player.value.ready(() => {
    window.clearInterval(timer)
    player.value.currentTime(currentPlayData.moment || 0)
    player.value.play()
  })
}

// 本地媒资播放（P23）：TCPlayer 未引入（index.html 注释掉了 CDN），且本地模式拿到的就是直链 ——
// 直接用页面里已有的 <video> 元素。事件语义与上面 TCPlayer 分支一一对应（播放记录上报逻辑不变）。
/**
 * 视频播放地址（P23）：
 *   ① 后端给的是网关视角的相对路径 `/ms/media-stream/xxx` —— 直接当 <video src> 会打到
 *      dev server（18090）→ 黑屏，所以必须拼绝对地址；
 *   ② **不走网关**：实测网关转发 Range 请求会返回 206 + 89B 的 JSON（内容不对），
 *      而浏览器播 mp4 依赖 Range（拖进度、分段加载）→ 直接连媒体服务端口。
 *      生产部署时把 VITE_MEDIA_BASE_URL 换成媒体服务域名 / 网关前的反向代理即可。
 */
const MEDIA_BASE = (import.meta.env.VITE_MEDIA_BASE_URL || 'http://localhost:8084').replace(/\/+$/, '')
const absoluteMediaUrl = (url) => {
  if (!url) return ''
  if (/^https?:/i.test(url)) return url
  // 去掉网关前缀 /ms（媒体服务自己的路径是 /media-stream/...）
  return MEDIA_BASE + url.replace(/^\/ms\//, '/')
}

let nativeBound = false
const initNativePlay = (url) => {
  const el = videoRef.value
  if (!el) { setMediaError('播放器未就绪，请刷新页面'); return }
  if (!nativeBound) {
    nativeBound = true
    el.addEventListener('timeupdate', () => {
      currentPlayData.moment = el.currentTime
      currentPlayTime.value = el.currentTime
    })
    el.addEventListener('pause', () => { window.clearInterval(timer); playing = false })
    el.addEventListener('play', () => {
      if (playing) return
      playing = true
      finished.value = false
      if (!currentPlayData.lessonId) return // 免费试看不记播放进度
      addPlayLogHandle()
      timer = window.setInterval(addPlayLogHandle, 15000)
    })
    el.addEventListener('ended', () => {
      window.clearInterval(timer)
      // P25：播到结尾补报最后一次进度 —— 否则最后一次上报可能是 15 秒前的，
      // 完整看完也可能卡在完成线以下（短视频尤其明显）。
      currentPlayData.moment = Math.round(el.duration || el.currentTime || 0)
      if (currentPlayData.lessonId) addPlayLogHandle()
      // 播到结尾 = 进度 100% ≥ 70% 完成线：本地立即打上完成标记。
      // sectionMap 里存的就是 chapters 里同一个对象，目录中该小节马上出现 ✓，不用等整页刷新。
      const cur = sectionMap.value[currentPlayData.sectionId]
      if (cur) cur.finished = true
      playing = false // 置回未播放态，用户重播时才会重新开始上报
      finished.value = true
    })
    // 拉不到视频时给一句人话，别只留一块黑屏
    el.addEventListener('error', () => {
      window.clearInterval(timer)
      playing = false
      setMediaError('视频加载失败，请稍后重试（若刚上传，请确认已上架该课程）')
    })
  }
  el.src = absoluteMediaUrl(url)
  // 元数据就绪后从上次进度续播
  const resume = () => {
    el.removeEventListener('loadedmetadata', resume)
    // P25：完成判定必须用**视频真实时长** —— 接口给的是课程里预设的 mediaDuration，
    // 与实际上传的视频长度常常对不上（预设 900 秒 / 真实 5 秒 → 永远到不了完成线）。
    if (Number.isFinite(el.duration) && el.duration > 0) {
      currentPlayData.duration = Math.round(el.duration)
    }
    if (currentPlayData.moment) el.currentTime = Number(currentPlayData.moment) || 0
    el.play().catch(() => { /* 自动播放被拦时用户手动点播放即可 */ })
  }
  el.addEventListener('loadedmetadata', resume)
}

// 目录、问答、笔记滚动
const load = () => {}
const t = (n) => {
  return n < 10 ? '0'+n : n;
}
const now = () => {
  let d = new Date();
  return d.getFullYear() + "-" + t(d.getMonth() + 1) + "-" +t(d.getDate()) +
      " " + t(d.getHours()) + ":" + t(d.getMinutes())+":" + t(d.getSeconds());
}

// 播放新的小节的时候提交相关记录
const addPlayLogHandle = () => {
  let {lessonId, sectionId, moment, duration} = currentPlayData;
  addPlayLog({lessonId, sectionId, moment, duration, sectionType: 1, commitTime: now()})
      .then((res) => {
        if (res.code === 200) {
          console.log("记录成功:", res)
        }
      })
      .catch(err =>console.log(err));
};

// 通过课程的小节id获取视频的fileId
// 播放区里要显示的「为什么播不了」（比弹一句网关原文有用）
const mediaError = ref('')
/**
 * 播放失败的统一出口。除了展示原因，还必须**把播放器复位**：
 * 切到没有视频的小节时，<video> 里还挂着上一节的 src（画面和声音会继续留着），
 * 提示会盖在正在播放的旧视频上 —— 看着很怪。
 */
const setMediaError = (msg) => {
  mediaError.value = msg
  const el = videoRef.value
  if (el && el.getAttribute('src')) {
    el.pause()
    el.removeAttribute('src')
    el.load() // 复位播放器（无 src 时不会触发 error，不会递归）
  }
}
const getMediasSignatureData = async (sectionId) => {
  let res
  try {
    res = await getMediasSignature({sectionId})
  } catch (e) {
    setMediaError('播放凭证请求失败，请稍后重试')
    return false
  }
  if (res.code === 200) {
    mediaError.value = ''
    // 本地媒资（P23）：签名接口直接返回 mediaUrl → 原生 <video> 播放，不走 VOD 签名
    if (res.data.mediaUrl) {
      initNativePlay(res.data.mediaUrl)
      return true
    }
    fileId.value = res.data.fileId
    signature.value = res.data.signature
    vodAppId.value = res.data.appId ?? null
    if (player.value == null) {
      initPlay(res.data.fileId, res.data.signature, res.data.appId)
    }
    return true;
  } else {
    // 不再把网关原文（如「服务不存在」）弹成 toast —— 直接写在播放区里，
    // 用户一眼能看出是"这一节视频放不了"，而不是整个系统坏了
    setMediaError(res.msg || '未获取到播放凭证')
    return false;
  }
};

// 点击小节
const playHadle = async (val) => {
  const {item, tp} = val
  if(tp == 0){
    finished.value = false;
    ElMessage.success("本章学习完毕，做做练习吧")
    return
  }
  // 小节名称
  currentPlayData.sectionName = item.name
  // 练习返回
  if(tp == '9'){
    pageType.value = 1;
    return 
  }
  // 更新currentPlayData 提交播放记录使用
  currentPlayData.sectionId = item.id  // 小节Id
  currentPlayData.moment = item.moment // 播放时间
  currentPlayData.duration = item.mediaDuration // 总时长
  // 视频播放
  if (tp == '1') {
    pageType.value = 1
    if (item.id === currentPlayData.id && player.value) {
      player.value.play()
    } else {
      let r = await getMediasSignatureData(item.id)
      if(!r || !player.value){
        return;
      }
      player.value.loadVideoByID(
          {
            appID: String(vodAppId.value ?? ''),
            fileID: fileId.value,
            psign: signature.value,
          }
      )
      player.value.currentTime(item.latestSectionMoment)
      player.value.play()
    }
  } else if (tp == '2' || tp == '3') {
    // 暂停视频。⚠️ 播放器可能根本没初始化：拿不到播放凭证时 initPlay 会提前 return，
    //    player.value 仍是 null —— 以前这里直接 .pause() 会抛 TypeError，
    //    导致后面的确认弹窗压根不出现、点「考试」小节永远进不去试卷（2026-09-16 无头实测复现）。
    if (player.value) player.value.pause()
    // 打开练习题 开始考试
    if (item.type != 2 ){
        ElMessageBox.confirm(
        `温馨提示：考试只能考一次，如果中途退出或未提交结果，将不会允许再次考试，确认考试请点击 继续考试`,
        '确认考试',
          {
            confirmButtonText: '继续考试',
            cancelButtonText: '等会再来',
            type: 'warning',
          }
        )
        .then(() => {
          startExaminationHandle(item)
        })
      } else {
        startExaminationHandle(item)
      }
    }
  store.setCurrentPlayData(currentPlayData)
}

// 考试开始时提交
const examInfo = ref({})
const startExaminationHandle = (item) => {
  examInfo.value = {
    sectionId: item.id, // 小节id
    type: item.type - 1,  // 类型，1-练习，2-考试  item.type对应章节的类型，2-视频（小节），3-考试
    courseId: currentPlayData.courseId,
  }
  pageType.value = 2;
}

// 答题出错的时候
const errorHandle = (val) => {
  pageType.value = 1
}

// 目录 - 打开一个章列表
const openCatalogue = (item) => {
  currentPlayData.chapterId = item
}

// table切换 目录、问答、笔记
const actId = ref(1)
const changeTable = id => {
  actId.value = id
}
// 关闭目录
const isClose = ref(false);
const close = () => {
  isClose.value = true
}
// 打开目录
const open = () => {
  isClose.value = false
}
// 返回上一页
const goBack = () => {
  window.clearInterval(timer);
  timer = 0;
  router.go(-1)
}
//收藏
</script>

<style lang="scss" scoped>
// =============================================================================
// 课程学习页（学员端新风格）
// 左：视频 / 练习自适应；右：课程信息 + 目录/问答/笔记/AI助教（380px）
// 子组件（Catalogue / Question / Note / AiTutor / Practise）自带 scoped 样式，
// 此处只提供版式容器与滚动宿主。
// =============================================================================
.learn {
  display: flex;
  gap: 20px;
  align-items: flex-start;

  &__stage {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  &__bar {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
  }

  &__title {
    min-width: 0;
    font-size: 16px;
    line-height: 22px;
    font-weight: 600;
    color: #1d1d1f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__side {
    flex: 0 0 380px;
    width: 380px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
}

.backBtn {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px 0 10px;
  border: 0;
  border-radius: 17px;
  background: #f5f5f7;
  color: #333;
  font-size: 13px;
  line-height: 18px;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  svg { width: 16px; height: 16px; }
  &:hover { background: #e9eaee; color: #0066cc; }
}

// 视频容器：固定 16:9，播放器注入到 <video> 元素内
.videoBox {
  position: relative;
  aspect-ratio: 16 / 9;
  display: flex;
  border-radius: 18px;
  overflow: hidden;
  background: #0b0b0e;
  box-shadow: inset 0 0 0 1px rgba(16, 24, 40, 0.08);

  video {
    width: 100%;
    height: 100%;
    display: block;
    background: #0b0b0e;
    object-fit: contain;
  }
}

// 视频不可播放时的说明（盖在黑底播放区上，替换原来的「一整块黑」）
.videoTip {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 24px;
  text-align: center;
  pointer-events: none;

  &__title {
    margin: 0;
    font-size: 15px;
    line-height: 22px;
    font-weight: 600;
    color: #f5f5f7;
  }
  &__desc {
    margin: 0;
    font-size: 12px;
    line-height: 18px;
    color: rgba(245, 245, 247, 0.62);
  }
}

// ---------- 右栏：课程信息 ----------
.courseRow {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 18px;

  &__cover {
    flex: 0 0 64px;
    width: 64px;
    height: 44px;
    border-radius: 8px;
    object-fit: cover;
    background: #e9eaee;
  }
  &__col { flex: 1 1 auto; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
  &__name {
    font-size: 14px;
    line-height: 20px;
    font-weight: 600;
    color: #1d1d1f;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  &__teacher {
    font-size: 12px;
    line-height: 17px;
    color: #86868b;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

// ---------- 右栏：页签体 ----------
.sideBody {
  padding: 16px;
  border-radius: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  border-bottom: 1px solid #ececf0;
  padding-bottom: 10px;
}

.tab {
  position: relative;
  height: 30px;
  padding: 0 14px;
  border: 0;
  border-radius: 15px;
  background: #f5f5f7;
  color: #333;
  font-size: 13px;
  line-height: 18px;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease;

  &:hover { background: #e9eaee; }
  &.is-on {
    background: #0066cc;
    color: #fff;
    font-weight: 500;
  }
}

// 子组件的滚动宿主（目录 / 问答 / 笔记 / AI助教）
// 高度链：.learn(100%, fullHeight) → __side(100%) → sideBody(flex:1) → paneHost(flex:1) → pane(100%)
// 问答/笔记的输入区是 absolute 定位（锚定最近的 position 祖先），所以 pane 必须 position:relative + 不整体滚动，
// 由子组件内部的列表区各自滚动、输入区钉在面板底部。
.paneHost {
  position: relative;
  height: 560px;
  overflow: hidden;
}
.pane {
  position: relative;
  height: 100%;
  min-height: 0;
  overflow: hidden;

  // 目录页签：整块滚动（内部没有 absolute 元素）
  &--scroll {
    overflow-y: auto;
  }

  // AI助教：聊天区在此高度内自行滚动（回答过长时出现内部滚动条）

  // ---- 问答（老组件 Question.vue）：撑满 + 列表内部滚动，提问区钉底 ----
  :deep(.questionWrapper) {
    height: 100%;
    margin-top: 0;
    display: flex;
    flex-direction: column;
    position: relative;
    padding-bottom: 124px;

    .askCont {
      flex: 1 1 auto;
      min-height: 0;
      overflow-y: auto;
    }
    .noData {
      height: auto;
      min-height: 120px;
    }
  }

  // ---- 笔记（老组件 Note.vue）：列表区改自适应高度滚动 ----
  :deep(.learnNoteWrapper) {
    height: 100%;
    display: flex;
    flex-direction: column;
    position: relative;
    padding-bottom: 132px;

    .noteCont {
      flex: 1 1 auto;
      height: auto;
      min-height: 0;
      overflow-y: auto;
    }
    .noData {
      height: auto !important;
      min-height: 120px;
    }
  }
}
.pane { min-height: 0; }

// =============================================================================
// 窄屏：右栏下沉，视频撑满
// =============================================================================
@media (max-width: 1180px) {
  .learn { flex-direction: column; }
  .learn__side { flex: 0 0 auto; width: 100%; }
  .paneHost { height: 480px; }
}
</style>
