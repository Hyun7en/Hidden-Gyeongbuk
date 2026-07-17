import { useLanguage, type Lang } from '../context/LanguageContext'

// 새 언어를 추가하려면 여기에 항목만 더하면 된다 — 단, 백엔드 LangFallback.normalize()가
// 아직 en/ko 두 값만 정규화하므로 실제 콘텐츠 번역까지 지원하려면 백엔드 작업이 먼저 필요하다.
const LANGUAGES: { code: Lang; label: string }[] = [
  { code: 'en', label: 'EN' },
  { code: 'ko', label: 'KO' },
]

function LanguageSwitch() {
  const { lang, setLang } = useLanguage()

  return (
    <div className="flex shrink-0 border border-line">
      {LANGUAGES.map(({ code, label }) => (
        <button
          key={code}
          type="button"
          aria-current={lang === code}
          onClick={() => setLang(code)}
          className={
            lang === code
              ? 'bg-accent px-[9px] py-[5px] text-[11px] font-bold text-bg-elevated md:px-3 md:py-1.5 md:text-xs'
              : 'px-[9px] py-[5px] text-[11px] font-bold text-text-soft md:px-3 md:py-1.5 md:text-xs'
          }
        >
          {label}
        </button>
      ))}
    </div>
  )
}

export default LanguageSwitch
