import type { ReactNode } from 'react'

function Tag({ active, onClick, children }: { active?: boolean; onClick?: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`h-[30px] border px-3 text-[13px] font-medium md:h-9 md:px-4 md:text-sm ${
        active ? 'border-accent bg-accent text-bg-elevated' : 'border-line text-text'
      }`}
    >
      {children}
    </button>
  )
}

export default Tag
