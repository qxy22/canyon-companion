<template>
  <div class="page">
    <p class="code num">404</p>
    <h1>这个地址没有页面</h1>
    <p class="hint">可能是链接打错了，或者这个功能还没做。</p>
    <el-button type="primary" @click="goHome">回到首页</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  const token = localStorage.getItem('token')
  router.replace(token ? '/' : '/login')
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  text-align: center;
}
.code {
  font-family: var(--font-num);
  font-size: 56px;
  line-height: 1;
  color: var(--gold);
  letter-spacing: 0.04em;
}
h1 {
  font-size: var(--fs-title);
}
.hint {
  color: var(--text-dim);
  margin-bottom: 8px;
}
</style>
