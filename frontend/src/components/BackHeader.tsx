import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Container from './Container'
import KeyfretStrip from './KeyfretStrip'
import LanguageSwitch from './LanguageSwitch'

function BackHeader({
  backTo = '/',
  onBack,
  rightSlot,
}: {
  backTo?: string
  onBack?: () => void
  rightSlot?: ReactNode
}) {
  const { t } = useTranslation()
  return (
    <header className="sticky top-0 z-10 bg-bg-elevated">
      <Container className="flex items-center justify-between gap-4 py-4 md:py-5">
        {onBack ? (
          <button type="button" onClick={onBack} className="shrink-0 text-sm text-text hover:text-accent md:text-base">
            {t('common.back')}
          </button>
        ) : (
          <Link to={backTo} className="shrink-0 text-sm text-text hover:text-accent md:text-base">
            {t('common.back')}
          </Link>
        )}
        <div className="flex shrink-0 items-center gap-4">
          {rightSlot ? <span className="text-[13px] text-accent md:text-sm">{rightSlot}</span> : null}
          <LanguageSwitch />
        </div>
      </Container>
      <KeyfretStrip />
    </header>
  )
}

export default BackHeader
