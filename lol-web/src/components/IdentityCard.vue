<template>
  <el-card class="card" shadow="never">
    <div class="head" @click="openPlayerDialog(profile)">
      <!-- 点头像本身是「换头像」，点旁边才是「看自己的资料卡」，两个动作分开。
           相机角标只在悬停 / 键盘聚焦时显形，想要一直都在的入口下面还有一行文字按钮。 -->
      <div class="avatar-box">
        <el-avatar :size="56" :src="profile.avatar">{{ initial }}</el-avatar>
        <button
          type="button"
          class="avatar-edit"
          :class="{ busy: avatarBusy }"
          :disabled="avatarBusy"
          :aria-label="profile.avatar ? '更换头像' : '上传头像'"
          :title="profile.avatar ? '更换头像' : '上传头像'"
          @click.stop="pickAvatar"
        >
          <svg v-if="!avatarBusy" viewBox="0 0 24 24" width="17" height="17" aria-hidden="true">
            <path
              d="M4 7.5h2.6l1.3-2.2h8.2l1.3 2.2H20a1 1 0 0 1 1 1v9.5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
            <circle cx="12" cy="13.4" r="3.2" fill="none" stroke="currentColor" stroke-width="1.7" />
          </svg>
          <span v-else class="spinner" aria-hidden="true" />
        </button>
        <!-- 程序化 click() 会冒泡，不加 stop 会把「看资料卡」也触发一遍 -->
        <input
          ref="avatarInput"
          type="file"
          accept="image/png,image/jpeg,image/gif,image/webp"
          hidden
          @click.stop
          @change="onAvatarFile"
        />
      </div>
      <div class="name">
        <div class="nickname">{{ profile.nickname || '未设置昵称' }}</div>
        <el-tag :type="statusTagType" size="small">{{ statusLabel(profile.status) }}</el-tag>
      </div>
    </div>

    <div class="avatar-ops">
      <button type="button" class="link" :disabled="avatarBusy" @click="pickAvatar">
        {{ profile.avatar ? '更换头像' : '上传头像' }}
      </button>
      <button
        v-if="profile.avatar"
        type="button"
        class="link danger"
        :disabled="avatarBusy"
        @click="clearAvatar"
      >
        移除
      </button>
      <span class="fmt">PNG / JPG / GIF / WebP，≤2MB</span>
    </div>

    <!-- 原来是个两态开关，后端明明有 BUSY 却没法设置。「游戏中」在开黑场景里是有用的：
         告诉别人你现在没法被拉进队伍。 -->
    <div class="status-block">
      <p class="label">我的状态</p>
      <div class="segmented" role="group" aria-label="设置我的在线状态">
        <button
          v-for="opt in statusOptions"
          :key="opt.code"
          type="button"
          class="seg"
          :class="{ on: profile.status === opt.code }"
          :aria-pressed="profile.status === opt.code"
          :disabled="statusLoading"
          @click="setStatus(opt.code)"
        >
          {{ opt.label }}
        </button>
      </div>
      <p class="hint">{{ statusHint }}</p>
    </div>

    <div class="rating" @click="openPlayerDialog(profile)">
      <div class="tier" :style="{ background: gradeColor(profile.skillTier) }">
        {{ profile.skillTier || '—' }}
      </div>
      <div class="rating-meta">
        <div v-if="hasMatches" class="score num">
          {{ profile.skillScore }}<span class="unit"> 分</span>
        </div>
        <div v-else class="score none">暂无实力分</div>
        <div class="count">
          <template v-if="hasMatches">已录入 {{ profile.matchCount }} 场</template>
          <template v-else>还没有战绩数据</template>
        </div>
      </div>
    </div>

    <p v-if="!hasMatches" class="onboard">
      点下面的「录入战绩」，从本机游戏客户端读最近 10 场，系统才能算出你的实力分和等级。
    </p>

    <TierRail :tier="profile.rankTier" />

    <el-divider />

    <div class="info">
      <!-- 前两项是客户端给的，不是用户填的，所以空态的措辞是「未同步」而不是「未填」 -->
      <div class="row">
        <span class="k">召唤师名<em class="auto">自动</em></span>
        <span class="v">{{ profile.summonerName || '未同步' }}</span>
      </div>
      <div class="row">
        <span class="k">段位<em class="auto">自动</em></span>
        <span class="v">{{ rankLabel(profile.rankTier) }}</span>
      </div>
      <div class="row"><span class="k">大区</span><span class="v">{{ profile.region || '未填' }}</span></div>
      <div class="row"><span class="k">主玩位置</span><span class="v">{{ positionLabel(profile.position) }}</span></div>
      <div class="row"><span class="k">性别</span><span class="v">{{ profile.gender || '未填' }}</span></div>
      <div class="bio">{{ profile.bio || '还没写签名' }}</div>
    </div>

    <el-button type="primary" :loading="syncing" class="act" @click="doSync">
      {{ syncing ? '正在读取…' : '录入战绩' }}
    </el-button>
    <p class="act-hint">
      <template v-if="syncing && syncStep">{{ syncStep }}</template>
      <template v-else>{{ syncHint }}</template>
    </p>

    <!-- 网页版：上面那个按钮点了只会引导去下载。这条是同一件事的直达入口，
         放在按钮正下方，是因为用户的视线此刻就在这儿 -->
    <el-button v-if="!isDesktop" link class="download-link" @click="downloadClient">
      下载 {{ CLIENT_PLATFORM_LABEL }} 客户端安装包
    </el-button>

    <el-button plain class="act" @click="openEdit">编辑资料</el-button>

    <el-dialog v-model="editVisible" title="编辑资料" width="460px" append-to-body>
      <el-form :model="form" label-position="top">
        <el-form-item label="昵称">
          <el-input :model-value="profile.nickname" disabled />
          <p class="field-hint">昵称在注册时确定，暂时不支持修改。</p>
        </el-form-item>

        <!-- 召唤师名和段位的权威来源是游戏客户端。让用户手填会出现
             「段位填王者、战绩全是青铜局」这种自相矛盾的档案，所以这里只展示不自填。 -->
        <p class="locked">
          召唤师名和段位由游戏客户端自动同步，这里不能改。想更新就点身份卡上的「录入战绩」。
        </p>

        <el-form-item label="大区">
          <el-input v-model.trim="form.region" placeholder="如：艾欧尼亚" />
        </el-form-item>

        <el-form-item label="主玩位置">
          <el-select v-model="form.position" placeholder="选择位置" clearable style="width: 100%">
            <el-option v-for="p in POSITIONS" :key="p.code" :label="p.label" :value="p.code" />
          </el-select>
        </el-form-item>

        <el-form-item label="性别">
          <el-select v-model="form.gender" placeholder="选择性别" clearable style="width: 100%">
            <el-option v-for="g in GENDERS" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>

        <el-form-item label="签名">
          <el-input v-model="form.bio" type="textarea" :rows="2" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { updateProfile, updateStatus, uploadAvatar, removeAvatar } from '../api/user'
import { POSITIONS, GENDERS, positionLabel, rankLabel, statusLabel, gradeColor } from '../constants'
import { IS_DESKTOP } from '../utils/platform'
import { CLIENT_PLATFORM_LABEL, downloadClient } from '../config/client'
import { resolveMessage } from '../utils/errorMessage'
import { openPlayerDialog } from '../composables/usePlayerDialog'
import { pausePresence, resumePresence } from '../composables/usePresence'
import TierRail from './TierRail.vue'

const props = defineProps({
  profile: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['updated'])

const statusLoading = ref(false)
const saving = ref(false)
const syncing = ref(false)
const editVisible = ref(false)

const avatarInput = ref(null)
const avatarUploading = ref(false)
const avatarRemoving = ref(false)
const avatarBusy = computed(() => avatarUploading.value || avatarRemoving.value)

// 和后端 FileStorageService.Kind.AVATAR 对齐。改了一处，另一处要跟着改。
const AVATAR_MAX_MB = 2
const AVATAR_MIMES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp']
const AVATAR_EXTS = ['.png', '.jpg', '.jpeg', '.gif', '.webp']

// 只有用户可以自己决定的字段。召唤师名和段位来自游戏客户端，不在表单里。
const form = reactive({
  region: '',
  position: '',
  gender: '',
  bio: ''
})

const statusOptions = [
  { code: 'ONLINE', label: '在线' },
  { code: 'BUSY', label: '游戏中' },
  { code: 'OFFLINE', label: '离线' }
]

const initial = computed(() => ((props.profile.nickname || 'L').charAt(0) || 'L').toUpperCase())
const hasMatches = computed(() => (props.profile.matchCount || 0) > 0)

const statusTagType = computed(() => {
  if (props.profile.status === 'ONLINE') return 'success'
  if (props.profile.status === 'BUSY') return 'warning'
  return 'info'
})

// 说清每种状态意味着什么 —— 用户最关心的是"别人能不能看到我"
const statusHint = computed(() => {
  if (props.profile.status === 'ONLINE') return '你正出现在「在线可开黑」列表里，别人能看到你。'
  if (props.profile.status === 'BUSY') return '游戏进行中。你不会出现在「在线可开黑」列表里，也就不会被邀请。'
  return '已离线。别人看不到你，搜索还能搜到。'
})

function setStatus(code) {
  if (props.profile.status === code) return
  statusLoading.value = true
  updateStatus(code)
    .then(res => {
      // 手动选了「在线」→ 自动上线机制继续工作；
      // 手动选了「游戏中」/「离线」→ 让它让位，否则下一次心跳/切回前台
      // 会把人又拉回在线，用户会觉得自己点了没用。
      if (code === 'ONLINE') resumePresence()
      else pausePresence()
      ElMessage.success(`状态已改为「${statusLabel(code)}」`)
      emit('updated', res.data)
    })
    .catch(() => {})
    .finally(() => { statusLoading.value = false })
}

// 录入战绩只有桌面客户端做得到：英雄联盟客户端跑在用户本机，云端后端看不见它。
// 所以这里不是「有两条路」——浏览器里点这个按钮，唯一有效的下一步就是下载客户端。
// （后端 /api/match/sync 仍在，只是网页不再调它，留给脚本调试用。）
const isDesktop = IS_DESKTOP

const syncHint = isDesktop
  ? '读取最近 10 场战绩，同时把召唤师名、游戏 ID 和当前段位写进资料。需要本机英雄联盟客户端已启动并登录。'
  : '网页版读不到你电脑上的游戏客户端。下载 Windows 客户端后，在客户端里点这里。'

const syncStep = ref('')

/** 桌面端上报成功后，后端回的就是完整资料，这里统一收口渲染。 */
function applySyncResult(data) {
  // 同步接口返回的是 { profile, fetchedCount, rankSynced, note }，
  // 资料在 profile 里；旧结构直接就是资料对象，两种都接得住。
  const next = data.profile || data
  const count = data.fetchedCount != null ? data.fetchedCount : (next.matchCount || 0)

  if (data.rankSynced) {
    ElMessage.success(`已读取 ${count} 场，段位更新为「${rankLabel(data.rankTier)}」，实力分已更新`)
  } else {
    ElMessage.success(`已读取 ${count} 场，实力分已更新`)
  }
  // note 是"这次有点不一样"的说明（0 场 / 段位没读到），得让用户看见，
  // 否则段位没同步时他会以为功能坏了。
  if (data.note) {
    ElMessage({ type: 'warning', message: data.note, duration: 6000 })
  }
  emit('updated', next)
}

async function doSync() {
  if (syncing.value) return

  // 网页版：直接说清「要下载客户端」，并就地给出下载按钮。
  // 不能让请求发出去 —— 云端后端必然返回「未找到本地游戏客户端凭证」，
  // 那句提示在云部署下是误导（用户的游戏明明开着）。
  if (!isDesktop) {
    ElMessageBox.confirm(
      '录入战绩要读你电脑上的英雄联盟客户端，浏览器做不到。下载 Windows 客户端后，在客户端里点「录入战绩」即可。',
      '请下载客户端版本',
      {
        confirmButtonText: '下载客户端',
        cancelButtonText: '先不用',
        type: 'info',
        customClass: 'download-confirm',
      }
    )
      .then(() => downloadClient())
      .catch(() => {})
    return
  }

  syncing.value = true
  syncStep.value = ''
  let stopProgress = null

  try {
    // 桌面端：本机读 LCU → 上报到 /api/match/import，服务端不需要知道请求来自哪
    stopProgress = window.lolDesktop.onSyncProgress(text => {
      syncStep.value = text
    })
    const res = await window.lolDesktop.syncMatches({ count: 10 })
    if (!res.ok) {
      // 桌面端报的是已经分过类的一句话（没开客户端 / 正在启动 / 凭证失效），
      // 比网页版那句「未找到本地游戏客户端凭证」长得多，给足阅读时间。
      ElMessage({ type: 'error', message: res.message || '录入失败', duration: 9000 })
      return
    }
    applySyncResult(res.data || {})
  } catch (e) {
    ElMessage({ type: 'error', message: resolveMessage(e), duration: 5000 })
  } finally {
    if (stopProgress) stopProgress()
    syncStep.value = ''
    syncing.value = false
  }
}

function pickAvatar() {
  avatarInput.value?.click()
}

function onAvatarFile(event) {
  const file = event.target.files && event.target.files[0]
  // 清空 value，否则连着选同一张图不会再触发 change
  event.target.value = ''
  if (!file) return

  // 先在本地挡一道：太大的图不值得走一趟网络。后端还有完全相同的两道检查，
  // 这里的意义是把话说得更具体（这张图多大、支持哪些格式）。
  const name = (file.name || '').toLowerCase()
  const isImage =
    AVATAR_MIMES.includes(file.type) || AVATAR_EXTS.some(ext => name.endsWith(ext))
  if (!isImage) {
    ElMessage.error(`「${file.name || '这个文件'}」不是图片，头像只支持 PNG / JPG / GIF / WebP`)
    return
  }
  if (file.size > AVATAR_MAX_MB * 1024 * 1024) {
    ElMessage.error(
      `头像最大 ${AVATAR_MAX_MB}MB，这张是 ${(file.size / 1024 / 1024).toFixed(1)}MB，压缩一下再传`
    )
    return
  }

  avatarUploading.value = true
  uploadAvatar(file)
    .then(res => {
      ElMessage.success('头像已更新')
      emit('updated', res.data)
    })
    .catch(() => {
      // 具体原因（格式 / 大小 / 登录过期）由拦截器统一提示
    })
    .finally(() => { avatarUploading.value = false })
}

function clearAvatar() {
  avatarRemoving.value = true
  removeAvatar()
    .then(res => {
      ElMessage.success('已移除头像，改回昵称首字')
      emit('updated', res.data)
    })
    .catch(() => {})
    .finally(() => { avatarRemoving.value = false })
}

function openEdit() {
  form.region = props.profile.region || ''
  form.position = props.profile.position || ''
  form.gender = props.profile.gender || ''
  form.bio = props.profile.bio || ''
  editVisible.value = true
}

function save() {
  saving.value = true
  // 传空串表示"清空这个字段"。
  // 原来传的是 null，而后端用 `if (req.getX() != null)` 判断，
  // 于是清空操作被当成"不更新"，字段一旦填上就再也删不掉。
  const payload = {
    region: form.region || '',
    position: form.position || '',
    gender: form.gender || '',
    bio: form.bio || ''
  }

  updateProfile(payload)
    .then(res => {
      ElMessage.success('资料已保存')
      editVisible.value = false
      emit('updated', res.data)
    })
    .catch(() => {})
    .finally(() => { saving.value = false })
}
</script>

<style scoped>
.card {
  height: 100%;
}

.head {
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
}

/* 头像 + 角标。角标平时藏起来，悬停或键盘聚焦才显形 ——
   身份卡上已经有一堆按钮了，再来一个常驻的会抢戏。 */
.avatar-box {
  position: relative;
  flex: none;
  width: 56px;
  height: 56px;
}

.avatar-edit {
  position: absolute;
  right: -3px;
  bottom: -3px;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 50%;
  background: var(--panel-hi);
  border: 1px solid var(--gold);
  color: var(--gold);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s;
}

.avatar-box:hover .avatar-edit,
.avatar-edit:focus-visible {
  opacity: 1;
}

.avatar-edit:hover:not(:disabled) {
  background: var(--ink);
}

.avatar-edit:disabled {
  cursor: progress;
}

.avatar-edit.busy {
  opacity: 1;
}

/* 上传中就别显示相机了，转圈比静态图标更说明"正在动" */
.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--gold-dim);
  border-top-color: var(--gold);
  border-radius: 50%;
  animation: avatar-spin 0.7s linear infinite;
}

@keyframes avatar-spin {
  to {
    transform: rotate(360deg);
  }
}

.avatar-ops {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
  font-size: var(--fs-micro);
}

.link {
  appearance: none;
  padding: 0;
  border: none;
  background: none;
  font-family: inherit;
  font-size: var(--fs-micro);
  color: var(--gold);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.link:hover:not(:disabled) {
  color: var(--text);
}

.link:disabled {
  cursor: default;
  opacity: 0.6;
  text-decoration: none;
}

.link.danger {
  color: var(--ember);
}

.fmt {
  color: var(--text-mute);
}

.name {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

.nickname {
  font-size: 18px;
  font-weight: 600;
  word-break: break-all;
}

.status-block {
  margin-top: 18px;
}

.label {
  font-size: var(--fs-small);
  color: var(--text-dim);
  margin-bottom: 8px;
}

/* 三态分段控件：比开关多出来的「游戏中」是开黑场景真正需要的状态 */
.segmented {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  padding: 4px;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.seg {
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-dim);
  font-family: inherit;
  font-size: var(--fs-small);
  padding: 6px 4px;
  border-radius: 5px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.seg:hover:not(:disabled) {
  color: var(--text);
  background: var(--panel-hi);
}

.seg.on {
  background: var(--panel);
  border-color: var(--gold);
  color: var(--gold);
  font-weight: 600;
}

.seg:disabled {
  cursor: default;
  opacity: 0.6;
}

.hint {
  margin-top: 8px;
  font-size: var(--fs-micro);
  line-height: 1.5;
  color: var(--text-mute);
}

.rating {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 10px 12px;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.tier {
  flex: none;
  width: 42px;
  height: 42px;
  border-radius: var(--radius-sm);
  color: #0a1428;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rating-meta {
  min-width: 0;
}

.score {
  font-size: 22px;
  font-weight: 700;
  color: var(--teal);
  line-height: 1.15;
}

.score.none {
  font-size: var(--fs-body);
  color: var(--text-mute);
  font-weight: 400;
}

.unit {
  font-size: var(--fs-micro);
  color: var(--text-mute);
  font-weight: 400;
}

.count {
  color: var(--text-mute);
  font-size: var(--fs-micro);
}

.onboard {
  margin-top: 10px;
  font-size: var(--fs-micro);
  line-height: 1.6;
  color: var(--text-dim);
  padding-left: 10px;
  border-left: 2px solid var(--gold);
}

.info {
  font-size: var(--fs-small);
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.row .k {
  color: var(--text-mute);
  flex: none;
}

.row .v {
  color: var(--text);
  text-align: right;
  word-break: break-all;
}

.bio {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line-soft);
  color: var(--text-dim);
  font-size: var(--fs-micro);
  line-height: 1.6;
}

.act {
  width: 100%;
  margin: 14px 0 0 0;
}

.act-hint {
  margin-top: 6px;
  font-size: var(--fs-micro);
  line-height: 1.5;
  color: var(--text-mute);
}

/* 下载入口：链接用青（数据/链接色），金色留给上面那个主按钮，别抢层级 */
.download-link {
  width: 100%;
  height: auto;
  margin-top: 4px;
  padding: 0;
  justify-content: flex-start;
  font-size: var(--fs-micro);
  color: var(--teal);
}

.download-link:hover {
  color: var(--teal);
  text-decoration: underline;
}

.field-hint {
  margin-top: 4px;
  font-size: var(--fs-micro);
  line-height: 1.5;
  color: var(--text-mute);
}

/* 「自动」标记：告诉用户这两项不是他自己填的，省得他到处找编辑入口 */
.auto {
  font-style: normal;
  font-size: 10px;
  color: var(--text-mute);
  border: 1px solid var(--line);
  border-radius: 3px;
  padding: 0 3px;
  margin-left: 4px;
  vertical-align: 1px;
}

.locked {
  margin: 0 0 16px;
  padding: 8px 10px;
  background: var(--ink);
  border-left: 2px solid var(--gold);
  border-radius: var(--radius-sm);
  font-size: var(--fs-micro);
  line-height: 1.6;
  color: var(--text-dim);
}

:deep(.el-form-item) {
  margin-bottom: 16px;
}
</style>
