import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

/** 전 화면 공통 푸터. 헤더보다 존재감을 낮게 둔다(Wireframe §1). */
function Footer() {
  const { t } = useTranslation()
  const links = [
    { to: '/about', label: t('nav.about') },
    { to: '/contact', label: t('nav.contact') },
    { to: '/terms', label: t('nav.terms') },
    { to: '/privacy', label: t('nav.privacy') },
  ]

  return (
    <footer className="relative z-[var(--z-content)] mt-auto flex flex-col gap-4 border-t border-line bg-bg px-6 py-5 text-xs text-text-soft md:flex-row md:items-center md:gap-6 md:px-12">
      <div className="text-text">Hidden Gyeongbuk</div>
      <div className="md:flex-1" />
      <nav className="flex flex-wrap gap-x-[18px] gap-y-2">
        {links.map((l) => (
          <Link key={l.to} to={l.to} className="text-accent-text hover:text-accent-hover">
            {l.label}
          </Link>
        ))}
      </nav>
      <div className="text-[9.5px]">© 2026 Hidden Gyeongbuk</div>
    </footer>
  )
}

export default Footer
