<template>
  <!-- 纯展示组件：不请求数据，由外层容器（资料弹窗 / 资料页）决定加载哪一页 -->
  <div class="record-table">
    <el-skeleton v-if="loading" :rows="4" animated class="sk" />

    <div v-else-if="records.length === 0" class="empty">
      <p class="empty-title">这位玩家还没有录入战绩</p>
      <p class="empty-hint">
        战绩要从他本机的游戏客户端读取。他在自己的资料页点过「录入战绩」之后，这里才会有数据。
      </p>
    </div>

    <template v-else>
      <div class="table">
        <div class="tr th">
          <span>结果</span>
          <span>位置</span>
          <span>击杀 / 死亡 / 助攻</span>
          <span class="ta-r">补刀</span>
          <span class="ta-r">视野</span>
          <span class="ta-r">评分</span>
        </div>

        <div v-for="r in records" :key="r.id" class="tr">
          <span class="result" :class="r.win ? 'win' : 'lose'">{{ r.win ? '胜' : '负' }}</span>
          <span class="lane">{{ positionLabel(r.lane) }}</span>
          <span class="kda num">{{ r.kills }} / {{ r.deaths }} / {{ r.assists }}</span>
          <span class="ta-r num">
            {{ r.cs }}
            <em v-if="r.csPerMin" class="sub">{{ r.csPerMin.toFixed(1) }}/分</em>
          </span>
          <span class="ta-r num">{{ r.visionScore }}</span>
          <span class="ta-r num score-cell">{{ Math.round(r.score) }}</span>
        </div>
      </div>

      <div v-if="total > size" class="pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="changePage"
        />
      </div>
    </template>
  </div>
</template>

<script setup>
import { positionLabel } from '../constants'

defineProps({
  records: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:page'])

function changePage(p) {
  emit('update:page', p)
}
</script>

<style scoped>
.sk {
  margin-top: 20px;
}

.empty {
  padding: 22px 0 6px;
}

.empty-title {
  font-weight: 600;
}

.empty-hint {
  margin-top: 6px;
  font-size: var(--fs-small);
  line-height: 1.7;
  color: var(--text-mute);
  max-width: 52ch;
}

.table {
  margin-top: 18px;
  border-top: 1px solid var(--line);
}

.tr {
  display: grid;
  grid-template-columns: 44px 64px minmax(0, 1fr) 74px 54px 52px;
  gap: 10px;
  align-items: center;
  padding: 9px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: var(--fs-small);
}

.tr.th {
  color: var(--text-mute);
  font-size: var(--fs-micro);
  border-bottom-color: var(--line);
}

.ta-r {
  text-align: right;
}

.result {
  font-weight: 600;
}

.result.win {
  color: var(--teal);
}

.result.lose {
  color: var(--ember);
}

.lane {
  color: var(--text-dim);
}

.kda {
  color: var(--text);
}

.sub {
  display: block;
  font-style: normal;
  font-size: 11px;
  color: var(--text-mute);
  line-height: 1.2;
}

.score-cell {
  color: var(--teal);
  font-weight: 600;
}

.pager {
  margin-top: 14px;
  text-align: right;
}

@media (max-width: 640px) {
  .tr {
    grid-template-columns: 40px 58px minmax(0, 1fr) 62px 46px 44px;
    gap: 6px;
    font-size: var(--fs-micro);
  }
}
</style>
