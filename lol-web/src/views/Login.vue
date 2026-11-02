<template>
  <AuthLayout>
    <h2 class="title">登录</h2>
    <p class="sub">用注册时填的用户名和密码</p>

    <FormAlert
      v-if="banner"
      class="banner"
      :type="banner.type"
      :title="banner.title"
      :message="banner.text"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      :disabled="loading"
      @submit.prevent="handleLogin"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model.trim="form.username" placeholder="例如：duanjin" autocomplete="username" />
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="登录密码"
          autocomplete="current-password"
          show-password
        />
      </el-form-item>

      <!-- native-type="submit" 才能让回车直接提交；原来按钮是 type=button，回车没反应 -->
      <el-button
        type="primary"
        native-type="submit"
        size="large"
        class="submit"
        :loading="loading"
      >
        登录
      </el-button>
    </el-form>

    <p class="tip">
      还没有账号？
      <router-link to="/register">注册一个</router-link>
    </p>
  </AuthLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthLayout from '../components/AuthLayout.vue'
import FormAlert from '../components/FormAlert.vue'
import { login } from '../api/auth'
import { startPresence } from '../composables/usePresence'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const serverError = ref('')

const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 页面级提示：登录失败 / 会话过期 / 注册成功
const banner = computed(() => {
  if (serverError.value) {
    return { type: 'error', title: '登录失败', text: serverError.value }
  }
  if (route.query.expired) {
    return { type: 'warning', title: '', text: '上次的登录状态已失效，请重新登录。' }
  }
  if (route.query.registered) {
    return { type: 'info', title: '', text: '账号已创建，用刚才的用户名登录。' }
  }
  return null
})

onMounted(() => {
  // 注册页跳过来时带上用户名，省一次输入
  if (route.query.u) {
    form.username = String(route.query.u)
  }
})

async function handleLogin() {
  serverError.value = ''

  try {
    await formRef.value.validate()
  } catch {
    return // 字段级错误已由 el-form 标出
  }

  loading.value = true
  try {
    const res = await login({ username: form.username, password: form.password })
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('user', JSON.stringify(res.data.user))

    // 登录成功就算在线。App 挂载时还没有 token，那次 startPresence 是空跑，
    // 所以这里必须再启动一次，否则要等用户刷新页面才会进「在线可开黑」。
    startPresence()

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    // silent 请求，错误由本页显示；后端的「用户名或密码错误」会原样带过来
    serverError.value = e.userMessage || '登录没成功，请重试。'
    form.password = ''
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.title {
  font-size: var(--fs-title);
}

.sub {
  margin-top: 4px;
  color: var(--text-mute);
  font-size: var(--fs-small);
}

.banner {
  margin: 16px 0 4px;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.tip {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
  text-align: center;
  font-size: var(--fs-small);
  color: var(--text-mute);
}

/* 去掉 Element 默认的表单项下边距，改由 gap 控制，间距更均匀 */
:deep(.el-form-item) {
  margin-bottom: 18px;
}
</style>
