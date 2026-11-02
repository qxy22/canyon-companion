<template>
  <div class="rail">
    <div
      class="bars"
      role="img"
      :aria-label="ariaLabel"
    >
      <span
        v-for="(t, i) in TIER_LADDER"
        :key="t.code"
        class="bar"
        :class="{ on: i === activeIndex }"
        :style="{ background: t.color }"
      />
    </div>

    <div class="legend">
      <span class="now" :style="{ color: activeColor }">{{ nowLabel }}</span>
      <span v-if="activeIndex >= 0" class="of num">第 {{ activeIndex + 1 }} / {{ TIER_LADDER.length }} 档</span>
      <span v-else class="of">还没定段</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { TIER_LADDER, tierIndexOf, tierRampColor, rankLabel } from '../constants'

const props = defineProps({
  tier: { type: String, default: '' }
})

const activeIndex = computed(() => tierIndexOf(props.tier))
const activeColor = computed(() => (activeIndex.value >= 0 ? tierRampColor(props.tier) : 'var(--text-mute)'))
const nowLabel = computed(() => (activeIndex.value >= 0 ? rankLabel(props.tier) : '未定段'))

const ariaLabel = computed(() =>
  activeIndex.value >= 0
    ? `段位路标：${nowLabel.value}，共 ${TIER_LADDER.length} 档中的第 ${activeIndex.value + 1} 档`
    : '段位路标：还没有选择段位'
)
</script>

<style scoped>
.rail {
  margin-top: 14px;
}

.bars {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 12px;
}

/* 十段等宽色带。只有当前段是实心的、并且向上冒出一截 —— 一眼看出自己在哪一格。 */
.bar {
  flex: 1;
  height: 6px;
  border-radius: 2px;
  opacity: 0.24;
}

.bar.on {
  height: 12px;
  opacity: 1;
}

.legend {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin-top: 8px;
  font-size: var(--fs-micro);
}

.now {
  font-weight: 600;
}

.of {
  color: var(--text-mute);
}
</style>
