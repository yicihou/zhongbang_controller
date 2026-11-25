<template>
  <div>
    <div class="page-head">
      <div>
        <h2>员工管理</h2>
        <p>老板、管理员、业务员和师傅。停用的师傅不会出现在派工下拉框里。只有老板能改这里。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增员工</el-button>
    </div>
    <div class="panel">
      <div class="filters">
        <el-input v-model="keyword" clearable placeholder="姓名 / 手机 / 用户名" style="width: 220px" @keyup.enter="reload" />
        <el-select v-model="role" clearable placeholder="全部角色" style="width: 140px" @change="reload">
          <el-option v-for="item in STAFF_ROLE_OPTIONS" :key="item" :label="STAFF_ROLE_LABEL[item]" :value="item" />
        </el-select>
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 140px" @change="reload">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-button @click="reload">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" row-key="ref">
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="mobile" label="手机号" width="140" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">{{ STAFF_ROLE_LABEL[row.role] || row.role }}</template>
        </el-table-column>
        <el-table-column label="登录账号" min-width="140">
          <template #default="{ row }">{{ row.username || '未开通' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ STAFF_STATUS_LABEL[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" :disabled="!row.loginAccount" @click="openPassword(row)">重置密码</el-button>
            <el-button link type="primary" @click="toggle(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPage"
        />
      </div>
    </div>

    <el-dialog v-model="editorVisible" :title="form.ref ? '编辑员工' : '新增员工'" width="560px">
      <el-form label-width="96px">
        <el-form-item label="姓名" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="form.mobile" maxlength="11" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="item in STAFF_ROLE_OPTIONS" :key="item" :label="STAFF_ROLE_LABEL[item]" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ENABLED">启用</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.role === 'TECHNICIAN'">
          <el-form-item label="技能">
            <el-input v-model="form.skillsText" placeholder="逗号分隔，例如 平缝机,包缝机" />
          </el-form-item>
          <el-form-item label="区域">
            <el-input v-model="form.region" />
          </el-form-item>
        </template>
        <el-form-item label="用户名" :required="form.role !== 'TECHNICIAN'">
          <el-input v-model="form.username" :placeholder="form.role === 'TECHNICIAN' ? '不填则不能登录后台' : '登录账号'" />
        </el-form-item>
        <el-form-item v-if="!form.loginAccount" label="密码" :required="form.role !== 'TECHNICIAN'">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位。已有账号请用重置密码" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="重置密码" width="420px">
      <el-form label-width="88px">
        <el-form-item label="新密码">
          <el-input v-model="password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 员工管理。列表把登录账号和还没开通登录的师傅合在一起。
 * 师傅停用后，报修详情和订单安装的下拉框都不再列出他。
 */
import { onMounted, reactive, ref } from 'vue'
import { changeStaffStatus, createStaff, listStaffMembers, resetStaffPassword, updateStaff } from '@/api/admin'
import type { StaffMember, StaffRole, StaffStatus } from '@/domain/staff'
import { STAFF_ROLE_LABEL, STAFF_ROLE_OPTIONS, STAFF_STATUS_LABEL } from '@/domain/labels'
import { run } from '@/utils/feedback'

const rows = ref<StaffMember[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const role = ref('')
const status = ref('')
const loading = ref(false)
const editorVisible = ref(false)
const passwordVisible = ref(false)
const password = ref('')
const passwordRef = ref('')

const form = reactive({
  ref: '',
  loginAccount: false,
  name: '',
  mobile: '',
  role: 'TECHNICIAN' as StaffRole,
  status: 'ENABLED' as StaffStatus,
  username: '',
  password: '',
  remark: '',
  skillsText: '',
  region: '',
})

onMounted(reload)

async function reload() {
  loading.value = true
  const ok = await run(async () => {
    const data = await listStaffMembers({
      keyword: keyword.value.trim(),
      role: role.value,
      status: status.value,
      page: page.value,
      size: size.value,
    })
    rows.value = data.list
    total.value = data.total
  })
  loading.value = false
  if (!ok) return
}

function onPage(next: number) {
  page.value = next
  void reload()
}

function openCreate() {
  form.ref = ''
  form.loginAccount = false
  form.name = ''
  form.mobile = ''
  form.role = 'TECHNICIAN'
  form.status = 'ENABLED'
  form.username = ''
  form.password = ''
  form.remark = ''
  form.skillsText = ''
  form.region = ''
  editorVisible.value = true
}

function openEdit(row: StaffMember) {
  form.ref = row.ref
  form.loginAccount = row.loginAccount
  form.name = row.name
  form.mobile = row.mobile
  form.role = row.role
  form.status = row.status
  form.username = row.username ?? ''
  form.password = ''
  form.remark = row.remark
  form.skillsText = row.skills.join(',')
  form.region = row.region
  editorVisible.value = true
}

function body() {
  return {
    name: form.name,
    mobile: form.mobile,
    role: form.role,
    status: form.status,
    remark: form.remark,
    username: form.username,
    password: form.password || undefined,
    skills: form.role === 'TECHNICIAN' ? form.skillsText.split(/[,，]/).map((item) => item.trim()).filter(Boolean) : [],
    region: form.role === 'TECHNICIAN' ? form.region : '',
  }
}

async function submit() {
  const ok = await run(async () => {
    if (form.ref) await updateStaff(form.ref, body())
    else await createStaff(body())
  }, '已保存')
  if (!ok) return
  editorVisible.value = false
  await reload()
}

async function toggle(row: StaffMember) {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const ok = await run(async () => {
    await changeStaffStatus(row.ref, next)
  }, next === 'DISABLED' ? '已停用' : '已启用')
  if (ok) await reload()
}

function openPassword(row: StaffMember) {
  passwordRef.value = row.ref
  password.value = ''
  passwordVisible.value = true
}

async function submitPassword() {
  const ok = await run(async () => {
    await resetStaffPassword(passwordRef.value, password.value)
  }, '密码已重置')
  if (!ok) return
  passwordVisible.value = false
}
</script>
