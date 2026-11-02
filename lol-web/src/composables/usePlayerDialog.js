import { reactive } from 'vue'

// 全局共享的"玩家战绩弹窗"状态，任何组件点头像都能打开
export const playerDialog = reactive({ visible: false, user: null })

export function openPlayerDialog(user) {
  if (!user) return
  playerDialog.user = user
  playerDialog.visible = true
}
