<template>
  <div class="alert" :class="type" :role="type === 'error' ? 'alert' : 'status'">
    <span class="mark" aria-hidden="true" />
    <div class="body">
      <p v-if="title" class="title">{{ title }}</p>
      <p class="text"><slot>{{ message }}</slot></p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  type: { type: String, default: 'error' }, // error | warning | info
  title: { type: String, default: '' },
  message: { type: String, default: '' }
})
</script>

<style scoped>
.alert {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 11px 13px;
  border-radius: var(--radius-sm);
  border: 1px solid;
  font-size: var(--fs-small);
  line-height: 1.55;
  text-align: left;
}

.mark {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  transform: rotate(45deg);
  background: currentColor;
}

.title {
  font-weight: 600;
  color: var(--text);
  margin-bottom: 2px;
}

.text {
  color: var(--text-dim);
}

/* 错误最重：赤铜描边 + 淡底，页面上第一眼就能看到 */
.alert.error {
  color: var(--ember);
  border-color: rgba(212, 87, 78, 0.55);
  background: rgba(212, 87, 78, 0.1);
}

.alert.warning {
  color: var(--gold);
  border-color: rgba(200, 170, 110, 0.45);
  background: rgba(200, 170, 110, 0.08);
}

.alert.info {
  color: var(--teal);
  border-color: rgba(10, 200, 185, 0.4);
  background: rgba(10, 200, 185, 0.08);
}
</style>
