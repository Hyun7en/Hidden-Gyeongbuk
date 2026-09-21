import { createContext, useContext } from 'react'

export type Theme = 'light' | 'dark'

export const STORAGE_KEY = 'hg-theme'

export function storedTheme(): Theme | null {
  if (typeof localStorage === 'undefined') return null
  const v = localStorage.getItem(STORAGE_KEY)
  return v === 'dark' || v === 'light' ? v : null
}

export function systemTheme(): Theme {
  if (typeof matchMedia !== 'function') return 'light'
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export interface ThemeValue {
  theme: Theme
  toggle: () => void
}

export const ThemeCtx = createContext<ThemeValue | null>(null)

/**
 * 다크모드. **초기값은 OS 설정을 따르고, 사용자가 토글하면 그 선택이 우선한다**(Wireframe §1).
 *
 * **상태는 {@link ThemeProvider} 하나에만 둔다.** 소비자마다 useState를 따로 잡으면 토글한
 * 컴포넌트의 state만 바뀌고 나머지는 옛 값에 머문다 — CSS는 `dataset.theme`을 보고 전부 넘어가는데
 * 테마 값을 읽어 에셋을 고르는 쪽(`Logo`)만 뒤처져, 어느 방향으로 토글하든 로고 워드마크가
 * 배경에 묻혔다(2026-09-21 실측).
 */
export function useTheme(): ThemeValue {
  const v = useContext(ThemeCtx)
  if (!v) throw new Error('useTheme는 ThemeProvider 안에서만 쓴다')
  return v
}
