<!-- 首页头部课程分类 -->
<template>
    <div class="classCategory ft-14" :class="{ classCategoryHeader: type == 'float'}">
      <div @mouseout="mouseoutHandle()">
        <div class="items">
          <div class="item" v-for="item in data" :key="item.id" @mouseover="mouseoverHandle(item)" >
            <!-- 一级分类 -->
            <div class="fx-sb">
              <div @click="() => $router.push({path:'/search', query:{type:'categoryIdLv1',id:item.id}})" class="font-bt2" >{{item.name}}</div>
              <img src="@/assets/icon_more.png" alt="">
            </div>
            <!-- 二级分类前两个 -->
            <div class="desc ft-12 ft-cl-des">
              <span @click="() => $router.push({path:'/search', query:{type:'categoryIdLv2',id:item.children[0].id}})" class="font-bt2 ft-cl-des" v-if="item.children.length > 0">{{ item.children[0].name}}</span>
              <span v-if="item.children.length > 1"> / </span>
              <span @click="() => $router.push({path:'/search', query:{type:'categoryIdLv2',id:item.children[1].id}})" v-if="item.children.length > 1" class="font-bt2 ft-cl-des">{{  item.children[1].name}}</span>
            </div>
            <!-- 二级/三级详情面板：渲染在各一级项内部，position:absolute 相对该项定位，
                 left:100% 使面板左边缘紧贴一级项右边缘，鼠标水平右移即可进入，无 hover 空隙 -->
            <div class="allCategory" v-show="activeId === item.id">
              <div class="cont">
                <div class="fx ft-wt-600 pd-bt-10" v-for="cate in item.children" :key="cate.id">
                  <span class="tit font-bt2" @click="() => $router.push({path:'/search', query:{type:'categoryIdLv2',id:cate.id}})">{{cate.name}} :</span>
                  <div class="name fx-1">
                    <span class="ft-wt-400 cur-pt font-bt2" @click="() => $router.push({path:'/search', query:{type:'categoryIdLv3',id:it.id}})" v-for="it in cate.children" :key="it.id">{{it.name}}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
</template>
<script setup>
import {ref} from 'vue';
  // 当前悬停的一级分类 id（null = 全部收起）
  const activeId = ref(null);
  // 接收的全部分类
  const props = defineProps({
    data: {
        type: Array,
        default: []
    },
    type: {
      type: String,
      default: ''
    }
  })

  // 鼠标滑过一级分类：记录当前项，面板随之在该项右侧展开
  const mouseoverHandle = (item) => {
    activeId.value = item ? item.id : null
  }

  const mouseoutHandle = () => {
    activeId.value = null
  }
  
</script>
<style lang="scss" scoped>
.classCategory, .classCategoryHeader{
  position: relative;
  .items{
    // 自适应高度：随一级分类数量增长（原固定 312px 只容纳 4 项）
    min-height: 312px;
  }
  .item{
    position: relative; /* 作为二级面板的定位锚点 */
    padding:15px;
    height: 78px;
    border-bottom: solid 1px #eeeeee;
    img{
      width: 20px;
      height: 20px;
    }
    .desc{
      padding-top: 8px;
    }
    /* 靠下方的一级项（倒数 2 个）：面板改为底部对齐，避免向下溢出视口 */
    &:nth-last-child(-n + 2) .allCategory{
      top: auto;
      bottom: 0;
    }
    &:hover{
      background-color: var(--color-background2);
    }
    &:first-child{
      border-radius: 8px 8px 0 0;
    }
    &:nth-child(4){
      border-radius: 0;
    }
    &:last-child{
      border-radius: 0 0 8px 8px;
      border-bottom: none;
    }
  }
  .allCategory{
    position: absolute;
    /* 相对被悬停的一级项定位：左边缘紧贴该项右边缘（left:100%），顶部对齐该项顶部，
       鼠标水平右移即可进入面板，不存在 hover 空隙 */
    z-index: 10; /* 面板必须盖过后续兄弟 .item（均为 relative 定位参与堆叠） */
    top: 0;
    left: 100%;
    width: 537px;
    overflow-y: auto;
    max-height: 390px;
    padding: 20px 30px 20px 20px;
    background: #FFFFFF;
    box-shadow: var(--shadow-card-hover);
    border-radius: var(--radius-md);
    .cont{
      // 随分类内容自适应，超出由外层滚动
    }
    .tit{
      display: inline-block;
      width: 120px;
      overflow: hidden;
      height: 20px;
      border-right:none;
    }
    span{
      display: inline-block;
      padding: 0 10px 0 10px;
      border-right: solid 1px var(--color-font5);
      line-height: 16px;
      margin-bottom: 10px;
      &:last-child{
        border-right:none;
      }
    }
  }
}
.classCategoryHeader{
  .items{
    min-height: 312px;
    width: 236px;
    overflow: inherit;
    box-shadow: 0 4px 6px 2px rgba(108,112,118,0.17);
    border-radius: 8px;
    .item:first-child{
      position: relative;
      &::before{
        content: '';
        display: inline-block;
        width: 15px;
        height: 15px;
        position: absolute;
        z-index: -1;
        background-color: #fff;
        top: -6px;
        left: 44%;
        transform: rotate(45deg);
        box-shadow: 4px 4px 6px 2px rgba(108,112,118,0.3);
        // border:solid 1px #E3E5E9;
      }
      &:hover{
        background-color: var(--color-background2);
        &::before{
          background-color: var(--color-background2);
        }
      }
    }
  }
}
</style>
