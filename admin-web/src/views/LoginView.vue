<template>
  <div class="login-page">
    <div class="card">
      <div class="banner">
        <b>众邦缝纫机</b>
        <span>商家管理后台</span>
      </div>
      <div class="body">
        <h1>员工登录</h1>
        <p class="hint">使用员工账号。进入后台需要 JWT 角色为 STAFF（ROLE_STAFF）。</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="username" data-testid="login-username" autocomplete="username" placeholder="admin" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="password"
              data-testid="login-password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button data-testid="login-submit" type="primary" class="go" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form>
        <p v-if="mockMode" class="demo">Mock 账号 admin / admin123，客服 clerk / clerk123</p>
        <el-collapse class="token-box">
          <el-collapse-item title="已有 STAFF Token" name="token">
            <el-input v-model="pasted" type="textarea" :rows="3" placeholder="粘贴后端签发的 JWT" />
            <el-button class="token-btn" @click="useToken">用该 Token 进入</el-button>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { USE_MOCK } from '@/config'
import { useSessionStore } from '@/stores/session'
import { run } from '@/utils/feedback'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const username = ref('admin')
const password = ref('')
const pasted = ref('')
const loading = ref(false)
const mockMode = USE_MOCK

async function submit() {
  loading.value = true
  const ok = await run(async () => {
    await session.login(username.value.trim(), password.value)
  })
  loading.value = false
  if (!ok) return
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  await router.replace(redirect)
}

function useToken() {
  try {
    session.adopt(pasted.value.trim())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '令牌无效')
    return
  }
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  void router.replace(redirect)
}
</script>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 80% 10%, rgba(200, 16, 46, 0.16), transparent 32%),
    var(--zb-bg);
}
.card {
  width: 420px;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 24px 60px rgba(20, 25, 35, 0.12);
}
.banner {
  background: linear-gradient(135deg, #c8102e 0%, #96061f 100%);
  color: #fff;
  padding: 28px 28px 22px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.banner b {
  font-size: 22px;
}
.banner span {
  opacity: 0.88;
  font-size: 13px;
}
.body {
  padding: 22px 28px 28px;
}
h1 {
  margin: 0 0 6px;
  font-size: 18px;
}
.hint,
.demo {
  color: var(--zb-muted);
  font-size: 13px;
  line-height: 1.6;
}
.go {
  width: 100%;
  height: 40px;
}
.token-box {
  margin-top: 12px;
  border: none;
}
.token-btn {
  margin-top: 8px;
}
</style>
