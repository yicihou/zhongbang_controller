import { ElMessage } from 'element-plus'
import { ApiError } from '@/domain/errors'
import { router } from '@/router'
import { useSessionStore } from '@/stores/session'

export async function run(task: () => Promise<void>, okText?: string): Promise<boolean> {
  try {
    await task()
    if (okText) ElMessage.success(okText)
    return true
  } catch (error) {
    const session = useSessionStore()
    if (error instanceof ApiError && error.code === 2001) {
      session.logout()
      await router.push('/login')
    }
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
    return false
  }
}
