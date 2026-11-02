<template>
  <div class="picker">
    <div class="tabs">
      <button
        v-for="g in GROUPS"
        :key="g.key"
        type="button"
        class="tab"
        :class="{ on: active === g.key }"
        @click="active = g.key"
      >
        {{ g.label }}
      </button>
    </div>

    <div class="grid">
      <button
        v-for="e in items"
        :key="e"
        type="button"
        class="emo"
        :title="e"
        @click="$emit('pick', e)"
      >
        {{ e }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

defineEmits(['pick'])

// 不加载任何表情字体包，直接用系统 emoji：
// 答辩现场可能断网，而 emoji 由操作系统渲染，零体积零请求。
const GROUPS = [
  { key: 'hot', label: '常用', items: ['😂', '😅', '🙂', '😎', '🤔', '😭', '😡', '🥳', '👍', '👏', '🙏', '💪', '🔥', '✨', '🎉', '😴', '🤝', '🐉'] },
  {
    key: 'face', label: '表情',
    items: ['😀', '😃', '😄', '😁', '😆', '😅', '🤣', '😂', '🙂', '🙃', '😉', '😊', '😇', '🥰', '😍', '😘',
      '😋', '😛', '😜', '🤪', '😝', '🤗', '🤭', '🤫', '🤔', '🤐', '😐', '😑', '😏', '😒', '🙄', '😬',
      '😌', '😔', '😪', '😴', '😷', '🤒', '🤕', '🤢', '🥵', '🥶', '😵', '🤯', '🥳', '😎', '🥺', '😭']
  },
  {
    key: 'hand', label: '手势',
    items: ['👍', '👎', '👌', '✌️', '🤞', '🤟', '🤘', '🤙', '👈', '👉', '👆', '👇', '☝️', '✋', '🤚',
      '🖐️', '🖖', '👋', '🤝', '🙏', '✊', '👊', '🤛', '🤜', '👏', '🙌', '👐', '🤲', '💪', '🦾']
  },
  {
    key: 'lol', label: '开黑',
    items: ['🔥', '⚔️', '🛡️', '🏆', '🎯', '💥', '⚡', '👑', '🎮', '🕹️', '💀', '👻', '🤖', '🐉',
      '🦊', '🐺', '🍀', '🌟', '⭐', '💎', '🚀', '⏰', '📍', '🎧', '🎤', '🥇', '🎖️', '🧊']
  }
]

const active = ref('hot')
const items = computed(() => (GROUPS.find(g => g.key === active.value) || GROUPS[0]).items)
</script>

<style scoped>
.picker {
  width: 292px;
}

.tabs {
  display: flex;
  gap: 2px;
  padding: 4px;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.tab {
  appearance: none;
  flex: 1;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-dim);
  font-family: inherit;
  font-size: var(--fs-micro);
  padding: 4px 2px;
  border-radius: 4px;
  cursor: pointer;
}

.tab:hover {
  color: var(--text);
  background: var(--panel-hi);
}

.tab.on {
  background: var(--panel);
  border-color: var(--gold);
  color: var(--gold);
  font-weight: 600;
}

.grid {
  display: grid;
  grid-template-columns: repeat(9, 1fr);
  gap: 2px;
  margin-top: 8px;
  max-height: 184px;
  overflow-y: auto;
}

.emo {
  appearance: none;
  border: none;
  background: transparent;
  font-size: 19px;
  line-height: 1;
  padding: 5px 0;
  border-radius: 5px;
  cursor: pointer;
  transition: background 0.12s;
}

.emo:hover {
  background: var(--panel-hi);
}
</style>
