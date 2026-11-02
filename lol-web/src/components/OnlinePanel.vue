<template>
  <el-card class="is-lead" shadow="never">
    <div class="head">
      <div class="head-text">
        <h3>在线可开黑<template v-if="total"> · {{ total }} 人</template></h3>
        <p class="eyebrow">状态为「在线」的人会出现在这里，每页 10 人</p>
      </div>
      <el-button text :loading="loading" @click="manualLoad">刷新</el-button>
    </div>

    <el-skeleton v-if="firstLoad" :rows="4" animated />

    <div v-else-if="list.length === 0" class="empty">
      <p class="empty-title">现在没人在线</p>
      <p class="empty-hint">
        把你自己的状态切到「在线」，你就会出现在这个列表里，别人也能看到你。
      </p>
    </div>

    <div v-else class="list">
      <div v-for="u in list" :key="u.id" class="online-item" @click="openPlayerDialog(u)">
        <el-avatar :size="36" :src="u.avatar">{{ initial(u) }}</el-avatar>
        <div class="main">
          <div class="nickname">{{ u.nickname }}</div>
          <div class="tags">
            <el-tag size="small" effect="plain">{{ positionLabel(u.position) }}</el-tag>
            <el-tag size="small" effect="plain" type="warning">{{ rankLabel(u.rankTier) }}</el-tag>
          </div>
        </div>
      </div>
    </div>

    <Pager v-model:page="page" :size="SIZE" :total="total" />
  </el-card>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { onlineList } from '../api/match'
import { positionLabel, rankLabel } from '../constants'
import { openPlayerDialog } from '../composables/usePlayerDialog'
import { refreshTick } from '../composables/useDataRefresh'
import { useAutoRefresh } from '../composables/useAutoRefresh'
import Pager from './Pager.vue'

const emit = defineEmits(['count'])

// 我在大厅里，但用户此刻看的是「开黑大厅」那一页
const props = defineProps({
  active: { type: Boolean, default: true }
})

const SIZE = 10

const list = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
// 骨架屏只在第一次出现，之后每 2 秒静默换数据
const firstLoad = ref(true)

function initial(user) {
  const name = user && user.nickname ? user.nickname : 'L'
  return (name.charAt(0) || 'L').toUpperCase()
}

function load(isManual) {
  if (isManual) loading.value = true
  return onlineList({ page: page.value, size: SIZE }, { silent: !isManual })
    .then(res => {
      const data = res.data || {}
      list.value = data.list || []
      total.value = data.total || 0
      emit('count', total.value)
      // 有人下线后总数会变少，当前页可能已经越过最后一页，收回来
      const pages = Math.max(1, Math.ceil(total.value / SIZE))
      if (page.value > pages) page.value = pages
    })
    .catch(() => {
      // 保留现有内容
    })
    .finally(() => {
      if (isManual) loading.value = false
      firstLoad.value = false
    })
}

const { refresh, setPaused } = useAutoRefresh(load, 2000)

function manualLoad() {
  refresh()
}

onMounted(() => {
  load(true)
  // 我一开始就是被切走的那一页，别跟着一起轮询
  if (!props.active) setPaused(true)
})

// 切到我这一页才开始刷新，切走就停
watch(() => props.active, on => setPaused(!on))

// 翻完页立刻拿新数据，不等下一轮 2 秒
watch(page, () => refresh())

// 自己切换了在线状态，这个列表必须跟着变 —— 否则用户会以为开关没生效
watch(refreshTick, () => refresh())
</script>

<style scoped>
/* 主栏：跟「开黑大厅」同一套 head 规格，切换过去时视觉位置不动 */
.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line-soft);
}

.head h3 {
  font-size: 20px;
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
  margin-top: 6px;
}

/* 跟找队友那列同一套：外开的热区，方便横向扫下行时有稳定的落点 */
.online-item {
  display: flex;
  gap: 10px;
  align-items: center;
  margin: 0 -10px;
  padding: 11px 10px;
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer;
}

.online-item:last-child {
  border-bottom: none;
}

.online-item:hover {
  background: var(--panel-hi);
}

.main {
  min-width: 0;
}

.nickname {
  font-weight: 600;
  font-size: var(--fs-small);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tags {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}
</style>
