<template>
  <el-card shadow="never">
    <div class="head">
      <div class="head-text">
        <h3>找队友</h3>
        <p class="eyebrow">段位相近 · 位置互补</p>
      </div>
      <el-button text :loading="loading" @click="manualLoad">刷新</el-button>
    </div>

    <el-skeleton v-if="firstLoad" :rows="4" animated />

    <div v-else-if="list.length === 0" class="empty">
      <p class="empty-title">暂时没有可推荐的队友</p>
      <p class="empty-hint">
        推荐范围只有在线的玩家。现在没人在线，或者你自己的段位和位置还没同步时，这里就会是空的。
      </p>
    </div>

    <div v-else class="list">
      <div
        v-for="item in list"
        :key="item.user.id"
        class="match-item"
        @click="openPlayerDialog(item.user)"
      >
        <el-avatar :size="36" :src="item.user.avatar">{{ initial(item.user) }}</el-avatar>

        <div class="main">
          <div class="line1">
            <span class="nickname">{{ item.user.nickname }}</span>
            <span class="score num">{{ item.matchScore }}</span>
          </div>

          <!-- 位置 · 段位 写成一行短句。窄栏里塞两个标签会把昵称挤到换行 -->
          <p class="meta">{{ summary(item.user) }}</p>

          <!-- 匹配度条：把那个数字变成长短，扫一眼就能比高低，比读数字省力 -->
          <div class="gauge" role="img" :aria-label="`匹配度 ${item.matchScore}`">
            <i :style="{ width: item.matchScore + '%' }" />
          </div>

          <ul v-if="item.reasons && item.reasons.length" class="reasons">
            <li v-for="r in item.reasons.slice(0, 2)" :key="r">{{ r }}</li>
          </ul>
        </div>
      </div>
    </div>

    <Pager v-model:page="page" :size="SIZE" :total="total" />
  </el-card>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { recommend } from '../api/match'
import { positionLabel, rankLabel } from '../constants'
import { openPlayerDialog } from '../composables/usePlayerDialog'
import { refreshTick } from '../composables/useDataRefresh'
import { useAutoRefresh } from '../composables/useAutoRefresh'
import Pager from './Pager.vue'

const SIZE = 10

const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
// 骨架屏只在第一次出现。之后每 2 秒静默换数据，闪一下反而更难看
const firstLoad = ref(true)

function initial(user) {
  const name = user && user.nickname ? user.nickname : 'L'
  return (name.charAt(0) || 'L').toUpperCase()
}

/**
 * 只留位置与段位两样 —— 这两样决定能不能一起玩，别的都在资料卡里。
 *
 * positionLabel / rankLabel 对空值分别回落到「未填位置」「未定段」，
 * 窄栏这一行里同时写两个"没填"太占地方，所以两样都缺时换成一句整的话。
 */
const UNSURE = ['未填位置', '未定段']

function summary(user) {
  const parts = [positionLabel(user && user.position), rankLabel(user && user.rankTier)]
    .filter(s => s && !UNSURE.includes(s))
  return parts.join(' · ') || '资料还没同步'
}

/**
 * isManual = true 是用户点的（点刷新、刚翻页），可以转圈；
 * 自动刷新的那次一律静默：不弹错误提示，也不闪界面。
 */
function load(isManual) {
  if (isManual) loading.value = true
  return recommend({ page: page.value, size: SIZE }, { silent: !isManual })
    .then(res => {
      const data = res.data || {}
      list.value = data.list || []
      total.value = data.total || 0
      // 有人下线后总数会变少，当前页可能已经越过最后一页，收回来
      const pages = Math.max(1, Math.ceil(total.value / SIZE))
      if (page.value > pages) page.value = pages
    })
    .catch(() => {
      // 保留现有内容：网络抖一下不该把页面清空
    })
    .finally(() => {
      if (isManual) loading.value = false
      firstLoad.value = false
    })
}

const { refresh } = useAutoRefresh(load, 2000)

function manualLoad() {
  refresh()
}

onMounted(() => load(true))

// 翻完页立刻拿新数据，不等下一轮 2 秒
watch(page, () => refresh())

// 自己的段位/位置变了，推荐结果也就不一样了
watch(refreshTick, () => refresh())
</script>

<style scoped>
:deep(.el-card__body) {
  padding: 16px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line-soft);
}

.head h3 {
  font-size: 17px;
}

.eyebrow {
  margin-top: 2px;
}

.empty {
  padding: 20px 0 4px;
}

.empty-title {
  font-weight: 600;
}

.empty-hint {
  margin-top: 6px;
  font-size: var(--fs-small);
  line-height: 1.7;
  color: var(--text-mute);
}

.list {
  margin-top: 2px;
}

/* 悬停热区向外开 10px：只剩 316px 的这一列如果让 hover 矩形贴齐文字，
   会显得局促；让它比文字宽一圈反而更整齐 */
.match-item {
  display: flex;
  gap: 10px;
  margin: 0 -10px;
  padding: 12px 10px;
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer;
}

.match-item:last-child {
  border-bottom: none;
}

.match-item:hover {
  background: var(--panel-hi);
}

.main {
  flex: 1;
  min-width: 0;
}

.line1 {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}

.nickname {
  font-weight: 600;
  font-size: var(--fs-small);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 数字是数据，用青色；不用标签包是因为这列窄，包了就得多一层进度 */
.score {
  flex: none;
  color: var(--teal);
  font-weight: 600;
  font-size: 15px;
}

.meta {
  margin-top: 2px;
  font-size: var(--fs-micro);
  color: var(--text-mute);
}

/* 匹配度条：把成绩变成长短。比反复读数字省力，也让这列有一眼可辨的东西 */
.gauge {
  height: 3px;
  margin-top: 7px;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: 2px;
  overflow: hidden;
}

.gauge i {
  display: block;
  height: 100%;
  background: var(--teal);
  transition: width 0.3s ease;
}

.reasons {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.reasons li {
  font-size: 10px;
  color: var(--text-dim);
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: 3px;
  padding: 0 6px;
  line-height: 1.7;
}
</style>
