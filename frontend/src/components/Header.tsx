import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Logo from './Logo'
import { useLanguage, type Lang } from '../context/LanguageContext'
import { useTheme } from '../context/ThemeContext'

const LANGUAGES: { code: Lang; label: string }[] = [
  { code: 'ko', label: '한국어' },
  { code: 'en', label: 'English' },
]

/**
 * 전 화면 공통 헤더 — 로고 + 언어 선택기 + 다크모드 토글.
 *
 * <b>뒤로가기 버튼은 어느 화면에도 없다</b>(ADR-022). PWA를 쓰지 않는 일반 반응형 웹이라
 * 브라우저 뒤로가기가 항상 존재하고, 로고가 이미 "홈으로"를 담당해 자체 버튼은 중복이다.
 * 그래서 브라우저 뒤로가기가 <b>유일한 복귀 경로</b>이며, 라우팅을 건드릴 때마다 확인해야 한다.
 */
function Header() {
  const { t } = useTranslation()
  const { lang, setLang } = useLanguage()
  const { theme, toggle } = useTheme()

  return (
    <header className="hg-header">
      <div className="hg-bar mx-auto max-w-[var(--container-lg)]">
        <Link to="/" className="hg-brand" aria-label="Hidden Gyeongbuk">
          <Logo height={40} />
        </Link>
        <div className="hg-actions">
          <select
            className="hg-lang"
            value={lang}
            aria-label="Language"
            onChange={(e) => setLang(e.target.value as Lang)}
          >
            {LANGUAGES.map((l) => (
              <option key={l.code} value={l.code}>
                {l.label}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="hg-theme-toggle"
            aria-label={theme === 'dark' ? t('common.a11yToLight') : t('common.a11yToDark')}
            aria-pressed={theme === 'dark'}
            onClick={toggle}
          >
            ◐
          </button>
        </div>
      </div>
    </header>
  )
}

export default Header
