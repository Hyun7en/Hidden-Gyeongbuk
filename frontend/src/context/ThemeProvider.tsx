import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { STORAGE_KEY, ThemeCtx, storedTheme, systemTheme, type Theme } from './ThemeContext'

/**
 * 테마 상태의 유일한 보유자. 저장된 선택이 있으면 `documentElement.dataset.theme`에 박고,
 * 없으면 속성을 비워 `prefers-color-scheme`이 그대로 먹게 둔다 — index.css가 두 경로를 모두
 * 정의하고 있으므로 여기서 OS 값을 읽어 되쓸 필요가 없다.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [pinned, setPinned] = useState<Theme | null>(storedTheme)
  const [system, setSystem] = useState<Theme>(systemTheme)

  // 사용자가 고르지 않은 동안에는 OS 변경을 따라간다.
  useEffect(() => {
    if (typeof matchMedia !== 'function') return
    const mq = matchMedia('(prefers-color-scheme: dark)')
    const sync = () => setSystem(mq.matches ? 'dark' : 'light')
    mq.addEventListener('change', sync)
    return () => mq.removeEventListener('change', sync)
  }, [])

  const resolved: Theme = pinned ?? system

  useEffect(() => {
    const root = document.documentElement
    if (pinned) {
      root.dataset.theme = pinned
      localStorage.setItem(STORAGE_KEY, pinned)
    } else {
      delete root.dataset.theme
      localStorage.removeItem(STORAGE_KEY)
    }
  }, [pinned])

  const toggle = useCallback(() => {
    setPinned((prev) => ((prev ?? systemTheme()) === 'dark' ? 'light' : 'dark'))
  }, [])

  return <ThemeCtx.Provider value={{ theme: resolved, toggle }}>{children}</ThemeCtx.Provider>
}
