<template>
  <el-card class="is-lead" shadow="never">
    <!-- head 带一条底边：把「标题 + 动作」和下面的列表分开，
         中间这栏内容多，没有这条线会糊成一片 -->
    <div class="head">
      <div class="head-text">
        <h3>开黑大厅</h3>
        <p class="eyebrow">点组员头像可以看他的段位、大区和战绩</p>
      </div>
      <div class="head-actions">
        <el-button text :loading="loading" @click="manualLoad">刷新</el-button>
        <el-button type="primary" @click="openPublish">发布组队</el-button>
      </div>
    </div>

    <el-skeleton v-if="firstLoad" :rows="4" animated />

    <template v-else>
      <!-- 我所在的队伍永远排在最上面：满员后它会从招募列表消失，
           不单独列出来，用户就再也找不到「退出队伍」的入口了。 -->
      <section v-if="myList.length" class="section">
        <h4 class="sec-title">我的队伍 · {{ myList.length }} 支</h4>
        <TeamupItem
          v-for="t in myList"
          :key="`mine-${t.id}`"
          :teamup="t"
          :my-id="myId"
          :unread="unreadOf(t.id)"
          :busy="busyId === t.id ? busyAction : null"
          @join="join"
          @leave="confirmLeave"
          @kick="confirmKick"
          @open-player="goPlayer"
        />
      </section>

      <section class="section">
        <h4 class="sec-title">
          招募中<template v-if="total"> · {{ total }} 支</template>
        </h4>

        <div v-if="list.length === 0" class="empty">
          <p class="empty-title">还没有队伍在招募</p>
          <p class="empty-hint">点右上角「发布组队」，写清楚要什么位置和时间，别人就能直接加入。</p>
        </div>

        <div v-else>
          <TeamupItem
            v-for="t in list"
            :key="`open-${t.id}`"
            :teamup="t"
            :my-id="myId"
            :unread="unreadOf(t.id)"
            :busy="busyId === t.id ? busyAction : null"
            @join="join"
            @leave="confirmLeave"
            @kick="confirmKick"
            @open-player="goPlayer"
          />
        </div>

        <Pager v-model:page="page" :size="SIZE" :total="total" />
      </section>
    </template>

    <el-dialog v-model="publishVisible" title="发布组队" width="460px" append-to-body>
      <el-form ref="publishFormRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="游戏模式" prop="gameMode">
          <el-select v-model="form.gameMode" placeholder="选择模式" style="width: 100%">
            <el-option v-for="m in GAME_MODES" :key="m.code" :label="m.label" :value="m.code" />
          </el-select>
        </el-form-item>

        <el-form-item label="缺哪些位置">
          <el-select v-model="form.positions" multiple placeholder="不选表示位置不限" style="width: 100%">
            <el-option v-for="p in POSITIONS" :key="p.code" :label="p.label" :value="p.code" />
          </el-select>
        </el-form-item>

        <el-form-item label="队伍人数上限">
          <el-input-number v-model="form.maxSize" :min="2" :max="10" />
          <p class="field-hint">包含你自己在内。达到上限后队伍会自动关闭。</p>
        </el-form-item>

        <el-form-item label="说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="100"
            show-word-limit
            placeholder="如：今晚 8 点排位，来两个稳的"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">发布</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { publishTeamup, listTeamup, myTeamups, joinTeamup, leaveTeamup, kickMember } from '../api/teamup'
import { unreadSummary } from '../api/chat'
import { POSITIONS, GAME_MODES } from '../constants'
import TeamupItem from './TeamupItem.vue'
import { refreshTick } from '../composables/useDataRefresh'
import { useAutoRefresh } from '../composables/useAutoRefresh'
import Pager from './Pager.vue'

const emit = defineEmits(['count', 'unread'])

const props = defineProps({
  active: { type: Boolean, default: true }
})

const router = useRouter()

const SIZE = 10

// 后端把成员完整返回，所以"我是不是在这个队里"可以直接算出来，
// 不再需要像之前那样靠一个本地数组硬记（刷新页面就丢）。
const myId = (JSON.parse(localStorage.getItem('user') || 'null') || {}).id

const list = ref([])
const myList = ref([])
const total = ref(0)
const page = ref(1)
// 各房间我没读的消息条数：{ 队伍id: 条数 }。键来自 JSON，是字符串
const unreadMap = ref({})
const loading = ref(false)
// 骨架屏只在第一次出现，之后每 2 秒静默换数据
const firstLoad = ref(true)
const publishing = ref(false)
const publishVisible = ref(false)
const publishFormRef = ref(null)

// 只记"哪条队伍的哪个动作在进行"，这样转圈只出现在被点的那一条上
const busyId = ref(null)
const busyAction = ref(null)

const form = reactive({ gameMode: '', positions: [], maxSize: 5, description: '' })

const rules = {
  gameMode: [{ required: true, message: '先选一个游戏模式', trigger: 'change' }]
}

function goPlayer(member) {
  if (!member || !member.id) return
  router.push(`/player/${member.id}`)
}

/**
 * 列表本身 2 秒更新一次。
 *
 * 未读数被刻意拆到另一个 10 秒的定时器（见文件末尾）：它每查一个房间就要
 * 问一次数据库，跟着列表一起每 2 秒跑会把省下来的查询又全花回去。
 *
 * 用 allSettled 而不是 all：聊天/组队接口任一个出问题，大厅也要照常显示。
 */
function loadList(isManual) {
  if (isManual) loading.value = true
  const options = { silent: !isManual }

  return Promise.allSettled([
    myTeamups(options),
    listTeamup({ page: page.value, size: SIZE }, options)
  ]).then(([mine, open]) => {
    myList.value = mine.status === 'fulfilled' ? (mine.value.data || []) : []
    if (open.status === 'fulfilled') {
      const data = open.value.data || {}
      list.value = data.list || []
      total.value = data.total || 0
      clampPage()
    }
    emit('count', myList.value.length + total.value)
  }).finally(() => {
    if (isManual) loading.value = false
    firstLoad.value = false
  })
}

/** 别人下线/解散队伍之后总数会变少，当前页可能已经越过最后一页，收回来 */
function clampPage() {
  const pages = Math.max(1, Math.ceil(total.value / SIZE))
  if (page.value > pages) page.value = pages
}

/**
 * 未读数单独跑，10 秒一次。
 *
 * 它后端是「每个房间查两次库」，是所有列表接口里最贵的一个，
 * 也正因如此上一轮才刻意没给它加缓存（红点必须实时）。
 * 让它跟着 2 秒的列表一起跑，等于把刚省下来的查询又加倍花出去。
 * 10 秒足够让红点显得跟手，又不会每 2 秒惊动一次数据库。
 */
function loadUnread() {
  return unreadSummary({ silent: true })
    .then(res => {
      unreadMap.value = res.data || {}
      emit('unread', totalUnread())
    })
    .catch(() => {
      // 聊天接口挂了不影响大厅：红点不更新就是了
    })
}

function unreadOf(id) {
  return unreadMap.value[id] || 0
}

/** 我的所有房间加起来一共多少条未读，交给外层做徽标 */
function totalUnread() {
  return myList.value.reduce((sum, t) => sum + unreadOf(t.id), 0)
}

function openPublish() {
  form.gameMode = ''
  form.positions = []
  form.maxSize = 5
  form.description = ''
  publishVisible.value = true
  if (publishFormRef.value) publishFormRef.value.clearValidate()
}

async function publish() {
  try {
    await publishFormRef.value.validate()
  } catch {
    return
  }

  publishing.value = true
  try {
    await publishTeamup({ ...form })
    ElMessage.success('已发布，你自动成为队长')
    publishVisible.value = false
    // 新队伍排在最前面，回到第一页才看得到它
    page.value = 1
    refreshAll()
  } catch {
    // 错误提示已由拦截器给出
  } finally {
    publishing.value = false
  }
}

function withBusy(id, action, task) {
  busyId.value = id
  busyAction.value = action
  return task().finally(() => {
    busyId.value = null
    busyAction.value = null
  })
}

function join(id) {
  withBusy(id, 'join', () =>
    joinTeamup(id)
      .then(res => {
        // 满员的队伍会自动关闭并从招募列表消失。如果不说明，用户刚点完"加入"
        // 就发现那条队伍不见了，会以为是自己操作失败。
        if ((res.data || {}).status === 'CLOSED') {
          ElMessage.success('已加入队伍。人数已达上限，该队伍不再出现在招募列表里')
        } else {
          ElMessage.success('已加入队伍')
        }
        refreshAll()
      })
      .catch(() => {
        // 「你已加入该组队」「该组队已满或已结束」会原样提示出来
      })
  )
}

async function confirmLeave(id) {
  try {
    await ElMessageBox.confirm('退出后队伍里的位置会立刻空出来。确定要退出吗？', '退出队伍', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }

  withBusy(id, 'leave', () =>
    leaveTeamup(id)
      .then(res => {
        // data 为 null 表示最后一个人退出了，队伍已删除
        if (!res.data) {
          ElMessage.success('已退出，队伍已解散')
        } else {
          ElMessage.success('已退出队伍')
        }
        refreshAll()
      })
      .catch(() => {})
  )
}

async function confirmKick({ id, user }) {
  try {
    await ElMessageBox.confirm(`把「${user.nickname || '该玩家'}」移出队伍？`, '移除成员', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  withBusy(id, 'leave', () =>
    kickMember(id, user.id)
      .then(() => {
        ElMessage.success(`已将 ${user.nickname || '该玩家'} 移出队伍`)
        refreshAll()
      })
      .catch(() => {})
  )
}

// 列表 2 秒，未读数 10 秒。两条节奏分开，重的那个慢一点
const listTimer = useAutoRefresh(loadList, 2000)
const unreadTimer = useAutoRefresh(loadUnread, 10000)

const refreshList = listTimer.refresh
const refreshUnread = unreadTimer.refresh

/** 手动刷新（点按钮）和「我刚改了东西」，两个都立刻拉一次 */
function manualLoad() {
  refreshList()
  refreshUnread()
}

function refreshAll() {
  refreshList()
  refreshUnread()
}

/** 切到「在线可开黑」那一页时，大厅这边两个定时器一起停 */
function setActive(on) {
  listTimer.setPaused(!on)
  unreadTimer.setPaused(!on)
}

onMounted(() => {
  loadList(true)
  loadUnread()
  if (!props.active) setActive(false)
})

watch(() => props.active, on => setActive(on))

// 翻完页立刻拿新数据，不等下一轮 2 秒
watch(page, () => refreshList())

// 别人发布/加入后不需要手动刷新：任何一处改了资料或战绩都触发这个计数
watch(refreshTick, () => refreshAll())
</script>

<style scoped>
/* 主栏：标题比旁栏大一档，head 用一条分隔线压住下面的列表 */
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

.head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

.section {
  margin-top: 14px;
}

.sec-title {
  font-size: var(--fs-small);
  font-weight: 600;
  color: var(--text-dim);
  letter-spacing: 0.04em;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--line-soft);
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
  max-width: 52ch;
}

.field-hint {
  margin-top: 4px;
  font-size: var(--fs-micro);
  line-height: 1.5;
  color: var(--text-mute);
}
</style>
