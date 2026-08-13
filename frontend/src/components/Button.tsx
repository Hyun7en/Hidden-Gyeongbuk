import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/**
 * 주요 액션 버튼. 디자인 시스템 `Button`(`hg-cta`)을 그대로 쓴다 — 정의는 `styles/ds.css`.
 *
 * `to`를 주면 링크로, 아니면 버튼으로 렌더한다. 히어로 CTA처럼 화면을 옮기는 버튼은
 * 실제 링크여야 브라우저 뒤로가기가 성립한다(ADR-022에서 자체 뒤로가기를 없앴으므로).
 */
type Props = {
  children: ReactNode
  size?: 'mobile' | 'desktop'
  disabled?: boolean
  className?: string
}

export function Button({ children, size = 'desktop', disabled, onClick, className }: Props & { onClick?: () => void }) {
  return (
    <button type="button" className={`hg-cta hg-btn-${size} ${className ?? ''}`} disabled={disabled} onClick={onClick}>
      {children}
    </button>
  )
}

export function ButtonLink({ children, size = 'desktop', to, className }: Props & { to: string }) {
  return (
    <Link to={to} className={`hg-cta hg-btn-${size} inline-flex items-center justify-center ${className ?? ''}`}>
      {children}
    </Link>
  )
}

export default Button
