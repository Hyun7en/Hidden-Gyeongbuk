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

/**
 * `external`이면 라우터가 아니라 실제 `<a>`로 낸다. 카카오맵·구글지도 딥링크처럼 앱 밖으로
 * 나가는 주소를 `Link`에 넣으면 라우터가 자기 경로로 착각해 빈 화면이 뜬다.
 */
export function ButtonLink({
  children,
  size = 'desktop',
  to,
  external,
  className,
}: Props & { to: string; external?: boolean }) {
  const cls = `hg-cta hg-btn-${size} inline-flex items-center justify-center ${className ?? ''}`
  if (external) {
    return (
      <a href={to} target="_blank" rel="noopener noreferrer" className={cls}>
        {children}
      </a>
    )
  }
  return (
    <Link to={to} className={cls}>
      {children}
    </Link>
  )
}

export default Button
