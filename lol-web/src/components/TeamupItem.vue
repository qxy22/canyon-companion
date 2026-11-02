<template>
  <article class="teamup-item" :class="{ 'is-mine': isMember }">
    <div class="top">
      <el-tag size="small" type="info" effect="plain">{{ modeLabel(teamup.gameMode) }}</el-tag>
      <el-tag v-if="teamup.status === 'CLOSED'" size="small" type="warning" effect="plain">已满员</el-tag>
      <el-tag v-if="unread > 0" size="small" type="danger" effect="dark">{{ unread }} 条未读</el-tag>
      <span class="time">{{ relativeTime(teamup.createdAt) }}</span>
      <span class="count num">{{ teamup.currentSize }} / {{ teamup.maxSize }} 人</span>
    </div>

    <!-- 组员一览：头像上的小圆点是他的自报状态，点一下进资料页 -->
    <div class="members">
      <span v-for="m in members" :key="m.id" class="member-wrap">
        <button type="button" class="member" :title="`查看 ${m.nickname} 的资料`" @click="emit('open-player', m)">
          <span class="avatar-wrap">
            <el-avatar :size="26" :src="m.avatar">{{ initial(m) }}</el-avatar>
            <span class="dot" :class="statusClass(m.status)" :title="statusLabel(m.status)" />
          </span>
          <span class="member-text">
            <span class="member-name">
              {{ m.nickname || '未知玩家' }}
              <em v-if="isOwnerMember(m)" class="badge">队长</em>
              <em v-if="m.id === myId" class="badge is-me">我</em>
            </span>
            <span class="member-sub">{{ memberSub(m) }}</span>
          </span>
        </button>

        <!-- 踢人只对队长可见，且不能踢自己（自己退出走「退出队伍」） -->
        <button
          v-if="canKick(m)"
          type="button"
          class="kick"
          :aria-label="`把 ${m.nickname} 移出队伍`"
          :title="`把 ${m.nickname} 移出队伍`"
          @click="emit('kick', { id: teamup.id, user: m })"
        >
          ×
        </button>
      </span>

      <span v-for="i in emptySlots" :key="`e${i}`" class="slot">空缺</span>
      <span v-if="spillSlots > 0" class="slot more">还有 {{ spillSlots }} 个空位</span>
    </div>

    <div class="row">
      <span class="row-k">缺</span>
      <template v-if="teamup.positions && teamup.positions.length">
        <el-tag v-for="p in teamup.positions" :key="p" size="small" effect="plain">{{ positionLabel(p) }}</el-tag>
      </template>
      <span v-else class="row-v">位置不限</span>
      <span class="slots">{{ slotsLeft > 0 ? `还差 ${slotsLeft} 人` : '已满员' }}</span>
    </div>

    <p class="desc">{{ teamup.description || '队长没有写说明' }}</p>

    <div class="foot">
      <!-- 在队伍里的人只需要一个出口，再给他一个「加入」按钮是自相矛盾的 -->
      <template v-if="isMember">
        <router-link :to="`/teamup/${teamup.id}`" class="enter">
          <el-button size="small" type="primary">进入房间</el-button>
        </router-link>
        <el-button
          size="small"
          type="danger"
          plain
          :loading="busy === 'leave'"
          @click="emit('leave', teamup.id)"
        >
          退出队伍
        </el-button>
        <span v-if="isOwner" class="foot-hint">
          {{ members.length > 1 ? '你是队长，退出后队长会交给最早加入的成员' : '你是唯一成员，退出后队伍解散' }}
        </span>
      </template>

      <el-button
        v-else
        size="small"
        type="primary"
        :disabled="!canJoin"
        :loading="busy === 'join'"
        @click="emit('join', teamup.id)"
      >
        {{ joinLabel }}
      </el-button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { POSITIONS, positionLabel, rankShort, statusLabel, modeLabel } from '../constants'

const props = defineProps({
  teamup: { type: Object, required: true },
  myId: { type: Number, default: null },
  // 'join' | 'leave' | null —— 正在进行的那一个操作，用来只在这条上转圈
  busy: { type: String, default: null },
  // 这个房间里有多少条我没读的消息
  unread: { type: Number, default: 0 }
})

const emit = defineEmits(['join', 'leave', 'kick', 'open-player'])

// 空位最多画 4 个，再多就折成一行文字，避免 10 人队铺出九宫格
const MAX_SLOT_PLACEHOLDERS = 4

const members = computed(() => props.teamup.members || [])

const isMember = computed(() => members.value.some(m => m.id === props.myId))
const isOwner = computed(() => Boolean(props.teamup.owner) && props.teamup.owner.id === props.myId)

const slotsLeft = computed(() =>
  Math.max((props.teamup.maxSize || 0) - (props.teamup.currentSize || 0), 0)
)
const emptySlots = computed(() => Math.min(slotsLeft.value, MAX_SLOT_PLACEHOLDERS))
const spillSlots = computed(() => Math.max(slotsLeft.value - MAX_SLOT_PLACEHOLDERS, 0))

const canJoin = computed(() => props.teamup.status === 'OPEN' && !isMember.value)

const joinLabel = computed(() => {
  if (props.teamup.status !== 'OPEN') return '已满员'
  return '加入队伍'
})

function isOwnerMember(m) {
  return Boolean(props.teamup.owner) && props.teamup.owner.id === m.id
}

function canKick(m) {
  return isOwner.value && m.id !== props.myId
}

function initial(user) {
  const name = user && user.nickname ? user.nickname : 'L'
  return (name.charAt(0) || 'L').toUpperCase()
}

/** 头像下方的两行小字，没填的字段直接不占位 */
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

/** 发布时间的粗粒度相对时间，够用且不需要额外依赖 */
function relativeTime(iso) {
  if (!iso) return ''
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return ''
  const diff = Date.now() - t
  if (diff < 60 * 1000) return '刚刚发布'
  const min = Math.floor(diff / 60000)
  if (min < 60) return `${min} 分钟前发布`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前发布`
  return `${Math.floor(hour / 24)} 天前发布`
}
</script>

<style scoped>
/* 悬停热区左右各外开 10px。这一行本来就有多种可点元素，
   热区比正文宽一圈，鼠标扫过去更容易命中，也让条目们左右对齐成一列 */
.teamup-item {
  margin: 0 -10px;
  padding: 14px 10px;
  border-bottom: 1px solid var(--line-soft);
}

.teamup-item:last-child {
  border-bottom: none;
}

.teamup-item:hover {
  background: var(--panel-hi);
}

/* 我所在的队伍给一道金色左边线，扫一眼就知道哪几支跟自己有关 */
.teamup-item.is-mine {
  border-left: 2px solid var(--gold);
  padding-left: 8px;
}

.top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.time {
  font-size: var(--fs-micro);
  color: var(--text-mute);
}

.count {
  margin-left: auto;
  font-size: var(--fs-micro);
  color: var(--text-dim);
}

/* 组员条：一行能放下 2~3 个成员，放不下就换行 */
.members {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.member-wrap {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.member {
  appearance: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 10px 5px 6px;
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

/* 移除成员：跟资料跳转分开放，避免把两个动作塞进同一个按钮里 */
.kick {
  appearance: none;
  width: 22px;
  height: 22px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: transparent;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--text-mute);
  font-family: inherit;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.kick:hover {
  color: var(--ember);
  border-color: var(--ember);
  background: var(--panel);
}

.avatar-wrap {
  position: relative;
  flex: none;
  display: block;
  line-height: 0;
}

/* 状态点：颜色沿用全站分工（青=在线，金=游戏中，灰=离线） */
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
  max-width: 9em;
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
  white-space: nowrap;
}

.slot {
  display: flex;
  align-items: center;
  padding: 0 12px;
  min-height: 38px;
  font-size: var(--fs-micro);
  color: var(--text-mute);
  border: 1px dashed var(--line);
  border-radius: 999px;
}

.slot.more {
  border-style: solid;
  border-color: transparent;
  padding: 0 4px;
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
  flex: none;
}

.row-v {
  color: var(--text-dim);
}

.slots {
  margin-left: auto;
  font-size: var(--fs-micro);
  color: var(--teal);
}

.desc {
  margin-top: 8px;
  font-size: var(--fs-small);
  line-height: 1.6;
  color: var(--text-dim);
}

.foot {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.foot-hint {
  font-size: var(--fs-micro);
  color: var(--text-mute);
  line-height: 1.5;
}

.enter {
  text-decoration: none;
}
</style>
