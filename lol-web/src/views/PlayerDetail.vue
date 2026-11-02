<template>
  <div class="player-page">
    <header class="bar">
      <button class="back" type="button" @click="goBack">
        <span aria-hidden="true">←</span> 返回
      </button>
      <span class="bar-title">
        <span class="mark" aria-hidden="true" />
        玩家资料
      </span>
    </header>

    <div class="wrap">
      <el-card v-if="loadingProfile && !player" shadow="never">
        <el-skeleton :rows="5" animated />
      </el-card>

      <el-card v-else-if="!player" shadow="never">
        <p class="empty-title">没有找到这位玩家</p>
        <p class="empty-hint">
          这个用户可能已经被删除，或者链接里的地址不对。回到首页可以重新找队友。
        </p>
        <el-button @click="goHome">回到首页</el-button>
      </el-card>

      <template v-else>
        <el-card shadow="never">
          <PlayerSummary :user="player" />

          <div v-if="!isMe" class="friend-action">
            <el-button v-if="alreadyFriend" plain disabled>已是好友</el-button>
            <el-button v-else type="primary" :loading="addingFriend" @click="doAddFriend">
              加好友
            </el-button>
            <span class="friend-hint">
              {{ alreadyFriend
                ? '点右上角「好友」可以邀请他加入你的房间。'
                : '加为好友后，他那边会立刻收到提醒和提示音。之后就能邀请他一起开黑。' }}
            </span>
          </div>

          <p v-if="isMe" class="me-note">这是你自己的资料。要修改就去首页左侧的「编辑资料」。</p>
        </el-card>

        <el-card shadow="never">
          <div class="card-head">
            <h3>资料</h3>
            <p class="eyebrow">他自己填写的内容</p>
          </div>

          <div class="info">
            <div class="row">
              <span class="k">大区</span>
              <span class="v">{{ player.region || '未填' }}</span>
            </div>
            <div class="row">
              <span class="k">主玩位置</span>
              <span class="v">{{ positionLabel(player.position) }}</span>
            </div>
            <div class="row">
              <span class="k">段位</span>
              <span class="v">{{ rankLabel(player.rankTier) }}</span>
            </div>
            <div class="row">
              <span class="k">召唤师名</span>
              <span class="v">{{ player.summonerName || '未填' }}</span>
            </div>
            <div class="row">
              <span class="k">性别</span>
              <span class="v">{{ player.gender || '未填' }}</span>
            </div>
            <div class="row">
              <span class="k">最近活跃</span>
              <span class="v">{{ lastActiveText }}</span>
            </div>
          </div>

          <div class="bio">
            <span class="k">签名</span>
            <p class="bio-text">{{ player.bio || '还没写签名' }}</p>
          </div>

          <p class="note">
            以上都是他自己填的。下面的对局记录是从他的游戏客户端读出来的，没法手改。
          </p>
        </el-card>

        <el-card shadow="never">
          <div class="card-head">
            <h3>对局记录</h3>
            <p class="eyebrow">
              <template v-if="player.matchCount">共录入 {{ player.matchCount }} 场，按时间倒序</template>
              <template v-else>还没有战绩数据</template>
            </p>
          </div>

          <ul v-if="recent" class="stats">
            <li>
              <span class="stat-num num">{{ recent.wins }}胜{{ recent.losses }}负</span>
              <span class="stat-label">最近 {{ recent.n }} 场</span>
            </li>
            <li>
              <span class="stat-num num">{{ recent.winRate }}%</span>
              <span class="stat-label">胜率</span>
            </li>
            <li>
              <span class="stat-num num">{{ recent.kda }}</span>
              <span class="stat-label">场均 KDA</span>
            </li>
            <li>
              <span class="stat-num">{{ positionLabel(recent.topLane) }}</span>
              <span class="stat-label">最近常玩位置</span>
            </li>
          </ul>

          <MatchRecordTable
            :records="records"
            :total="total"
            :size="size"
            :page="page"
            :loading="loadingMatches"
            @update:page="loadMatches"
          />
        </el-card>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserProfile } from '../api/user'
import { getUserMatches } from '../api/match'
import { addFriend, isFriend as checkFriendApi } from '../api/friend'
import { positionLabel, rankLabel } from '../constants'
import PlayerSummary from '../components/PlayerSummary.vue'
import MatchRecordTable from '../components/MatchRecordTable.vue'

const route = useRoute()
const router = useRouter()

const player = ref(null)
// 资料和对局是两条独立的请求，加载状态必须分开，
// 否则资料先回来时会把还在转的战绩骨架屏一起关掉。
const loadingProfile = ref(false)
const loadingMatches = ref(false)

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 10

// 「最近 N 场」小结固定用第一页算，翻页时不跟着变 —— 否则每翻一页四个数字全跳一次，
// 而用户想看的是"最近状态"，不是"这一页的平均值"。
const recent = ref(null)

const myId = (JSON.parse(localStorage.getItem('user') || 'null') || {}).id
const isMe = computed(() => Boolean(myId) && Boolean(player.value) && player.value.id === myId)

// 好友状态。unknown 表示还没问出来 —— 这期间按钮先按「加好友」显示，
// 不要先显示一个"已是好友"再跳回去。
const friendState = ref('unknown')
const addingFriend = ref(false)
const alreadyFriend = computed(() => friendState.value === 'friend')

const lastActiveText = computed(() => {
  const iso = player.value && player.value.lastActiveAt
  if (!iso) return '暂无记录'
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return '暂无记录'
  const diff = Date.now() - t
  if (diff < 60 * 1000) return '刚刚'
  const min = Math.floor(diff / 60000)
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 30) return `${day} 天前`
  return iso.slice(0, 10)
})

function loadProfile() {
  const id = route.params.id
  if (!id) return
  loadingProfile.value = true
  getUserProfile(id)
    .then(res => { player.value = res.data || null })
    .catch(() => { player.value = null })
    .finally(() => {
      loadingProfile.value = false
      loadFriendState()
    })
}

/** 只问「是不是好友」，不拉整个好友列表 —— 资料页每打开一次都拉一遍太浪费 */
function loadFriendState() {
  if (!player.value || isMe.value) {
    friendState.value = 'unknown'
    return
  }
  checkFriendApi(player.value.id, { silent: true })
    .then(res => { friendState.value = res.data ? 'friend' : 'none' })
    .catch(() => { friendState.value = 'none' })
}

function doAddFriend() {
  if (!player.value) return
  addingFriend.value = true
  addFriend(player.value.id)
    .then(() => {
      friendState.value = 'friend'
      ElMessage.success(`已把 ${player.value.nickname || '对方'} 加为好友`)
    })
    .catch(() => {})
    .finally(() => { addingFriend.value = false })
}

function loadMatches(targetPage = 1) {
  const id = route.params.id
  if (!id) return
  page.value = targetPage
  loadingMatches.value = true
  getUserMatches(id, { page: targetPage, size })
    .then(res => {
      const list = (res.data && res.data.list) || []
      records.value = list
      total.value = (res.data && res.data.total) || 0
      if (targetPage === 1) recent.value = summarize(list)
    })
    .catch(() => {
      records.value = []
      total.value = 0
      if (targetPage === 1) recent.value = null
    })
    .finally(() => { loadingMatches.value = false })
}

/** 最近一页战绩的快速小结：胜率、场均 KDA、最常玩的位置 */
function summarize(list) {
  if (!list || list.length === 0) return null
  const wins = list.filter(r => r.win).length
  const kdaSum = list.reduce((sum, r) => sum + (r.kda || 0), 0)
  const laneCount = {}
  list.forEach(r => {
    if (r.lane) laneCount[r.lane] = (laneCount[r.lane] || 0) + 1
  })
  const topLane = Object.keys(laneCount).sort((a, b) => laneCount[b] - laneCount[a])[0] || ''
  return {
    n: list.length,
    wins,
    losses: list.length - wins,
    winRate: Math.round((wins / list.length) * 100),
    kda: (kdaSum / list.length).toFixed(2),
    topLane
  }
}

function goBack() {
  // 直接打开链接进来的话没有上一页，退回首页更合理
  if (window.history.length > 1) router.back()
  else router.push('/')
}

function goHome() {
  router.push('/')
}

watch(
  () => route.params.id,
  () => {
    if (route.name !== 'player') return
    player.value = null
    recent.value = null
    page.value = 1
    loadProfile()
    loadMatches(1)
  },
  { immediate: true }
)
</script>

<style scoped>
.player-page {
  min-height: 100vh;
}

.bar {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 60px;
  padding: 0 20px;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back {
  appearance: none;
  background: transparent;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  color: var(--text-dim);
  font-family: inherit;
  font-size: var(--fs-small);
  padding: 5px 12px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.back:hover {
  color: var(--text);
  border-color: var(--gold);
  background: var(--panel-hi);
}

.bar-title {
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 600;
}

.bar-title .mark {
  width: 9px;
  height: 9px;
  transform: rotate(45deg);
  background: var(--gold);
}

.wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 860px;
  margin: 0 auto;
  padding: 20px;
}

.card-head h3 {
  font-size: 17px;
}

.card-head .eyebrow {
  margin-top: 2px;
}

/* 加好友：按钮要显眼（这是资料页唯一的动作），说明文字跟在后面解释后果 */
.friend-action {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.friend-hint {
  flex: 1;
  min-width: 200px;
  font-size: var(--fs-micro);
  line-height: 1.65;
  color: var(--text-mute);
}

.me-note {
  margin-top: 14px;
  padding-left: 10px;
  border-left: 2px solid var(--gold);
  font-size: var(--fs-micro);
  line-height: 1.6;
  color: var(--text-dim);
}

.info {
  margin-top: 16px;
  font-size: var(--fs-small);
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line-soft);
}

.row:last-child {
  border-bottom: none;
}

.k {
  color: var(--text-mute);
  flex: none;
}

.v {
  color: var(--text);
  text-align: right;
  word-break: break-all;
}

.bio {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.bio-text {
  margin-top: 6px;
  font-size: var(--fs-small);
  line-height: 1.7;
  color: var(--text-dim);
}

.note {
  margin-top: 14px;
  font-size: var(--fs-micro);
  line-height: 1.6;
  color: var(--text-mute);
}

.stats {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0 0;
  padding: 0;
}

.stats li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 108px;
  padding: 9px 12px;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.stat-num {
  font-size: 17px;
  font-weight: 600;
  color: var(--teal);
}

.stat-label {
  font-size: var(--fs-micro);
  color: var(--text-mute);
}

.empty-title {
  font-weight: 600;
}

.empty-hint {
  margin: 8px 0 16px;
  font-size: var(--fs-small);
  line-height: 1.7;
  color: var(--text-mute);
  max-width: 52ch;
}

@media (max-width: 640px) {
  .bar {
    padding: 0 14px;
  }

  .wrap {
    padding: 14px;
    gap: 14px;
  }

  .stats li {
    min-width: calc(50% - 5px);
    flex: 1;
  }
}
</style>
