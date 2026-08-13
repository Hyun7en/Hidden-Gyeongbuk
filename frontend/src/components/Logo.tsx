import { useTheme } from '../context/ThemeContext'

/**
 * 브랜드 로고. 세로 락업은 다크모드에서 흰색 버전으로 자동 교체된다 — 워드마크가 짙은
 * 먹색(#14312D)이라 어두운 배경에서 읽히지 않기 때문이다. 심볼 자체는 어느 테마에서도
 * 같은 브랜드 색을 쓴다.
 */
function Logo({ height = 40, className }: { height?: number; className?: string }) {
  const { theme } = useTheme()
  const file = theme === 'dark' ? 'logo-lockup-vertical-white.svg' : 'logo-lockup-vertical.svg'
  return (
    <img
      src={`/assets/${file}`}
      alt="Hidden Gyeongbuk"
      height={height}
      style={{ display: 'block', height }}
      className={className}
    />
  )
}

export default Logo
