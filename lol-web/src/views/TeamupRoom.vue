<template>
  <div class="room-page">
    <TopNav :profile="me" />

    <div class="room">
      <!-- 进出这个房间的两个动作放在最上面，一进房间就看得见。
           「返回大厅」只是离开页面、队伍还在；「退出房间」才是真的退队，两个不能混。 -->
      <div class="room-bar">
        <router-link to="/" class="bar-back">← 返回大厅</router-link>
        <el-button
          v-if="teamup"
          type="danger"
          plain
          size="small"
          :loading="leaving"
          @click="confirmLeave"
        >
          {{ leaveLabel }}
        </el-button>
      </div>

      <aside class="side">
        <el-card v-if="loading" shadow="never">
          <el-skeleton :rows="5" animated />
        </el-card>

        <!-- 被移出 / 队伍解散时，聊天那头会把具体原因送上来。
             这张卡片要把它说出来，否则用户只看到房间"没了"，不知道发生了什么。 -->
        <el-card v-else-if="!teamup" shadow="never">
          <div class="gone">
            <p class="gone-title">{{ leftReason ? '房间已关闭' : '进不去这个房间' }}</p>
            <p class="gone-hint">
              {{ leftReason || '只有队伍成员才能进房间。队伍可能已经解散，或者你还没有加入它。' }}
            </p>
            <router-link to="/" class="back-btn">回开黑大厅</router-link>
          </div>
        </el-card>

        <el-card v-else shadow="never">
          <div class="room-head">
            <el-tag size="small" type="info" effect="plain">{{ modeLabel(teamup.gameMode) }}</el-tag>
            <el-tag v-if="teamup.status === 'CLOSED'" size="small" type="warning" effect="plain">已满员</el-tag>
            <span class="size num">{{ teamup.currentSize }} / {{ teamup.maxSize }} 人</span>
          </div>

          <p class="desc">{{ teamup.description || '队长没有写说明' }}</p>

          <div class="row">
            <span class="row-k">缺</span>
            <template v-if="teamup.positions && teamup.positions.length">
              <el-tag v-for="p in teamup.positions" :key="p" size="small" effect="plain">{{ positionLabel(p) }}</el-tag>
            </template>
            <span v-else class="row-v">位置不限</span>
          </div>

          <el-divider />

          <h3 class="sec">房间里的人 · {{ members.length }}</h3>
          <div class="members">
            <button
              v-for="m in members"
              :key="m.id"
              type="button"
              class="member"
              :title="`查看 ${m.nickname} 的资料`"
              @click="goPlayer(m)"
            >
              <span class="avatar-wrap">
                <el-avatar :size="30" :src="m.avatar">{{ initial(m) }}</el-avatar>
                <span class="dot" :class="statusClass(m.status)" :title="statusLabel(m.status)" />
              </span>
              <span class="member-text">
                <span class="member-name">
                  {{ m.nickname || '未知玩家' }}
                  <em v-if="isOwner(m)" class="badge">队长</em>
                  <em v-if="m.id === myId" class="badge is-me">我</em>
                </span>
                <span class="member-sub">{{ memberSub(m) }}</span>
              </span>
            </button>
          </div>

        </el-card>
      </aside>

      <!-- 房间没了的时候这里不渲染任何东西：左边那张卡片已经把原因说清楚了，
           右边再放一块「房间加载中…」就是在骗人 -->
      <main v-if="teamup" class="main">
        <ChatPanel :teamup-id="teamup.id" @left="onLeft" />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myTeamups, leaveTeamup } from '../api/teamup'
import { getProfile } from '../api/user'
import { modeLabel, positionLabel, rankShort, statusLabel } from '../constants'
import { requestRefresh } from '../composables/useDataRefresh'
import TopNav from '../components/TopNav.vue'
import ChatPanel from '../components/ChatPanel.vue'

const route = useRoute()
const router = useRouter()

const myId = (JSON.parse(localStorage.getItem('user') || 'null') || {}).id

const teamup = ref(null)
const loading = ref(true)
const leaving = ref(false)
const me = ref({})
// 被移出 / 解散时聊天组件给的原因。有值就说明"曾经在里面，现在不在了"，
// 跟"从来就不是成员"要分开说
const leftReason = ref('')

const members = computed(() => (teamup.value && teamup.value.members) || [])
const isMine = computed(() => Boolean(teamup.value && teamup.value.owner) && teamup.value.owner.id === myId)

function isOwner(m) {
  return Boolean(teamup.value && teamup.value.owner) && teamup.value.owner.id === m.id
}

function initial(user) {
  const name = user && user.nickname ? user.nickname : 'L'
  return (name.charAt(0) || 'L').toUpperCase()
}

function memberSub(m) {
  const parts = []
  if (m.position) parts.push(positionLabel(m.position))
  if (m.rankTier) parts.push(rankShort(m.rankTier))
  return parts.length ? parts.join(' · ') : '资料未填'
}

function statusClass(status) {
  if (status === 'ONLINE') return 'is-online'
  if (status === 'BUSY') return 'is-busy'
  return 'is-offline'
}

function goPlayer(member) {
  if (member && member.id) router.push(`/player/${member.id}`)
}

/**
 * 只从「我的队伍」里找这个房间。
 * 这样做的好处是成员身份顺带被验证了 —— 不是我所在的队伍，这里就查不到。
 */
function load() {
  loading.value = true
  return myTeamups()
    .then(res => {
      const list = res.data || []
      teamup.value = list.find(t => String(t.id) === String(route.params.id)) || null
      // 还能查到房间就说明确实还是成员，之前的原因不该继续挂着
      if (teamup.value) leftReason.value = ''
    })
    .catch(() => { teamup.value = null })
    .finally(() => { loading.value = false })
}

/** 房间里只剩自己时，退出等于解散 —— 按钮上就该这么写，别让人以为只是离开 */
const leaveLabel = computed(() => (members.value.length <= 1 ? '解散房间' : '退出房间'))

/**
 * 三种身份三种后果，后端就是这么实现的（TeamupService.leave）：
 * 最后一人 → 删队伍；队长 → 队长移交给最早加入的人；普通成员 → 空出位置。
 */
const leaveBody = computed(() => {
  if (members.value.length <= 1) {
    return '你是房间里最后一个人，退出会解散这支队伍，聊天记录和房间里的图片会一起清掉。'
  }
  if (isMine.value) {
    return '你是队长，退出后队长会交给最早加入的成员，房间和你发的消息都还在。'
  }
  return '退出后房间里的位置会立刻空出来，其他人不受影响。'
})

async function confirmLeave() {
  const last = members.value.length <= 1
  try {
    await ElMessageBox.confirm(leaveBody.value, leaveLabel.value, {
      type: 'warning',
      confirmButtonText: last ? '解散并退出' : '退出房间',
      cancelButtonText: '再想想'
    })
  } catch {
    // 点了「再想想」，什么都不做
    return
  }

  leaving.value = true
  leaveTeamup(teamup.value.id)
    .then(res => {
      // 返回 null 表示自己是最后一人、队伍已经删掉了
      ElMessage.success(res && res.data ? '已退出房间' : '已退出，队伍已解散')
      // 大厅的「我的队伍」和招募列表都要跟着变
      requestRefresh()
      router.push('/')
    })
    .catch(() => {})
    .finally(() => { leaving.value = false })
}

/** 聊天那头收到「你已不在队伍里」：房间信息刷掉，卡片上会写出具体原因 */
function onLeft(reason) {
  leftReason.value = reason || ''
  teamup.value = null
}

onMounted(() => {
  load()
  getProfile().then(res => { me.value = res.data || {} }).catch(() => {})
})

// 从大厅点进来时路由参数会变，跟着换房间
watch(() => route.params.id, load)
</script>

<style scoped>
.room-page {
  min-height: 100vh;
}

.room {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  padding: 20px;
  max-width: 1160px;
  margin: 0 auto;
}

.main {
  min-width: 0;
}

.room-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.size {
  margin-left: auto;
  font-size: var(--fs-micro);
  color: var(--text-dim);
}

.desc {
  margin-top: 10px;
  font-size: var(--fs-small);
  line-height: 1.6;
  color: var(--text-dim);
}

.row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  font-size: var(--fs-small);
}

.row-k {
  color: var(--text-mute);
}

.row-v {
  color: var(--text-dim);
}

.sec {
  font-size: var(--fs-small);
  font-weight: 600;
  color: var(--text-dim);
  letter-spacing: 0.04em;
  margin-bottom: 8px;
}

.members {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.member {
  appearance: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  width: 100%;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: 999px;
  color: inherit;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.member:hover {
  border-color: var(--teal);
  background: var(--panel);
}

.avatar-wrap {
  position: relative;
  flex: none;
  line-height: 0;
}

.dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 2px solid var(--ink);
}

.dot.is-online {
  background: var(--teal);
}

.dot.is-busy {
  background: var(--gold);
}

.dot.is-offline {
  background: rgba(240, 230, 210, 0.32);
}

.member-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.member-name {
  font-size: var(--fs-small);
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.badge {
  font-style: normal;
  font-size: 10px;
  font-weight: 400;
  color: var(--gold);
  border: 1px solid var(--gold-dim);
  border-radius: 3px;
  padding: 0 3px;
  margin-left: 3px;
  vertical-align: 1px;
}

.badge.is-me {
  color: var(--teal);
  border-color: rgba(10, 200, 185, 0.4);
}

.member-sub {
  font-size: var(--fs-micro);
  line-height: 1.3;
  color: var(--text-mute);
}

/* 横跨两栏压在顶部。房间是 grid，给它 1 / -1 才不会被塞进左边的格子里 */
.room-bar {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: -4px;
}

.bar-back {
  font-size: var(--fs-small);
  color: var(--gold);
  text-decoration: none;
  padding: 4px 2px;
  border-radius: var(--radius-sm);
}

.bar-back:hover {
  text-decoration: underline;
}

.bar-back:focus-visible {
  outline: 2px solid var(--gold);
  outline-offset: 2px;
}

.gone {
  padding: 10px 0;
}

.gone-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.gone-hint {
  font-size: var(--fs-small);
  line-height: 1.7;
  color: var(--text-mute);
}

.back-btn {
  display: inline-block;
  margin-top: 14px;
  color: var(--gold);
  text-decoration: none;
  font-size: var(--fs-small);
}

.back-btn:hover {
  text-decoration: underline;
}

/* 窄屏：房间信息挪到聊天上面 */
@media (max-width: 900px) {
  .room {
    grid-template-columns: minmax(0, 1fr);
    padding: 14px;
    gap: 14px;
  }
}
</style>
