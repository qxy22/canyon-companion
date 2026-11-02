<template>
  <div class="lobby">
    <!-- 两张卡片内容都不少，挤在一屏里谁都看不清，改成切换。
         两个面板都保持挂载（v-show），切回来时不用重新加载，也不会丢滚动位置。 -->
    <div class="tabs" role="tablist" aria-label="大厅视图切换">
      <button
        type="button"
        role="tab"
        class="tab"
        :class="{ on: active === 'teamup' }"
        :aria-selected="active === 'teamup'"
        @click="active = 'teamup'"
      >
        开黑大厅
        <span v-if="teamupCount" class="n num">{{ teamupCount }}</span>
      </button>
      <button
        type="button"
        role="tab"
        class="tab"
        :class="{ on: active === 'online' }"
        :aria-selected="active === 'online'"
        @click="active = 'online'"
      >
        在线可开黑
        <span v-if="onlineCount" class="n num">{{ onlineCount }}</span>
      </button>
    </div>

    <!-- 两个面板都保持挂载（切回来不用重新加载），但只有看得见的那个在轮询。
         active 传进去就是让看不见的那边把定时刷停掉。 -->
    <div class="panel">
      <TeamupFeed
        v-show="active === 'teamup'"
        :active="active === 'teamup'"
        @count="teamupCount = $event"
      />
      <OnlinePanel
        v-show="active === 'online'"
        :active="active === 'online'"
        @count="onlineCount = $event"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import TeamupFeed from './TeamupFeed.vue'
import OnlinePanel from './OnlinePanel.vue'

const active = ref('teamup')
const teamupCount = ref(0)
const onlineCount = ref(0)
</script>

<style scoped>
.lobby {
  min-width: 0;
}

/* 分段控件沿用身份卡里那套：ink 底槽 + 金色选中态，全站一个样子 */
.tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  padding: 4px;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.tab {
  appearance: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-dim);
  font-family: inherit;
  font-size: var(--fs-body);
  font-weight: 600;
  padding: 8px 10px;
  border-radius: 5px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.tab:hover:not(.on) {
  color: var(--text);
  background: var(--panel-hi);
}

.tab.on {
  background: var(--panel);
  border-color: var(--gold);
  color: var(--gold);
}

/* 数字徽标：不切过去也知道那一边有没有东西 */
.n {
  font-size: var(--fs-micro);
  font-weight: 700;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 999px;
  background: var(--ink);
  border: 1px solid var(--line);
  color: var(--text-dim);
}

.tab.on .n {
  color: var(--gold);
  border-color: var(--gold-dim);
}

.panel {
  margin-top: 12px;
}
</style>
