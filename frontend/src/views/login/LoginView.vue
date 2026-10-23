<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)
const errMsg = ref('')

async function submit() {
  errMsg.value = ''
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/dashboard'
    router.replace(redirect)
  } catch (e) {
    errMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <div class="login-card">
      <h1>大润发电商管理系统</h1>
      <form class="login-form" @submit.prevent="submit">
        <label>用户名</label>
        <input v-model="form.username" type="text" autocomplete="username" required />
        <label>密码</label>
        <input v-model="form.password" type="password" autocomplete="current-password" required />
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
        <p v-if="errMsg" class="login-error">{{ errMsg }}</p>
      </form>
      <p class="login-hint">演示管理员账号：admin / admin123（登录后进入运营后台）</p>
    </div>
  </div>
</template>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
}
.login-card {
  background: #fff;
  padding: 40px 36px;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,.08);
  width: 380px;
}
.login-card h1 {
  text-align: center;
  font-size: 18px;
  margin-bottom: 28px;
  color: var(--brand-700, #931512);
}
.login-form label {
  display: block;
  font-size: 13px;
  margin: 14px 0 6px;
  color: #555;
}
.login-form input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  box-sizing: border-box;
}
.login-form button {
  width: 100%;
  margin-top: 20px;
  padding: 10px;
  background: var(--brand-500, #DE1313);
  color: #fff;
  border: 0;
  border-radius: 4px;
  font-size: 15px;
  cursor: pointer;
}
.login-form button:disabled { opacity: .6; cursor: not-allowed; }
.login-error {
  margin-top: 12px;
  color: #DE1313;
  font-size: 13px;
}
.login-hint {
  margin-top: 16px;
  font-size: 12px;
  color: #999;
  text-align: center;
}
</style>
