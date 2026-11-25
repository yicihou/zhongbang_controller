/**
 * 侧栏和分组标签的唯一配置。
 * 以前每个页面都是一个侧栏项，侧栏越堆越长；现在把同类页面合成一组，
 * 侧栏只显示组名，组内页面用页面顶部的标签切换。
 * 标签地址仍然用原来的路由地址（例如 /products、/stock-alerts），
 * 所以旧书签、通知跳转和页面里的 router.push 都不用改。
 */

/** 侧栏图标名。AdminLayout.vue 里有一张同名的图标表，换成真正的组件。 */
export type NavIcon = 'Odometer' | 'Goods' | 'Wallet' | 'Box' | 'Tools' | 'Avatar' | 'TrendCharts'

/** 一个标签页。 */
export interface NavTab {
  /** 标签对应的路由地址。 */
  path: string
  /** 路由名，方便排查问题和写单测。 */
  routeName: string
  /** 标签上显示的文字。 */
  label: string
  /** 只有老板能看到这个标签，例如数据导入。 */
  bossOnly?: boolean
}

/** 侧栏里的一个一级项。 */
export interface NavItem {
  /** 分组标识，工作台等固定项也用它。 */
  key: string
  /** 侧栏显示的文字。 */
  label: string
  icon: NavIcon
  /** 点击侧栏后进入的地址。分组取第一个当前角色能看到的标签。 */
  path: string
  /** 组内标签页。工作台没有标签页；经营分析只有一个页面，也不显示标签栏。 */
  tabs: NavTab[]
  /** 整组只有老板可见，例如客户和经营分析。 */
  bossOnly?: boolean
  /** 侧栏角标显示哪种未读提醒。 */
  badge?: 'order' | 'repair'
}

/**
 * 侧栏顺序。每个分组里的标签顺序就是页面顶部标签的顺序。
 * 新增页面时先看它属于哪一组，再把地址加进 tabs；不要直接往侧栏塞一项。
 */
export const NAV_ITEMS: NavItem[] = [
  {
    key: 'dashboard',
    label: '工作台',
    icon: 'Odometer',
    path: '/',
    tabs: [],
  },
  {
    key: 'inventory',
    label: '商品库存',
    icon: 'Goods',
    path: '/products',
    tabs: [
      { path: '/products', routeName: 'products', label: '商品' },
      { path: '/stock-alerts', routeName: 'stock-alerts', label: '库存预警' },
      // 进货、销售、取消和盘点都记在这一页，老板按它对库存。
      { path: '/stock-logs', routeName: 'stock-logs', label: '库存流水' },
      // 数据导入一直只有老板能看，路由守卫里也有同样的 boss 标记。
      { path: '/imports', routeName: 'imports', label: '数据导入', bossOnly: true },
    ],
  },
  {
    key: 'orders',
    label: '订单收款',
    icon: 'Wallet',
    path: '/orders',
    badge: 'order',
    tabs: [
      { path: '/orders', routeName: 'orders', label: '订单' },
      { path: '/receivables', routeName: 'receivables', label: '待收款' },
    ],
  },
  {
    key: 'purchase',
    label: '采购',
    icon: 'Box',
    path: '/purchases',
    tabs: [
      { path: '/purchases', routeName: 'purchases', label: '采购入库' },
      { path: '/suppliers', routeName: 'suppliers', label: '供应商' },
    ],
  },
  {
    key: 'service',
    label: '售后服务',
    icon: 'Tools',
    path: '/repairs',
    badge: 'repair',
    tabs: [
      { path: '/repairs', routeName: 'repairs', label: '报修' },
      { path: '/technicians', routeName: 'technicians', label: '师傅排期' },
    ],
  },
  {
    key: 'customers',
    label: '客户',
    icon: 'Avatar',
    path: '/customers',
    bossOnly: true,
    tabs: [
      { path: '/customers', routeName: 'customers', label: '客户列表' },
      { path: '/customers/reminders', routeName: 'customer-reminders', label: '还款提醒' },
      { path: '/customers/analysis', routeName: 'customer-analysis', label: '客户分析' },
      { path: '/customers/levels', routeName: 'customer-levels', label: '等级规则' },
    ],
  },
  {
    key: 'analytics',
    label: '经营分析',
    icon: 'TrendCharts',
    path: '/margin',
    bossOnly: true,
    tabs: [{ path: '/margin', routeName: 'margin', label: '销售毛利' }],
  },
]

/** 去掉末尾多余的斜杠，让 /orders/ 和 /orders 当成同一个地址。 */
function normalizePath(path: string): string {
  return path !== '/' ? path.replace(/\/+$/, '') : path
}

/**
 * 按角色过滤侧栏。
 * 分组里没有任何当前角色能看的标签时，整组隐藏；工作台没有标签，始终保留。
 * 返回的是新对象，不会改上面的 NAV_ITEMS。
 */
export function visibleNavItems(isBoss: boolean): NavItem[] {
  const result: NavItem[] = []
  for (const item of NAV_ITEMS) {
    if (item.bossOnly && !isBoss) continue
    const tabs = item.tabs.filter((tab) => !tab.bossOnly || isBoss)
    if (item.tabs.length > 0 && tabs.length === 0) continue
    // 点击侧栏进入第一个能看的标签；没有标签的固定项仍用自己的地址。
    result.push({ ...item, path: tabs[0]?.path ?? item.path, tabs })
  }
  return result
}

/**
 * 用当前地址找到侧栏分组。
 * 标签地址优先精确匹配；订单详情 /orders/123 这类页面没有自己的标签，
 * 就按前缀归到列表所在分组，侧栏仍然高亮。
 */
export function navItemForPath(path: string): NavItem | undefined {
  const clean = normalizePath(path)
  const exact = NAV_ITEMS.find(
    (item) => item.tabs.some((tab) => tab.path === clean) || (item.tabs.length === 0 && item.path === clean),
  )
  if (exact) return exact
  return NAV_ITEMS.find((item) => item.tabs.some((tab) => clean.startsWith(`${tab.path}/`)))
}

/**
 * 当前地址正好是组内某个标签时返回这个标签，否则返回 undefined。
 * 页面顶部标签栏用它判断要不要显示：订单详情 /orders/123 不是标签页，
 * 不显示标签栏，避免和详情页自己的标签重复。
 */
export function tabForPath(item: NavItem, path: string): NavTab | undefined {
  const clean = normalizePath(path)
  return item.tabs.find((tab) => tab.path === clean)
}

/**
 * 当前地址应该高亮哪个标签。
 * 详情页（/orders/123、/repairs/123、/customers/123）高亮它所属的列表标签。
 */
export function activeTabPath(item: NavItem, path: string): string | undefined {
  const clean = normalizePath(path)
  const exact = item.tabs.find((tab) => tab.path === clean)
  if (exact) return exact.path
  const matched = item.tabs
    .filter((tab) => clean.startsWith(`${tab.path}/`))
    .sort((a, b) => b.path.length - a.path.length)[0]
  return matched?.path ?? item.tabs[0]?.path
}
