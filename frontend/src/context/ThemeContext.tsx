import { useCallback, useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'hg-theme'

/**
 * 다크모드. **초기값은 OS 설정을 따르고, 사용자가 토글하면 그 선택이 우선한다**(Wireframe §1).
 *
 * 저장된 선택이 있으면 `documentElement.dataset.theme`에 박고, 없으면 속성을 비워
 * `prefers-color-scheme`이 그대로 먹게 둔다 — index.css가 두 경로를 모두 정의하고 있으므로
 * 여기서 OS 값을 읽어 되쓸 필요가 없다.
 */
function storedTheme(): Theme | null {
  if (typeof localStorage === 'undefined') return null
  const v = localStorage.getItem(STORAGE_KEY)
  return v === 'dark' || v === 'light' ? v : null
}

function systemTheme(): Theme {
  if (typeof matchMedia !== 'function') return 'light'
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function useTheme() {
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

  return { theme: resolved, toggle }
}
