<template>
  <!-- 系统提示：谁进来了、谁被移出去了。没有气泡，也没有头像，
       它就是一行说明，不该跟真人说的话抢注意力 -->
  <p v-if="isSystem" class="system">{{ message.content }}</p>

  <div v-else class="bubble-row" :class="{ mine }">
    <button
      v-if="showMeta && !mine"
      type="button"
      class="avatar-btn"
      :title="`查看 ${senderName} 的资料`"
      @click="$emit('open-player', message.user)"
    >
      <el-avatar :size="32" :src="message.user && message.user.avatar">{{ initial }}</el-avatar>
    </button>
    <span v-else-if="!mine" class="avatar-gap" />

    <div class="stack">
      <div v-if="showMeta" class="meta">
        <span v-if="!mine" class="who">{{ senderName }}</span>
        <span class="time num">{{ timeText }}</span>
      </div>

      <!-- 图片 / 视频 / 表情包 -->
      <div v-if="isMedia" class="media" :class="{ sticker: isSticker }">
        <!-- 图片用统一尺寸的缩略图铺排，点开才看原图。
             表情包本身就是方的小图，不需要再放大。 -->
        <el-image
          v-if="isImage"
          :src="message.content"
          :preview-src-list="[message.content]"
          fit="cover"
          hide-on-click-modal
          preview-teleported
          class="pic"
        />
        <el-image
          v-else-if="isSticker"
          :src="message.content"
          fit="contain"
          class="pic sticker-pic"
        />
        <video v-else-if="isVideo" :src="message.content" controls preload="metadata" />
        <p v-if="!isSticker && message.fileName" class="file">{{ message.fileName }}</p>
      </div>

      <div v-else class="bubble">{{ message.content }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  message: { type: Object, required: true },
  myId: { type: Number, default: null },
  // 同一个人连着说话时，只在第一条上面显示昵称和时间，省掉重复噪音
  showMeta: { type: Boolean, default: true }
})
defineEmits(['open-player'])

const isSystem = computed(() => props.message.type === 'SYSTEM')
const isImage = computed(() => props.message.type === 'IMAGE')
const isVideo = computed(() => props.message.type === 'VIDEO')
const isSticker = computed(() => props.message.type === 'STICKER')
const isMedia = computed(() => isImage.value || isVideo.value || isSticker.value)

// 「是不是我发的」在前端算：广播给所有人的那份 JSON 里不该带这个标记，
// 否则别人收到的消息也会显示成他发的。
const mine = computed(() => Boolean(props.message.user) && props.message.user.id === props.myId)

const senderName = computed(() => (props.message.user && props.message.user.nickname) || '未知玩家')

const initial = computed(() => (senderName.value.charAt(0) || 'L').toUpperCase())

const timeText = computed(() => {
  const raw = props.message.createdAt
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
})

</script>

<style scoped>
/* 系统提示：一行居中的小字，两侧各一段细线把它跟对话分开 */
.system {
  text-align: center;
  font-size: var(--fs-micro);
  color: var(--text-mute);
  padding: 10px 12px;
  position: relative;
}

.system::before,
.system::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 14%;
  height: 1px;
  background: var(--line-soft);
}

.system::before {
  left: 4%;
}

.system::after {
  right: 4%;
}

.bubble-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 3px 0;
}

.bubble-row.mine {
  flex-direction: row-reverse;
}

.avatar-btn {
  appearance: none;
  padding: 0;
  border: none;
  background: transparent;
  line-height: 0;
  cursor: pointer;
  flex: none;
}

.avatar-gap {
  width: 32px;
  flex: none;
}

.stack {
  min-width: 0;
  max-width: min(560px, 74%);
  display: flex;
  flex-direction: column;
}

.bubble-row.mine .stack {
  align-items: flex-end;
}

.meta {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin: 0 2px 4px;
  font-size: var(--fs-micro);
}

.who {
  color: var(--text-dim);
  font-weight: 600;
}

.time {
  color: var(--text-mute);
}

/* 别人说的：面板色 + 细描边；自己说的：金色描边。
   延续全站分工 —— 金色标记「我」，不新增颜色。 */
.bubble {
  padding: 8px 12px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px 10px 10px 3px;
  font-size: var(--fs-body);
  line-height: 1.6;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}

.mine .bubble {
  background: rgba(200, 170, 110, 0.13);
  border-color: var(--gold-dim);
  border-radius: 10px 10px 3px 10px;
}

.media {
  border: 1px solid var(--line);
  border-radius: 10px;
  overflow: hidden;
  background: var(--ink);
  max-width: 100%;
}

.mine .media {
  border-color: var(--gold-dim);
}

/* 表情包：不加边框不留白，图片本身就是内容 */
.media.sticker {
  border: none;
  background: transparent;
  width: 128px;
}

/* 缩略图框定死尺寸，房间里的行高才不会跟着每张图的比例跳 */
.pic {
  display: block;
  width: 200px;
  height: 150px;
  background: var(--ink);
}

.pic :deep(img) {
  cursor: zoom-in;
}

.sticker-pic {
  width: 128px;
  height: 128px;
  background: transparent;
}

.sticker-pic :deep(img) {
  cursor: default;
}

.media video {
  display: block;
  max-width: 320px;
  max-height: 320px;
  background: #000;
}

.file {
  padding: 5px 8px;
  font-size: var(--fs-micro);
  color: var(--text-mute);
  border-top: 1px solid var(--line-soft);
  word-break: break-all;
}
</style>
