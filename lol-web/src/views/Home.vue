<template>
  <div class="home">
    <TopNav :profile="profile" />

    <!-- 三栏。中间那栏最宽，因为发布/加入队伍是这个页面唯一的主要动作；
         找队友是"顺便看看"，所以放到旁边一列。

         DOM 顺序写成 资料 → 大厅 → 找队友，窄屏塌成单列时才符阅读顺序，
         宽屏再由 grid-template-areas 把它们摆到左 / 中 / 右。 -->
    <div class="body">
      <aside class="col lane-me">
        <el-card v-if="loading && !profile.nickname" shadow="never">
          <el-skeleton :rows="6" animated />
        </el-card>
        <IdentityCard v-else :profile="profile" @updated="onUpdated" />
      </aside>

      <main class="col lane-lobby">
        <LobbyTabs />
      </main>

      <aside class="col lane-match">
        <MatchPanel />
      </aside>
    </div>

    <MatchHistoryDialog v-model="playerDialog.visible" :user="playerDialog.user" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { getProfile } from '../api/user'
import TopNav from '../components/TopNav.vue'
import IdentityCard from '../components/IdentityCard.vue'
import MatchPanel from '../components/MatchPanel.vue'
import LobbyTabs from '../components/LobbyTabs.vue'
import MatchHistoryDialog from '../components/MatchHistoryDialog.vue'
import { playerDialog } from '../composables/usePlayerDialog'
import { requestRefresh } from '../composables/useDataRefresh'
import { presenceTick } from '../composables/usePresence'

// 先从本地缓存读，避免刷新时卡片闪空，再向服务器要最新资料
const saved = JSON.parse(localStorage.getItem('user') || 'null')
const profile = ref(saved || {})
const loading = ref(true)

function onUpdated(data) {
  if (data) profile.value = data
  // 资料/状态/战绩变了，推荐和在线列表跟着重算
  requestRefresh()
}

function loadProfile() {
  return getProfile()
    .then(res => { profile.value = res.data || {} })
    .catch(() => {
      // 具体原因由响应拦截器提示；这里保留缓存里的资料，页面不至于空掉
    })
    .finally(() => { loading.value = false })
}

onMounted(loadProfile)

// 进站 / 从后台回来时，在线状态是被自动改的，不会走 IdentityCard 的 updated 事件。
// 不听这个信号的话，人已经在线了，身份卡上却还写着「离线」。
watch(presenceTick, loadProfile)
</script>

<style scoped>
.home {
  min-height: 100vh;
}

/* 三栏：左身份卡（固定，锚点） / 中开黑大厅（唯一的主舞台，吃掉剩余宽度） /
   右找队友（固定窄栏）。中间那栏用分数单位，所以它能随窗口长大。 */
.body {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 316px;
  grid-template-areas: 'me lobby match';
  gap: 16px;
  align-items: start;
  padding: 20px;
  max-width: 1440px;
  margin: 0 auto;
}

.col {
  min-width: 0;
}

.lane-me {
  grid-area: me;
}

.lane-lobby {
  grid-area: lobby;
}

.lane-match {
  grid-area: match;
}

/* 三栏依次淡入上移，间隔 70ms。整场只有这一个编排好的入场，
   别处不再加动效 —— prefers-reduced-motion 下整段动画被压平（见下）。 */
.col {
  opacity: 0;
  animation: lane-in 0.42s cubic-bezier(0.22, 0.61, 0.36, 1) forwards;
}

.lane-lobby {
  animation-delay: 0.07s;
}

.lane-match {
  animation-delay: 0.14s;
}

@keyframes lane-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .col {
    opacity: 1;
    animation: none;
  }
}

/* 放不下三栏就退成两栏：找队友落到大厅下面，身份卡留在左边整列 */
@media (max-width: 1180px) {
  .body {
    grid-template-columns: 280px minmax(0, 1fr);
    grid-template-areas:
      'me lobby'
      'me match';
  }
}

/* 窄屏：全部单列，顺序为 资料 → 大厅/在线 → 找队友 */
@media (max-width: 880px) {
  .body {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'me'
      'lobby'
      'match';
    padding: 14px;
    gap: 14px;
  }
}
</style>
