import { reactive } from 'vue'

// 好友抽屉是全局的：顶栏打开它，玩家资料页/房间页也共用同一份状态
export const friendDrawer = reactive({ visible: false })

export function openFriendDrawer() {
  friendDrawer.visible = true
}

export function closeFriendDrawer() {
  friendDrawer.visible = false
}
