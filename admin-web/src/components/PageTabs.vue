<template>
  <div v-if="group && currentTab && group.tabs.length > 1" class="page-tabs">
    <el-tabs :model-value="currentTab.path" @tab-change="onTabChange">
      <el-tab-pane v-for="tab in group.tabs" :key="tab.path" :label="tab.label" :name="tab.path" />
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
/**
 * 分组页面顶部的标签栏。
 * 标签地址仍然用原来的页面地址（/products、/stock-alerts 等），
 * 所以切换标签只是普通的 router.push；刷新页面、旧书签和通知跳转都会落在同一个地址上。
 * 详情页（例如 /orders/123）没有对应标签，这里不显示标签栏，侧栏仍由 AdminLayout 高亮所属分组。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { navItemForPath, tabForPath, visibleNavItems } from '@/domain/navigation'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

/** 先按当前角色过滤，管理员看不到数据导入等老板专属标签。 */
const group = computed(() => {
  const item = navItemForPath(route.path)
  if (!item) return undefined
  return visibleNavItems(session.isBoss).find((candidate) => candidate.key === item.key)
})

/** 只有地址正好是组内标签时才显示标签栏；详情页不显示。 */
const currentTab = computed(() => (group.value ? tabForPath(group.value, route.path) : undefined))

function onTabChange(name: string | number) {
  const path = String(name)
  if (path !== route.path) void router.push(path)
}
</script>

<style scoped>
.page-tabs {
  background: #fff;
  border: 1px solid var(--zb-line);
  border-radius: 12px;
  padding: 0 16px;
  margin-bottom: 12px;
}
.page-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.page-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
</style>
