<template>
  <header class="topnav">
    <div class="brand">
      <span class="mark" aria-hidden="true" />
      <span class="name">LOL 战绩交友</span>
    </div>

    <form class="search" @submit.prevent="doSearch">
      <el-input
        v-model.trim="keyword"
        placeholder="搜昵称或召唤师名"
        clearable
        class="kw"
        aria-label="搜索昵称或召唤师名"
      />
      <el-select
        v-model="position"
        placeholder="位置不限"
        clearable
        class="sel"
        aria-label="按位置筛选"
      >
        <el-option v-for="p in POSITIONS" :key="p.code" :label="p.label" :value="p.code" />
      </el-select>
      <el-select
        v-model="rankTier"
        placeholder="段位不限"
        clearable
        class="sel sel-rank"
        aria-label="按段位筛选"
      >
        <el-option v-for="r in RANK_TIERS" :key="r.code" :label="r.label" :value="r.code" />
      </el-select>
      <el-button type="primary" native-type="submit" :loading="searching">搜索玩家</el-button>
    </form>

    <div class="account">
      <!-- 网页版才需要：已经在客户端里的人看到这个按钮只会觉得多余 -->
      <a v-if="!isDesktop" class="download" :href="CLIENT_DOWNLOAD_URL" :title="'下载 ' + CLIENT_PLATFORM_LABEL + ' 客户端安装包'">
        下载客户端
      </a>
      <button class="friends" type="button" @click="openFriendDrawer">
        好友
        <span v-if="friendTotal > 0" class="badge num">{{ friendTotal > 99 ? '99+' : friendTotal }}</span>
      </button>
      <span class="who">{{ displayName }}</span>
      <el-button text @click="logout">退出登录</el-button>
    </div>
  </header>

  <el-dialog v-model="dialogVisible" width="560px" append-to-body>
    <template #header>
      <div class="dlg-head">
        <h3>搜索结果</h3>
        <p v-if="!searching" class="dlg-sub">
          <template v-if="total > 0">找到 {{ total }} 位玩家<template v-if="filterText">，条件：{{ filterText }}</template></template>
          <template v-else>没有符合条件的玩家</template>
        </p>
      </div>
    </template>

    <el-skeleton v-if="searching" :rows="4" animated />

    <div v-else-if="results.length === 0" class="empty">
      <p class="empty-title">没有找到匹配的玩家</p>
      <p class="empty-hint">试试放宽条件：清掉段位或位置筛选，或者少打几个字。</p>
      <el-button v-if="hasFilter" @click="clearFilters">清除筛选条件</el-button>
    </div>

    <template v-else>
      <div v-for="u in results" :key="u.id" class="result-item" @click="openPlayerDialog(u)">
        <el-avatar :size="40" :src="u.avatar">{{ initial(u) }}</el-avatar>
        <div class="main">
          <div class="line1">
            <span class="nickname">{{ u.nickname }}</span>
            <el-tag size="small" :type="u.status === 'ONLINE' ? 'success' : 'info'">
              {{ statusLabel(u.status) }}
            </el-tag>
          </div>
          <div class="tags">
            <el-tag size="small" effect="plain">{{ positionLabel(u.position) }}</el-tag>
            <el-tag size="small" effect="plain" type="warning">{{ rankLabel(u.rankTier) }}</el-tag>
            <span v-if="u.region" class="region">{{ u.region }}</span>
          </div>
        </div>
        <span class="go">查看战绩</span>
      </div>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { search } from '../api/match'
import { friendCount } from '../api/friend'
import { POSITIONS, RANK_TIERS, positionLabel, rankLabel, statusLabel } from '../constants'
import { IS_DESKTOP } from '../utils/platform'
import { CLIENT_DOWNLOAD_URL, CLIENT_PLATFORM_LABEL } from '../config/client'
import { openPlayerDialog } from '../composables/usePlayerDialog'
import { openFriendDrawer } from '../composables/useFriendDrawer'
import { refreshTick } from '../composables/useDataRefresh'
import { stopPresence } from '../composables/usePresence'

const props = defineProps({
  profile: { type: Object, default: () => ({}) }
})

const router = useRouter()

const isDesktop = IS_DESKTOP

const keyword = ref('')
const position = ref('')
const rankTier = ref('')
const searching = ref(false)
const dialogVisible = ref(false)
const results = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10

// 好友数只用来点亮徽标，拉不到就当 0 —— 不能因为一个装饰性数字让顶栏报错
const friendTotal = ref(0)

function loadFriendCount() {
  friendCount({ silent: true })
    .then(res => { friendTotal.value = res.data || 0 })
    .catch(() => {})
}

onMounted(loadFriendCount)
// 加/删好友之后刷新
watch(refreshTick, loadFriendCount)

const displayName = computed(() => props.profile.nickname || props.profile.username || '未登录')

const hasFilter = computed(() => Boolean(keyword.value || position.value || rankTier.value))

// 把当前生效的筛选条件说清楚，避免用户以为"就是搜不到"
const filterText = computed(() => {
  const parts = []
  if (position.value) parts.push(positionLabel(position.value))
  if (rankTier.value) parts.push(rankLabel(rankTier.value))
  if (keyword.value) parts.push(`关键词「${keyword.value}」`)
  return parts.join(' · ')
})

function initial(user) {
  const name = user && user.nickname ? user.nickname : 'L'
  return (name.charAt(0) || 'L').toUpperCase()
}

function doSearch() {
  page.value = 1
  runSearch()
}

function onPageChange(p) {
  page.value = p
  runSearch()
}

function clearFilters() {
  keyword.value = ''
  position.value = ''
  rankTier.value = ''
  doSearch()
}

function runSearch() {
  searching.value = true
  dialogVisible.value = true
  search({
    position: position.value || undefined,
    rankTier: rankTier.value || undefined,
    keyword: keyword.value || undefined,
    page: page.value,
    size: pageSize
  })
    .then(res => {
      results.value = (res.data && res.data.list) || []
      total.value = (res.data && res.data.total) || 0
    })
    .catch(() => {
      results.value = []
      total.value = 0
    })
    .finally(() => { searching.value = false })
}

function logout() {
  // 先报离线再清 token —— 顺序反了请求就成 401，别人还会看到你挂在在线列表里
  stopPresence(true)
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.replace('/login')
}
</script>

<style scoped>
.topnav {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 60px;
  padding: 0 20px;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 10;
}

/* 品牌那头是金，往右边渐隐的发丝线 —— 压在原来的 1px 描边上（bottom: -1px 就是落在
   border 那一像素上）。全站只有这一条是渐变，其余横线都是实色，所以它认得出来 */
.topnav::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 1px;
  background: linear-gradient(
    90deg,
    var(--gold) 0%,
    rgba(200, 170, 110, 0.3) 30%,
    transparent 68%
  );
  pointer-events: none;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  white-space: nowrap;
}

.brand .mark {
  width: 9px;
  height: 9px;
  transform: rotate(45deg);
  background: var(--gold);
}

.brand .name {
  font-weight: 600;
}

.search {
  display: flex;
  gap: 8px;
  flex: 1;
  justify-content: center;
  min-width: 0;
}

.kw {
  width: 190px;
}

.sel {
  width: 116px;
}

.sel-rank {
  width: 128px;
}

.account {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  white-space: nowrap;
}

/* 好友入口：和「退出登录」那种纯文字不同，它有个边框 —— 这是要反复点的地方 */
.friends {
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  background: transparent;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  color: var(--text-dim);
  font-family: inherit;
  font-size: var(--fs-small);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.friends:hover,
.friends:focus-visible {
  color: var(--text);
  border-color: var(--gold);
  background: var(--panel-hi);
}

/* 下载客户端：给网页访客的行动号召。用金色描边而不是实底金 ——
   它重要，但不是这一屏的主任务（主任务是搜索和进大厅） */
.download {
  display: inline-flex;
  align-items: center;
  padding: 5px 11px;
  border: 1px solid rgba(200, 170, 110, 0.38);
  border-radius: var(--radius-sm);
  color: var(--gold);
  font-size: var(--fs-small);
  text-decoration: none;
  transition: border-color 0.15s, background 0.15s;
}

.download:hover,
.download:focus-visible {
  border-color: var(--gold);
  background: rgba(200, 170, 110, 0.08);
  color: var(--gold);
}

.badge {
  min-width: 17px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--gold);
  color: var(--ink);
  font-size: 11px;
  line-height: 17px;
  text-align: center;
  font-weight: 600;
}

.who {
  color: var(--text);
  font-weight: 600;
  font-size: var(--fs-small);
}

.dlg-head h3 {
  font-size: 17px;
}

.dlg-sub {
  margin-top: 4px;
  font-size: var(--fs-micro);
  color: var(--text-mute);
}

.empty {
  padding: 20px 0 8px;
  text-align: center;
}

.empty-title {
  font-weight: 600;
}

.empty-hint {
  margin: 6px 0 14px;
  font-size: var(--fs-small);
  color: var(--text-mute);
}

.result-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 8px;
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.result-item:last-of-type {
  border-bottom: none;
}

.result-item:hover {
  background: var(--panel-hi);
}

.main {
  flex: 1;
  min-width: 0;
}

.line1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.nickname {
  font-weight: 600;
}

.tags {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 6px;
}

.region {
  color: var(--text-mute);
  font-size: var(--fs-micro);
}

.go {
  flex: none;
  font-size: var(--fs-micro);
  color: var(--teal);
}

.pager {
  margin-top: 14px;
  text-align: right;
}

@media (max-width: 900px) {
  .topnav {
    height: auto;
    flex-wrap: wrap;
    padding: 12px 16px;
    gap: 12px;
  }
  .search {
    order: 3;
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
  .kw {
    flex: 1;
    min-width: 140px;
  }
}
</style>
