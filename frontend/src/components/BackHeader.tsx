import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import Container from './Container'
import KeyfretStrip from './KeyfretStrip'

function BackHeader({
  title,
  backTo = '/',
  onBack,
  rightSlot,
}: {
  title: string
  backTo?: string
  onBack?: () => void
  rightSlot?: ReactNode
}) {
  return (
    <header className="sticky top-0 z-10 bg-bg-elevated">
      <Container className="flex items-center gap-4 py-4 md:py-5">
        {onBack ? (
          <button type="button" onClick={onBack} className="shrink-0 text-sm text-text hover:text-accent md:text-base">
            ← Back
          </button>
        ) : (
          <Link to={backTo} className="shrink-0 text-sm text-text hover:text-accent md:text-base">
            ← Back
          </Link>
        )}
        <span className="flex-1 truncate text-[15px] font-bold md:text-lg">{title}</span>
        {rightSlot ? <span className="shrink-0 text-[13px] text-accent md:text-sm">{rightSlot}</span> : null}
      </Container>
      <KeyfretStrip />
    </header>
  )
}

export default BackHeader
