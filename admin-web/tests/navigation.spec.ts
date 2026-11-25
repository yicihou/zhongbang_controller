/**
 * 测侧栏分组和标签地址。
 * 重点：老板看到 7 个一级项，管理员看到 5 个；员工管理不在侧栏；
 * 旧页面地址（/products、/stock-alerts 等）仍然能把侧栏和标签高亮到正确位置。
 */
import { describe, expect, it } from 'vitest'
import {
  NAV_ITEMS,
  activeTabPath,
  navItemForPath,
  tabForPath,
  visibleNavItems,
  type NavItem,
} from '../src/domain/navigation'

function labels(items: NavItem[]): string[] {
  return items.map((item) => item.label)
}

function take(items: NavItem[], key: string): NavItem {
  const found = items.find((item) => item.key === key)
  if (!found) throw new Error(`缺少侧栏分组 ${key}`)
  return found
}

describe('侧栏分组可见性', () => {
  it('老板看到 7 个侧栏项，员工管理不在侧栏', () => {
    const boss = visibleNavItems(true)
    expect(labels(boss)).toEqual(['工作台', '商品库存', '订单收款', '采购', '售后服务', '客户', '经营分析'])
    expect(boss).toHaveLength(7)
    expect(labels(boss)).not.toContain('员工管理')
  })

  it('管理员看到 5 个侧栏项，老板专属分组整组隐藏', () => {
    const admin = visibleNavItems(false)
    expect(labels(admin)).toEqual(['工作台', '商品库存', '订单收款', '采购', '售后服务'])
    expect(admin).toHaveLength(5)
    expect(admin.map((item) => item.key)).not.toContain('customers')
    expect(admin.map((item) => item.key)).not.toContain('analytics')
  })

  it('组内标签也按角色过滤，数据导入只给老板', () => {
    const bossInventory = take(visibleNavItems(true), 'inventory')
    const adminInventory = take(visibleNavItems(false), 'inventory')
    expect(bossInventory.tabs.map((tab) => tab.label)).toEqual(['商品', '库存预警', '库存流水', '数据导入'])
    expect(adminInventory.tabs.map((tab) => tab.label)).toEqual(['商品', '库存预警', '库存流水'])
    // 侧栏点击进入组内第一个当前角色能看的标签。
    expect(bossInventory.path).toBe('/products')
    expect(adminInventory.path).toBe('/products')
  })
})

describe('标签和旧地址的对应关系', () => {
  it('旧页面地址都能落到正确的分组和标签', () => {
    const cases: [string, string, string][] = [
      ['/products', 'inventory', '/products'],
      ['/stock-alerts', 'inventory', '/stock-alerts'],
      ['/imports', 'inventory', '/imports'],
      ['/orders', 'orders', '/orders'],
      ['/receivables', 'orders', '/receivables'],
      ['/purchases', 'purchase', '/purchases'],
      ['/suppliers', 'purchase', '/suppliers'],
      ['/repairs', 'service', '/repairs'],
      ['/technicians', 'service', '/technicians'],
      ['/margin', 'analytics', '/margin'],
    ]
    for (const [path, key, tabPath] of cases) {
      const item = navItemForPath(path)
      expect(item?.key).toBe(key)
      expect(item && activeTabPath(item, path)).toBe(tabPath)
    }
  })

  it('详情页高亮所属列表标签，但不显示分组标签栏', () => {
    const cases: [string, string, string][] = [
      ['/orders/123', 'orders', '/orders'],
      ['/repairs/45', 'service', '/repairs'],
      ['/customers/9', 'customers', '/customers'],
      ['/customers/reminders', 'customers', '/customers/reminders'],
      ['/customers/analysis', 'customers', '/customers/analysis'],
      ['/customers/levels', 'customers', '/customers/levels'],
    ]
    for (const [path, key, tabPath] of cases) {
      const item = navItemForPath(path)
      expect(item?.key).toBe(key)
      expect(item && activeTabPath(item, path)).toBe(tabPath)
    }
    const orders = take(visibleNavItems(false), 'orders')
    expect(tabForPath(orders, '/orders')).toBeDefined()
    expect(tabForPath(orders, '/orders/123')).toBeUndefined()
  })

  it('每个旧页面地址只出现在一个标签里', () => {
    const paths = NAV_ITEMS.flatMap((item) => item.tabs.map((tab) => tab.path))
    expect(paths).toEqual([
      '/products',
      '/stock-alerts',
      '/stock-logs',
      '/imports',
      '/orders',
      '/receivables',
      '/purchases',
      '/suppliers',
      '/repairs',
      '/technicians',
      '/customers',
      '/customers/reminders',
      '/customers/analysis',
      '/customers/levels',
      '/margin',
    ])
    expect(new Set(paths).size).toBe(paths.length)
    // 员工管理从侧栏移到右上角菜单，不再有标签地址。
    expect(paths).not.toContain('/staff')
    expect(NAV_ITEMS.some((item) => item.path === '/')).toBe(true)
  })
})
