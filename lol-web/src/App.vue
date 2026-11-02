<template>
  <router-view />

  <!-- 全局通知：好友邀请、被移出房间的提醒。挂在最外层，任何页面都收得到 -->
  <GlobalNotify />

  <!-- 好友抽屉也是全局的，顶栏和各页面共用同一份状态 -->
  <FriendDrawer />
</template>

<script setup>
import { onMounted } from 'vue'
import GlobalNotify from './components/GlobalNotify.vue'
import FriendDrawer from './components/FriendDrawer.vue'
import { startPresence } from './composables/usePresence'

// 进站就在线。没有登录态时 startPresence 自己会跳过；
// 登录成功那一刻由 Login 再调一次（那时 App 已经挂载完了）。
onMounted(() => { startPresence() })
</script>
