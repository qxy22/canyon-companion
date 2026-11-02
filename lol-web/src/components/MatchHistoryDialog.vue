<template>
  <el-dialog
    :model-value="modelValue"
    width="640px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="head">
        <h3>{{ user ? `${user.nickname} 的战绩` : '玩家战绩' }}</h3>
        <p class="eyebrow">按时间倒序，每页 10 场</p>
      </div>
    </template>

    <div v-if="user">
      <PlayerSummary :user="user" />

      <MatchRecordTable
        :records="records"
        :total="total"
        :size="size"
        :page="page"
        :loading="loading"
        @update:page="load"
      />
    </div>

    <template #footer>
      <el-button @click="close">关闭</el-button>
      <el-button type="primary" :disabled="!user" @click="goDetail">查看完整资料</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getUserMatches } from '../api/match'
import PlayerSummary from './PlayerSummary.vue'
import MatchRecordTable from './MatchRecordTable.vue'

const props = defineProps({
  modelValue: Boolean,
  user: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])

const router = useRouter()

const records = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = 10

// 只监听打开动作就够了：换玩家必定经过 openPlayerDialog，而它会重新置 visible。
watch(
  () => props.modelValue,
  val => {
    if (val && props.user) {
      load(1)
    }
  }
)

/** 翻页和首次加载都走这里，显式带页码，不用再监听 page 变化（否则会重复请求）。 */
function load(targetPage = page.value) {
  if (!props.user) return
  page.value = targetPage
  loading.value = true
  getUserMatches(props.user.id, { page: targetPage, size })
    .then(res => {
      records.value = (res.data && res.data.list) || []
      total.value = (res.data && res.data.total) || 0
    })
    .catch(() => {
      records.value = []
      total.value = 0
    })
    .finally(() => { loading.value = false })
}

function close() {
  emit('update:modelValue', false)
}

// 弹窗只够看个大概，完整资料（大区、签名、最近战绩小结）在资料页
function goDetail() {
  if (!props.user) return
  close()
  router.push(`/player/${props.user.id}`)
}
</script>

<style scoped>
.head h3 {
  font-size: 17px;
}

.eyebrow {
  margin-top: 2px;
}
</style>
