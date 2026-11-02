import { ref } from 'vue'

/**
 * 一个全局的"数据变了，该重新拉一次"的信号。
 *
 * 场景：把状态切成「在线」之后，右侧的在线列表和推荐列表还是旧的，
 * 用户会以为没生效。改完资料/状态/战绩后调一次 requestRefresh()，
 * 依赖这些数据的面板自己重新加载。
 */
export const refreshTick = ref(0)

export function requestRefresh() {
  refreshTick.value += 1
}
