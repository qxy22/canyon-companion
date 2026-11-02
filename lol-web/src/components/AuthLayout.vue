<template>
  <div class="auth">
    <div class="shell">
      <section class="intro">
        <p class="eyebrow">英雄联盟 · 开黑队友</p>
        <h1 class="wordmark">LOL 战绩交友</h1>
        <p class="lede">
          读取你本机游戏客户端最近 10 场的战绩，算出实力分，再按段位和位置帮你找到合适的开黑队友。
        </p>

        <ul class="points">
          <li v-for="p in points" :key="p.k">
            <span class="mark" aria-hidden="true" />
            <span class="k">{{ p.k }}</span>
            <span class="v">{{ p.v }}</span>
          </li>
        </ul>
      </section>

      <section class="panel">
        <slot />
      </section>
    </div>
  </div>
</template>

<script setup>
const points = [
  { k: '看战绩', v: '从本机客户端直接读，不用手动填' },
  { k: '找队友', v: '按段位相近、位置互补排序推荐' },
  { k: '开黑组队', v: '发布招募，或加入别人的队伍' }
]
</script>

<style scoped>
.auth {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.shell {
  width: 100%;
  max-width: 880px;
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 56px;
  align-items: center;
}

.eyebrow {
  color: var(--gold);
}

.wordmark {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.01em;
  margin-top: 6px;
}

/* 标题下的一道细金线：全页唯一的结构性装饰 */
.wordmark::after {
  content: "";
  display: block;
  width: 56px;
  height: 1px;
  background: var(--gold);
  margin-top: 14px;
}

.lede {
  margin-top: 20px;
  max-width: 44ch;
  color: var(--text-dim);
}

.points {
  list-style: none;
  margin: 24px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.points li {
  display: grid;
  grid-template-columns: 10px max-content 1fr;
  align-items: baseline;
  gap: 10px;
  font-size: var(--fs-small);
}

.points .mark {
  width: 7px;
  height: 7px;
  transform: rotate(45deg) translateY(-1px);
  background: var(--gold);
}

.points .k {
  font-weight: 600;
  color: var(--text);
}

.points .v {
  color: var(--text-dim);
}

.panel {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 26px 24px 24px;
}

@media (max-width: 860px) {
  .auth {
    padding: 28px 20px;
    align-items: flex-start;
  }
  .shell {
    grid-template-columns: 1fr;
    gap: 28px;
    max-width: 440px;
  }
  .wordmark {
    font-size: 26px;
  }
  .lede {
    margin-top: 16px;
  }
  .points {
    margin-top: 18px;
    gap: 8px;
  }
}
</style>
