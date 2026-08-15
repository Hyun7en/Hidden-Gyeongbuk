import type { TFunction } from 'i18next'

/**
 * 여행 기간 계산. `duration` enum(당일/1박2일/2박3일)이 날짜 범위로 바뀌면서 생긴 유틸이다(ADR-020).
 * 박 수·일 수는 저장하지 않고 항상 날짜에서 파생한다 — 백엔드도 같은 규칙이라 둘이 어긋날 수 없다.
 */

/** 백엔드 `course.constraints.max-nights`와 같아야 한다. 넘기면 400이 돌아온다. */
export const MAX_NIGHTS = 14

/**
 * 로컬 시간대 기준 `YYYY-MM-DD`.
 *
 * `toISOString()`을 쓰면 안 된다 — UTC로 변환하므로 KST에서는 자정 이후 날짜가 하루 앞당겨진다.
 * 사용자가 5월 1일을 골랐는데 4월 30일이 서버로 가는 종류의 버그다.
 */
export function toIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** `YYYY-MM-DD` → 로컬 자정. 형식이 아니면 null. */
export function parseIsoDate(value: string | null | undefined): Date | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const [y, m, d] = value.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 오늘 자정. 비교의 기준점이라 시/분/초를 떨어낸다. */
export function today(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

export function nightsBetween(start: Date, end: Date): number {
  return Math.round((end.getTime() - start.getTime()) / 86_400_000)
}

export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

export function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

/** "당일치기" / "2박 3일" — 목록·카드에서 기간을 한 덩어리로 보여줄 때 쓴다. */
export function tripLengthLabel(t: TFunction, startIso: string, endIso: string): string {
  const start = parseIsoDate(startIso)
  const end = parseIsoDate(endIso)
  if (!start || !end) return ''
  const nights = nightsBetween(start, end)
  if (nights <= 0) return t('trip.dayTrip')
  return t('trip.nights', { count: nights, days: nights + 1 })
}

/** 화면에 보여줄 날짜 범위. 같은 해면 연도를 한 번만 쓴다. */
export function formatDateRange(locale: string, startIso: string, endIso: string): string {
  const start = parseIsoDate(startIso)
  const end = parseIsoDate(endIso)
  if (!start || !end) return ''
  const full = new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric' })
  if (isSameDay(start, end)) return full.format(start)
  const short = new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric' })
  return `${short.format(start)} – ${full.format(end)}`
}

/** 과거 날짜로 들어온 공유 링크인지. 막지는 않고 안내만 띄운다(ADR-020 결과 ④). */
export function isPastTrip(startIso: string): boolean {
  const start = parseIsoDate(startIso)
  return start !== null && start.getTime() < today().getTime()
}
