import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import FiberBackground from '../components/FiberBackground'

function Placeholder({ title, cta }: { title: string; cta?: { to: string; label: string } }) {
  const { t } = useTranslation()
  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10 flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">{t('placeholder.comingSoon')}</p>
        <h1 className="text-xl font-bold md:text-3xl">{title}</h1>
        <p className="text-sm text-text-soft md:text-base">{t('placeholder.body')}</p>
        {cta ? (
          <Link
            to={cta.to}
            className="mt-2 flex h-12 items-center justify-center bg-seal px-[22px] text-[15px] font-bold text-[#f5f1e7] md:h-14 md:px-7 md:text-base"
          >
            {cta.label}
          </Link>
        ) : null}
        <Link
          to="/"
          className="flex h-12 items-center justify-center border border-line px-[22px] text-[15px] font-bold text-text md:h-14 md:px-7 md:text-base"
        >
          {t('placeholder.home')}
        </Link>
      </div>
    </div>
  )
}

export default Placeholder
