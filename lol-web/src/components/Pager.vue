<template>
  <div v-if="pages > 0" class="pager">
    <span class="total">
      共 <span class="num">{{ total }}</span> 条
    </span>

    <div class="ctrl">
      <button
        type="button"
        class="btn"
        :disabled="page <= 1"
        aria-label="上一页"
        @click="go(page - 1)"
      >
        <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
          <path d="M10 3L5 8l5 5" fill="none" stroke="currentColor" stroke-width="1.6"
                stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        上一页
      </button>

      <span class="pos">
        <span class="num cur">{{ page }}</span>
        <span class="sep">/</span>
        <span class="num">{{ pages }}</span>
      </span>

      <button
        type="button"
        class="btn"
        :disabled="page >= pages"
        aria-label="下一页"
        @click="go(page + 1)"
      >
        下一页
        <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
          <path d="M6 3l5 5-5 5" fill="none" stroke="currentColor" stroke-width="1.6"
                stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  size: { type: Number, default: 10 },
  total: { type: Number, default: 0 }
})

const emit = defineEmits(['update:page'])

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

function go(next) {
  if (next < 1 || next > pages.value) return
  emit('update:page', next)
}
</script>

<style scoped>
/* 一列里塞得下才好用：左边报总数，右边翻页，不铺满整行 */
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}

.total {
  font-size: var(--fs-micro);
  color: var(--text-mute);
}

.total .num {
  color: var(--text-dim);
}

.ctrl {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

/* 翻页按钮沿用全站那套：透明底 + 1px 描边，选中态才上金色 */
.btn {
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--text-dim);
  font-family: inherit;
  font-size: var(--fs-micro);
  padding: 4px 9px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.btn:hover:not(:disabled) {
  color: var(--gold);
  border-color: var(--gold-dim);
  background: var(--ink);
}

.btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pos {
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
  font-size: var(--fs-micro);
  color: var(--text-mute);
  padding: 0 2px;
}

/* 当前页是数据，用青色，和列表里的数值一个口径 */
.cur {
  color: var(--teal);
  font-weight: 600;
}

.sep {
  opacity: 0.5;
}
</style>
