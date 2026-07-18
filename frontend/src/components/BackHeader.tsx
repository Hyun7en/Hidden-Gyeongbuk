import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Container from './Container'
import KeyfretStrip from './KeyfretStrip'
import LanguageSwitch from './LanguageSwitch'

function BackHeader({ backTo = '/', onBack }: { backTo?: string; onBack?: () => void }) {
  const { t } = useTranslation()
  return (
    <header className="sticky top-0 z-10 bg-bg-elevated">
      <Container className="flex items-center justify-between gap-4 py-4 md:py-5">
        <div className="flex min-w-0 items-center gap-4">
          <Link to="/" className="flex shrink-0 items-center gap-2" aria-label={t('common.home')}>
            <span className="h-4 w-4 shrink-0 bg-seal md:h-5 md:w-5" />
            <span className="hidden text-[15px] font-extrabold tracking-[-0.01em] sm:inline md:text-lg">
              Hidden Gyeongbuk
            </span>
          </Link>
          {onBack ? (
            <button type="button" onClick={onBack} className="shrink-0 text-sm text-text hover:text-accent md:text-base">
              {t('common.back')}
            </button>
          ) : (
            <Link to={backTo} className="shrink-0 text-sm text-text hover:text-accent md:text-base">
              {t('common.back')}
            </Link>
          )}
        </div>
        <LanguageSwitch />
      </Container>
      <KeyfretStrip />
    </header>
  )
}

export default BackHeader
