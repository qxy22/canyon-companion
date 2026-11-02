<template>
  <div class="chat">
    <div class="chat-head">
      <div class="head-text">
        <h2>房间聊天</h2>
        <p class="eyebrow">{{ stateText }}</p>
      </div>
    </div>

    <div ref="scroller" class="scroll" @scroll="onScroll">
      <button v-if="hasMore" class="more" :disabled="loadingMore" @click="loadOlder">
        {{ loadingMore ? '正在加载…' : '看看更早的消息' }}
      </button>
      <p v-else-if="messages.length" class="top-tip">已经是最早的消息了</p>

      <div v-if="!loaded" class="loading">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="messages.length === 0" class="empty">
        <p class="empty-title">还没有人说话</p>
        <p class="empty-hint">问一句「几点开」，比等着别人先开口快得多。</p>
      </div>

      <!-- v-for 不跟 v-else 挤在同一个元素上：那样分支判断和循环搅在一起，
           编译器处理顺序不直观，用一层 template 把「要不要显示列表」说清楚 -->
      <template v-else>
        <MessageBubble
          v-for="(m, i) in messages"
          :key="m.id"
          :message="m"
          :my-id="myId"
          :show-meta="needsMeta(i)"
          @open-player="goPlayer"
        />
      </template>
    </div>

    <div class="composer" :class="{ disabled: Boolean(closedReason) }">
      <div class="tools">
        <el-popover trigger="click" placement="top-start" :width="292" :show-after="0">
          <template #reference>
            <button type="button" class="tool" title="插入表情" :disabled="Boolean(closedReason)">😀</button>
          </template>
          <EmojiPicker @pick="insertEmoji" />
        </el-popover>

        <button type="button" class="tool" title="发图片" :disabled="Boolean(closedReason)" @click="pick('image')">
          图片
        </button>
        <button type="button" class="tool" title="发表情包" :disabled="Boolean(closedReason)" @click="pick('sticker')">
          表情包
        </button>
        <button type="button" class="tool" title="发视频（≤15 秒，≤20MB）" :disabled="Boolean(closedReason)" @click="pick('video')">
          视频
        </button>

        <span v-if="uploading" class="uploading">正在上传… {{ uploadPercent }}%</span>
      </div>

      <textarea
        ref="input"
        v-model="draft"
        class="draft"
        rows="2"
        :maxlength="textMax"
        :disabled="Boolean(closedReason)"
        placeholder="说点什么，回车发送"
        @keydown.enter.exact.prevent="sendText"
      />

      <div class="send-row">
        <span class="counter num">{{ draft.length }} / {{ textMax }}</span>
        <el-button type="primary" size="small" :disabled="!canSend" @click="sendText">发送</el-button>
      </div>

      <input ref="imageInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp" hidden @change="onFile('image', $event)" />
      <input ref="stickerInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp" hidden @change="onFile('sticker', $event)" />
      <input ref="videoInput" type="file" accept="video/mp4,video/webm" hidden @change="onFile('video', $event)" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { chatHistory, uploadChatFile, markRead, chatLimits } from '../api/chat'
import { createChatSocket } from '../composables/useChatSocket'
import MessageBubble from './MessageBubble.vue'
import EmojiPicker from './EmojiPicker.vue'

const props = defineProps({
  teamupId: { type: [Number, String], required: true }
})
const emit = defineEmits(['left'])

const router = useRouter()

const myId = (JSON.parse(localStorage.getItem('user') || 'null') || {}).id

const messages = ref([])
const draft = ref('')
const loaded = ref(false)
const loadingMore = ref(false)
const hasMore = ref(false)
const closedReason = ref('')
const uploading = ref(false)
const uploadPercent = ref(0)
const textMax = ref(1000)
const limits = ref({})

const scroller = ref(null)
const input = ref(null)
const imageInput = ref(null)
const stickerInput = ref(null)
const videoInput = ref(null)

const socket = createChatSocket(props.teamupId, {
  onOpen: () => {
    // 重连之后把断线期间漏掉的消息补齐：直接重拉最新一页并按 id 去重
    refresh()
  },
  onMessage: data => {
    const stick = nearBottom()
    merge([data])
    loaded.value = true
    if (stick) scrollToBottom()
    markReadSilent()
  },
  onNotice: text => {
    ElMessage({ type: 'warning', message: text, duration: 5000 })
  },
  onError: text => {
    ElMessage.error(text)
  },
  onClosed: text => {
    // 输入框先禁用，原因交给房间页整块展示 —— 这里自己弹一条横幅也没用，
    // 父组件收到 left 就会把这个面板卸载掉。
    closedReason.value = text || '你已不在该队伍里'
    emit('left', closedReason.value)
  },
  onFatal: text => {
    ElMessage.error(text)
  }
})

// 连不上就别让「发送」亮着 —— 点了没反应比灰掉更让人困惑
const canSend = computed(
  () => draft.value.trim().length > 0 && socket.state.value === 'open' && !closedReason.value
)

const stateText = computed(() => {
  if (closedReason.value) return '连接已关闭'
  if (socket.state.value === 'open') return '已连上，消息实时送达'
  if (socket.state.value === 'connecting') return '正在连接…'
  return '连接已断开，正在重试'
})

onMounted(async () => {
  try {
    const r = await chatLimits()
    const d = r.data || {}
    limits.value = d
    textMax.value = d.textMax || 1000
  } catch {
    // 拿不到就用默认值，不影响聊天
  }
  await loadLatest()
  socket.open()
})

onBeforeUnmount(() => {
  socket.close()
})

function loadLatest() {
  return chatHistory(props.teamupId, null, 30)
    .then(res => {
      const rows = res.data || []
      messages.value = rows
      hasMore.value = rows.length >= 30
      loaded.value = true
      scrollToBottom()
      markReadSilent()
    })
    .catch(() => {
      loaded.value = true
    })
}

/** 重连后补漏：拉最新一页合并进来，避免断线那几秒的消息丢了 */
function refresh() {
  return chatHistory(props.teamupId, null, 30)
    .then(res => {
      const stick = nearBottom()
      merge(res.data || [])
      if (stick) scrollToBottom()
      markReadSilent()
    })
    .catch(() => {})
}

function loadOlder() {
  if (!messages.value.length || loadingMore.value) return
  loadingMore.value = true
  const el = scroller.value
  const prevHeight = el ? el.scrollHeight : 0
  const prevTop = el ? el.scrollTop : 0

  chatHistory(props.teamupId, messages.value[0].id, 30)
    .then(res => {
      const rows = res.data || []
      merge(rows)
      hasMore.value = rows.length >= 30
      // 往上翻页时保持视线不跳：新内容顶进来了，把滚动位置补回来
      nextTick(() => {
        if (scroller.value) {
          scroller.value.scrollTop = prevTop + (scroller.value.scrollHeight - prevHeight)
        }
      })
    })
    .catch(() => {})
    .finally(() => { loadingMore.value = false })
}

function merge(rows) {
  const known = new Set(messages.value.map(m => m.id))
  const fresh = rows.filter(r => r && !known.has(r.id))
  if (!fresh.length) return
  messages.value = [...messages.value, ...fresh].sort((a, b) => a.id - b.id)
}

function sendText() {
  const text = draft.value.trim()
  if (!text) return
  if (socket.state.value !== 'open') {
    ElMessage.warning('连接还没建立，等一下再发')
    return
  }
  if (!socket.send({ type: 'TEXT', content: text })) {
    ElMessage.warning('没发出去，再试一次')
    return
  }
  draft.value = ''
  scrollToBottom()
}

function insertEmoji(e) {
  draft.value += e
  input.value?.focus()
}

function pick(which) {
  const map = { image: imageInput, sticker: stickerInput, video: videoInput }
  map[which]?.value?.click()
}

function onFile(which, event) {
  const file = event.target.files && event.target.files[0]
  // 清空 value，否则连着选同一个文件不会再触发 change
  event.target.value = ''
  if (!file) return

  if (which === 'video') {
    checkVideo(file).then(send).catch(msg => ElMessage.error(msg))
  } else {
    send(file)
  }

  function send(f) {
    doUpload(which, f)
  }
}

/** 视频太长会让房间刷屏，15 秒是拍个精彩操作够用、又不至于拖慢房间的长度 */
function checkVideo(file) {
  return new Promise((resolve, reject) => {
    const maxSeconds = limits.value.videoMaxSeconds || 15
    const url = URL.createObjectURL(file)
    const v = document.createElement('video')
    v.preload = 'metadata'
    v.onloadedmetadata = () => {
      URL.revokeObjectURL(url)
      if (v.duration > maxSeconds + 0.5) {
        reject(`视频最长 ${maxSeconds} 秒，这段是 ${Math.round(v.duration)} 秒，剪短一点再发`)
      } else {
        resolve(file)
      }
    }
    v.onerror = () => {
      URL.revokeObjectURL(url)
      // 读不出时长也别拦着，后端还有大小和格式两道检查
      resolve(file)
    }
    v.src = url
  })
}

function doUpload(which, file) {
  const kind = which === 'video' ? 'VIDEO' : 'IMAGE'
  const type = which === 'video' ? 'VIDEO' : which === 'sticker' ? 'STICKER' : 'IMAGE'

  uploading.value = true
  uploadPercent.value = 0
  const tick = setInterval(() => {
    uploadPercent.value = Math.min(uploadPercent.value + 7, 92)
  }, 200)

  uploadChatFile(props.teamupId, file, kind)
    .then(res => {
      const d = res.data || {}
      if (!socket.send({ type, content: d.url, fileName: d.name, fileSize: d.size })) {
        ElMessage.warning('文件传好了，但消息没发出去，再发一次')
      }
      scrollToBottom()
    })
    .catch(() => {
      // 具体原因（太大 / 格式不对）由拦截器提示
    })
    .finally(() => {
      clearInterval(tick)
      uploading.value = false
      uploadPercent.value = 0
    })
}

function needsMeta(i) {
  const m = messages.value[i]
  if (!m || m.type === 'SYSTEM') return false
  const prev = messages.value[i - 1]
  if (!prev || prev.type === 'SYSTEM') return true
  if ((prev.user && prev.user.id) !== (m.user && m.user.id)) return true
  const t1 = new Date(prev.createdAt).getTime()
  const t2 = new Date(m.createdAt).getTime()
  if (!Number.isFinite(t1) || !Number.isFinite(t2)) return true
  // 三分钟内的连续发言合并显示，省掉重复的昵称和时间
  return t2 - t1 > 3 * 60 * 1000
}

function nearBottom() {
  const el = scroller.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 140
}

function scrollToBottom() {
  nextTick(() => {
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  })
}

function onScroll() {
  // 用户往上翻看历史时，不要把他拽回底部
}

function markReadSilent() {
  const last = messages.value.length ? messages.value[messages.value.length - 1].id : null
  markRead(props.teamupId, last).catch(() => {})
}

function goPlayer(user) {
  if (user && user.id) router.push(`/player/${user.id}`)
}
</script>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.chat-head {
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--line-soft);
}

.chat-head h2 {
  font-size: 16px;
}

.eyebrow {
  margin-top: 2px;
}

/* 消息区必须能滚，否则聊到二十条就把页面撑得没有输入框的位置了 */
.scroll {
  flex: 1;
  min-height: 260px;
  max-height: 56vh;
  overflow-y: auto;
  padding: 12px 16px;
}

.more {
  display: block;
  width: 100%;
  margin-bottom: 8px;
  padding: 6px;
  appearance: none;
  border: 1px dashed var(--line);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-dim);
  font-family: inherit;
  font-size: var(--fs-micro);
  cursor: pointer;
}

.more:hover:not(:disabled) {
  border-color: var(--gold-dim);
  color: var(--gold);
}

.more:disabled {
  cursor: default;
  opacity: 0.6;
}

.top-tip {
  text-align: center;
  font-size: var(--fs-micro);
  color: var(--text-mute);
  padding-bottom: 10px;
}

.loading {
  padding: 8px 0;
}

.empty {
  padding: 34px 10px;
  text-align: center;
}

.empty-title {
  font-weight: 600;
}

.empty-hint {
  margin-top: 6px;
  font-size: var(--fs-small);
  color: var(--text-mute);
}

.composer {
  border-top: 1px solid var(--line-soft);
  padding: 10px 16px 12px;
}

.composer.disabled {
  opacity: 0.55;
}

.tools {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.tool {
  appearance: none;
  border: 1px solid var(--line);
  background: var(--ink);
  color: var(--text-dim);
  font-family: inherit;
  font-size: var(--fs-micro);
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.tool:hover:not(:disabled) {
  color: var(--gold);
  border-color: var(--gold-dim);
}

.tool:disabled {
  cursor: default;
  opacity: 0.5;
}

.uploading {
  font-size: var(--fs-micro);
  color: var(--teal);
}

.draft {
  width: 100%;
  resize: none;
  padding: 9px 11px;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  color: var(--text);
  font-family: inherit;
  font-size: var(--fs-body);
  line-height: 1.6;
  max-height: 120px;
}

.draft:focus {
  outline: none;
  border-color: var(--gold);
}

.draft::placeholder {
  color: var(--text-mute);
}

.send-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.counter {
  font-size: var(--fs-micro);
  color: var(--text-mute);
}
</style>
