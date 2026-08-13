import type { ReactNode } from 'react'

/**
 * 선택 칩. 디자인 시스템 `Tag`(`hg-tag`)를 그대로 쓴다 — 스타일 정의는 `styles/ds.css`.
 *
 * prop 이름은 기존 호출부에 맞춰 `active`로 유지한다(디자인 시스템 원본은 `selected`).
 */
function Tag({
  active,
  size = 'desktop',
  disabled,
  onClick,
  children,
}: {
  active?: boolean
  size?: 'mobile' | 'desktop'
  disabled?: boolean
  onClick?: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-pressed={active}
      className={`hg-tag hg-tag-${size} ${active ? 'hg-tag-selected' : 'hg-tag-unselected'}`}
    >
      {children}
    </button>
  )
}

export default Tag
