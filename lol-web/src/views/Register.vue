<template>
  <AuthLayout>
    <h2 class="title">注册</h2>
    <p class="sub">创建账号，之后再去资料页填游戏信息</p>

    <FormAlert
      v-if="serverError"
      class="banner"
      type="error"
      title="注册失败"
      :message="serverError"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      :disabled="loading"
      @submit.prevent="handleRegister"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model.trim="form.username" placeholder="登录时使用，3–20 位字母数字" autocomplete="username" />
      </el-form-item>

      <el-form-item label="昵称" prop="nickname">
        <el-input v-model.trim="form.nickname" placeholder="站内显示的名字" />
        <p class="hint">这是别人在列表里看到的名字。你的游戏内 ID 之后在资料页单独填「召唤师名」。</p>
      </el-form-item>

      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="至少 6 位"
          autocomplete="new-password"
          show-password
        />
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          placeholder="再输一次"
          autocomplete="new-password"
          show-password
        />
      </el-form-item>

      <el-button
        type="primary"
        native-type="submit"
        size="large"
        class="submit"
        :loading="loading"
      >
        创建账号
      </el-button>
    </el-form>

    <p class="tip">
      已经有账号了？
      <router-link to="/login">去登录</router-link>
    </p>
  </AuthLayout>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import AuthLayout from '../components/AuthLayout.vue'
import FormAlert from '../components/FormAlert.vue'
import { register } from '../api/auth'

const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const serverError = ref('')

const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })

// 说明：这是前端的软约束。后端 UserService.register 目前只检查用户名重复，
// 没有长度、格式和密码强度校验，所以这些规则绕过去仍然能注册成功。
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名 3–20 位', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '只能用字母、数字和下划线', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 20, message: '昵称最多 20 个字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码 6–32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再输一次密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一样'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function handleRegister() {
  serverError.value = ''

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await register({
      username: form.username,
      nickname: form.nickname,
      password: form.password
    })
    // 不用全局 toast，直接带着结果跳到登录页，提示显示在登录页顶部
    router.replace({ path: '/login', query: { registered: '1', u: form.username } })
  } catch (e) {
    // 后端唯一会拦的是重名，原样透出「用户名已存在」
    serverError.value = e.userMessage || '注册没成功，请重试。'
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

.hint {
  margin-top: 6px;
  font-size: var(--fs-micro);
  line-height: 1.5;
  color: var(--text-mute);
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

:deep(.el-form-item) {
  margin-bottom: 18px;
}
</style>
