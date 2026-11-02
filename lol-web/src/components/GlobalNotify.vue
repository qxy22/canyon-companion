<template>
  <!-- 组队邀请：全局弹窗。放在 App 里，所以人在任何页面都收得到 -->
  <el-dialog
    v-model="inviteVisible"
    width="440px"
    append-to-body
    :close-on-click-modal="false"
    @closed="onInviteClosed"
  >
    <template #header>
      <div class="invite-head">
        <span class="mark" aria-hidden="true" />
        <div>
          <h3>组队邀请</h3>
          <p class="eyebrow">
            {{ inviterName }} 邀请你加入他的房间
          </p>
        </div>
      </div>
    </template>

    <div class="invite-body">
      <div class="row">
        <span class="k">模式</span>
        <span class="v">{{ modeLabel(invite.gameMode) }}</span>
      </div>
      <div class="row">
        <span class="k">缺的位置</span>
        <span class="v">{{ positionsText }}</span>
      </div>
      <div class="row">
        <span class="k">人数</span>
        <span class="v num">{{ invite.currentSize || 1 }} / {{ invite.maxSize || 5 }}</span>
      </div>
      <div v-if="invite.description" class="row">
        <span class="k">说明</span>
        <span class="v">{{ invite.description }}</span>
      </div>
      <p v-if="inAnotherTeam" class="warn">
        你已经在别的队伍里，先退出那边才能接受这个邀请。
      </p>
    </div>

    <template #footer>
      <el-button @click="decline">先不去</el-button>
      <el-button type="primary" :loading="accepting" @click="accept">接受邀请</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification, ElMessage } from 'element-plus'
import { joinTeamup, myTeamups } from '../api/teamup'
import { modeLabel, positionLabel } from '../constants'
import { connectNotify, disconnectNotify, onNotify } from '../composables/useNotifySocket'
import { requestRefresh } from '../composables/useDataRefresh'
import { armAudio, playFriendAdded, playInvite, playKicked } from '../utils/sound'

const route = useRoute()
const router = useRouter()

const inviteVisible = ref(false)
const accepting = ref(false)
const invite = ref({})
const inAnotherTeam = ref(false)

const inviterName = computed(() => (invite.value.inviter && invite.value.inviter.nickname) || '一位好友')
const positionsText = computed(() => {
  const list = invite.value.positions || []
  if (!list.length) return '不限'
  return list.map(positionLabel).join(' · ')
})

let off = null

onMounted(() => {
  // 提示音要先等一次用户手势才能出声，这里提前把 AudioContext 挂上
  armAudio()
  off = onNotify(handle)
  syncConnection()
})

onBeforeUnmount(() => {
  if (off) off()
  disconnectNotify()
})

// 登录/登出都只是在路由上表现出来（登录后会离开 /login），跟着路由同步一次连接状态就够了
watch(() => route.fullPath, syncConnection)

function syncConnection() {
  if (localStorage.getItem('token')) connectNotify()
  else disconnectNotify()
}

function handle(payload) {
  switch (payload.event) {
    case 'friend_added':
      onFriendAdded(payload.data)
      break
    case 'friend_invite':
      onInvite(payload.data)
      break
    case 'kicked':
      onKicked(payload.data)
      break
    case 'teamup_left':
      onTeamupLeft(payload.data)
      break
    default:
      break
  }
}

function onFriendAdded(user) {
  playFriendAdded()
  ElNotification({
    title: '新的好友',
    message: `${(user && user.nickname) || '有人'} 把你加为好友了，可以在右上角「好友」里邀请他一起开黑`,
    type: 'success',
    duration: 6000
  })
  requestRefresh()
}

function onInvite(data) {
  if (!data || !data.teamupId) return
  invite.value = data
  checkMyTeam(data.teamupId)
  playInvite()
  inviteVisible.value = true
}

async function checkMyTeam(teamupId) {
  try {
    const res = await myTeamups({ silent: true })
    const list = res.data || []
    // 已经在这支队里（比如队长重复邀请）不算冲突
    inAnotherTeam.value = list.some(t => t.id !== teamupId)
  } catch {
    inAnotherTeam.value = false
  }
}

function onKicked(data) {
  playKicked()
  ElNotification({
    title: '你被移出了房间',
    message: (data && (data.reason || data.text)) || '队长把你移出了房间',
    type: 'warning',
    duration: 8000,
    showClose: true
  })
  requestRefresh()
}

/**
 * 处于离线状态被自动退队：得说一声，否则用户会觉得队伍凭空消失了。
 * 不播声音 —— 这不是"出事"，是他自己选择离线的直接结果。
 */
function onTeamupLeft(data) {
  ElMessage({
    message: (data && data.reason) || '你已离线，已自动退出队伍',
    type: 'info',
    duration: 5000
  })
  requestRefresh()
}

function decline() {
  inviteVisible.value = false
}
async function accept() {
  const teamupId = invite.value.teamupId
  if (!teamupId) return
  accepting.value = true
  try {
    const res = await myTeamups({ silent: true })
    const mine = res.data || []
    if (mine.some(t => t.id === teamupId)) {
      // 已经在这支队里，直接送进房间，不必再报一次「你已加入该组队」
      goRoom(teamupId)
      return
    }
    if (mine.length > 0) {
      ElMessage.warning('你已经在别的队伍里，先退出那边才能接受邀请')
      return
    }
    await joinTeamup(teamupId)
    ElMessage.success('已加入队伍')
    goRoom(teamupId)
  } catch {
    // 队伍满了 / 已解散等错误由请求拦截器统一提示
  } finally {
    accepting.value = false
  }
}

function goRoom(teamupId) {
  inviteVisible.value = false
  requestRefresh()
  router.push(`/teamup/${teamupId}`)
}

function onInviteClosed() {
  invite.value = {}
  inAnotherTeam.value = false
}
</script>

<style scoped>
.invite-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.invite-head .mark {
  width: 9px;
  height: 9px;
  margin-top: 7px;
  transform: rotate(45deg);
  background: var(--gold);
  flex: none;
}

.invite-head h3 {
  font-size: 17px;
}

.invite-head .eyebrow {
  margin-top: 2px;
}

.invite-body {
  font-size: var(--fs-small);
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line-soft);
}

.row:last-of-type {
  border-bottom: none;
}

.k {
  color: var(--text-mute);
  flex: none;
}

.v {
  color: var(--text);
  text-align: right;
  word-break: break-word;
}

.warn {
  margin-top: 12px;
  padding: 8px 10px;
  border-left: 2px solid var(--ember);
  background: rgba(212, 87, 78, 0.08);
  font-size: var(--fs-micro);
  line-height: 1.6;
  color: var(--text-dim);
}
</style>
