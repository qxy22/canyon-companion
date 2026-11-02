<template>
  <!-- 玩家身份 + 实力摘要。资料弹窗和玩家资料页共用一份，避免两处各写一遍后走偏。 -->
  <div class="summary">
    <el-avatar :size="56" :src="user.avatar">{{ initial }}</el-avatar>

    <div class="meta">
      <div class="name-row">
        <span class="name">{{ user.nickname || '未知玩家' }}</span>
        <el-tag size="small" :type="statusTagType" effect="plain">
          {{ statusLabel(user.status) }}
        </el-tag>
      </div>

      <div class="tags">
        <el-tag size="small" effect="plain">{{ positionLabel(user.position) }}</el-tag>
        <el-tag size="small" effect="plain" type="warning">{{ rankLabel(user.rankTier) }}</el-tag>
        <span v-if="user.summonerName" class="plain">{{ user.summonerName }}</span>
        <span v-if="user.region" class="plain">{{ user.region }}</span>
      </div>
    </div>

    <div class="score">
      <!-- 没有战绩时后端给的 skillTier 是 null。实心色块 + 破折号会让人以为是加载失败，
           所以空态改用虚线问号，一眼能看出是"暂无数据"而不是"没加载出来"。 -->
      <div
        class="tier"
        :class="{ 'is-empty': !user.skillTier }"
        :style="user.skillTier ? { background: gradeColor(user.skillTier) } : null"
      >
        {{ user.skillTier || '?' }}
      </div>
      <div class="score-text">
        <div class="score-num num" :class="{ 'is-empty': user.skillScore == null }">
          {{ user.skillScore ?? '—' }}
        </div>
        <div class="score-label">综合实力分</div>
      </div>
    </div>

    <div class="rail-wrap">
      <TierRail :tier="user.rankTier" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { positionLabel, rankLabel, statusLabel, gradeColor } from '../constants'
import TierRail from './TierRail.vue'

const props = defineProps({
  user: { type: Object, default: () => ({}) }
})

const initial = computed(() => ((props.user.nickname || 'L').charAt(0) || 'L').toUpperCase())

const statusTagType = computed(() => {
  if (props.user.status === 'ONLINE') return 'success'
  if (props.user.status === 'BUSY') return 'warning'
  return 'info'
})
</script>

<style scoped>
.summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
}

.meta {
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.name {
  font-size: 17px;
  font-weight: 600;
  word-break: break-all;
}

.tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.plain {
  font-size: var(--fs-micro);
  color: var(--text-mute);
}

.score {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
}

.tier {
  width: 46px;
  height: 46px;
  border-radius: var(--radius-sm);
  color: #0a1428;
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tier.is-empty {
  border: 1px dashed var(--line);
  color: var(--text-mute);
  font-weight: 500;
}

.score-text {
  text-align: right;
}

.score-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--teal);
  line-height: 1.15;
}

.score-num.is-empty {
  color: var(--text-mute);
  font-weight: 500;
}

.score-label {
  font-size: var(--fs-micro);
  color: var(--text-mute);
}

/* TierRail 自带 14px 上边距，这里收一点，与栅格间距对齐 */
.rail-wrap {
  grid-column: 1 / -1;
  margin-top: -6px;
}

@media (max-width: 520px) {
  .summary {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .score {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }
}
</style>
