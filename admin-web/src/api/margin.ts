export type MarginPeriod = 'MONTH' | 'YEAR'
export type MarginScope = 'ALL' | 'INVOICED' | 'UNINVOICED'

export interface MarginQuery {
  period?: MarginPeriod
  key?: string
  scope?: MarginScope
  comparePeriod?: MarginPeriod
  compareKey?: string
}

export interface MarginTotals {
  periodKey: string
  from: string
  through: string
  available: boolean
  salesAmount: number
  salesTaxIncluded: number
  salesTaxExcluded: number
  receivedAmount: number
  unpaidAmount: number
  goodsCost?: number | null
  grossProfit?: number | null
  grossMargin?: string | null
  unknownSalesAmount: number
  unknownLineCount: number
  installIncome: number
  repairIncome: number
}

export interface MarginReport {
  periodType: MarginPeriod
  comparePeriodType: MarginPeriod
  current: MarginTotals
  previous: MarginTotals
  differences: Record<string, number>
}

export interface MarginProduct {
  skuId: number
  productName: string
  skuName: string
  quantity: number
  salesAmount: number
  costAmount?: number | null
  grossMargin?: string | null
  unknownLineCount: number
  stock: number
  turnoverDays?: number | null
  hot: boolean
  slow: boolean
}

export interface MarginTrendValues {
  salesAmount: number | null
  receivedAmount: number | null
  unpaidAmount: number | null
  grossProfit: number | null
}

export interface MarginTrendPoint {
  date: string
  actual?: MarginTrendValues | null
  average?: MarginTrendValues | null
}

export interface MarginHistory {
  months: MarginTrendPoint[]
  days: MarginTrendPoint[]
  monthlyAverage?: MarginTrendValues | null
}
