<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="386px"
    :with-header="false"
    append-to-body
    class="friend-drawer"
  >
    <div class="head">
      <div class="head-text">
        <h3>好友</h3>
        <p class="eyebrow">
          <template v-if="friends.length">共 {{ friends.length }} 位</template>
          <template v-else>加好友后可以邀请他一起开黑</template>
        </p>
      </div>

      <el-tooltip :content="soundOn ? '提示音已开启，点击关闭' : '提示音已关闭，点击开启'" placement="bottom">
        <button
          class="sound"
          type="button"
          :aria-pressed="soundOn"
          :aria-label="soundOn ? '关闭提示音' : '开启提示音'"
          @click="toggleSound"
        >
          <svg
            width="18" height="18" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" stroke-width="1.6"
            stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
          >
            <path d="M18 8a6 6 0 0 0-12 0c0 7-3 8-3 8h18s-3-1-3-8" />
            <path d="M13.7 21a2 2 0 0 1-3.4 0" />
            <path v-if="!soundOn" d="M3 3l18 18" />
          </svg>
        </button>
      </el-tooltip>
    </div>

    <p class="team-hint" :class="{ off: !myTeam }">
      <template v-if="!myTeam">
        你还没有加入队伍。发布或加入一个队伍后，才能邀请好友。
      </template>
      <template v-else-if="teamFull">
        你的队伍已经满了（{{ myTeam.currentSize }}/{{ myTeam.maxSize }}），空出位置后才能邀请。
      </template>
      <template v-else>
        你在「{{ modeLabel(myTeam.gameMode) }}」的房间里（{{ myTeam.currentSize }}/{{ myTeam.maxSize }}），
        可以邀请下面的好友加入。
      </template>
    </p>

    <el-skeleton v-if="loading && friends.length === 0" :rows="4" animated />

    <div v-else-if="friends.length === 0" class="empty">
      <p class="empty-title">还没有好友</p>
      <p class="empty-hint">
        在任意玩家的资料页点「加好友」，对方会立刻收到提醒和提示音。
        加完之后回到这里，就能邀请他加入你的房间。
      </p>
    </div>

    <div v-else class="list">
      <div v-for="f in friends" :key="f.id" class="item">
        <div class="who" @click="goPlayer(f)">
          <el-avatar :size="38" :src="f.avatar">{{ initial(f) }}</el-avatar>
          <div class="main">
            <div class="line1">
              <span class="nickname">{{ f.nickname }}</span>
              <span class="dot" :class="f.status" aria-hidden="true" />
              <span class="status">{{ statusLabel(f.status) }}</span>
            </div>
            <div class="meta">{{ summary(f) }}</div>
          </div>
        </div>

        <div class="actions">
          <el-button
            size="small"
            :disabled="!canInvite"
            :loading="invitingId === f.id"
            @click="invite(f)"
          >
            邀请
          </el-button>
          <el-button size="small" text type="danger" @click="confirmRemove(f)">删除</el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { friendList, inviteFriend, removeFriend } from '../api/friend'
import { myTeamups } from '../api/teamup'
import { modeLabel, positionLabel, rankLabel, statusLabel } from '../constants'
import { friendDrawer, closeFriendDrawer } from '../composables/useFriendDrawer'
import { useAutoRefresh } from '../composables/useAutoRefresh'
import { refreshTick } from '../composables/useDataRefresh'
import { isSoundEnabled, setSoundEnabled } from '../utils/sound'

const router = useRouter()

const visible = computed({
  get: () => friendDrawer.visible,
  set: v => {
    if (!v) closeFriendDrawer()
    else friendDrawer.visible = true
  }
})

const friends = ref([])
const myTeam = ref(null)
const loading = ref(false)
const invitingId = ref(null)
const soundOn = ref(isSoundEnabled())

const teamFull = computed(() => {
  const t = myTeam.value
  return Boolean(t) && t.currentSize >= t.maxSize
})
const canInvite = computed(() => Boolean(myTeam.value) && !teamFull.value)

// 抽屉关着的时候不轮询 —— 看得见才值得刷
const { setPaused } = useAutoRefresh(() => load(false), 10000)

onMounted(() => setPaused(true))

watch(
  () => friendDrawer.visible,
  open => {
    setPaused(!open)
    if (open) load(true)
  }
)

// 刚加完好友 / 被踢出队伍之后，列表要跟着变
watch(refreshTick, () => {
  if (friendDrawer.visible) load(false)
})

function load(isManual) {
  if (isManual) loading.value = true
  // 好友列表和"我在哪支队"是两件事，一个失败不该清空另一个
  return Promise.allSettled([
    friendList({ silent: true }),
    myTeamups({ silent: true })
  ]).then(([list, mine]) => {
    friends.value = list.status === 'fulfilled' ? (list.value.data || []) : []
    const teams = mine.status === 'fulfilled' ? (mine.value.data || []) : []
    myTeam.value = teams[0] || null
  }).finally(() => {
    loading.value = false
  })
}

function initial(user) {
  const name = user && user.nickname ? user.nickname : 'L'
  return (name.charAt(0) || 'L').toUpperCase()
}

function summary(user) {
  const pos = positionLabel(user && user.position)
  const rank = rankLabel(user && user.rankTier)
  if (pos && rank) return `${pos} · ${rank}`
  return pos || rank || '资料还没同步'
}

function toggleSound() {
  soundOn.value = !soundOn.value
  setSoundEnabled(soundOn.value)
}

function goPlayer(user) {
  closeFriendDrawer()
  router.push(`/player/${user.id}`)
}

async function invite(user) {
  invitingId.value = user.id
  try {
    await inviteFriend(user.id)
    ElMessage.success(`已邀请 ${user.nickname || '好友'}，等对方接受`)
  } catch {
    // 具体原因（没队伍 / 队伍满了 / 对方在别的队伍）由请求拦截器提示
  } finally {
    invitingId.value = null
  }
}

async function confirmRemove(user) {
  try {
    await ElMessageBox.confirm(
      `确定把 ${user.nickname || '这位玩家'} 从好友列表里移除吗？移除后互相都看不到对方。`,
      '删除好友',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  removeFriend(user.id)
    .then(() => {
      friends.value = friends.value.filter(f => f.id !== user.id)
      ElMessage.success('已删除好友')
    })
    .catch(() => {})
}
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.head h3 {
  font-size: 18px;
}

.head .eyebrow {
  margin-top: 2px;
}

.sound {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: none;
  background: transparent;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  color: var(--text-dim);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.sound:hover,
.sound:focus-visible {
  color: var(--gold);
  border-color: var(--gold);
  background: var(--panel-hi);
}

.team-hint {
  margin: 12px 0 4px;
  padding: 9px 11px;
  border-left: 2px solid var(--teal);
  background: rgba(10, 200, 185, 0.06);
  font-size: var(--fs-micro);
  line-height: 1.7;
  color: var(--text-dim);
}

.team-hint.off {
  border-left-color: var(--gold);
  background: rgba(200, 170, 110, 0.06);
}

.empty {
  padding: 26px 2px;
}

.empty-title {
  font-weight: 600;
}

.empty-hint {
  margin-top: 7px;
  font-size: var(--fs-small);
  line-height: 1.75;
  color: var(--text-mute);
}

.list {
  margin-top: 8px;
}

.item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 10px;
  margin: 0 -10px;
  border-bottom: 1px solid var(--line-soft);
  border-radius: var(--radius-sm);
}

.item:hover {
  background: var(--panel-hi);
}

.who {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.main {
  min-width: 0;
}

.line1 {
  display: flex;
  align-items: center;
  gap: 6px;
}

.nickname {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 在线状态用一个小点表示，比整句「在线」省地方 */
.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-mute);
  flex: none;
}

.dot.ONLINE {
  background: var(--teal);
}

.dot.BUSY {
  background: var(--ember);
}

.status {
  font-size: var(--fs-micro);
  color: var(--text-mute);
  flex: none;
}

.meta {
  margin-top: 3px;
  font-size: var(--fs-micro);
  color: var(--text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: none;
}

.actions :deep(.el-button + .el-button) {
  margin-left: 2px;
}
</style>
