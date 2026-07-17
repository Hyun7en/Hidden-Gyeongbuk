import { useTranslation } from 'react-i18next'

export type Lang = 'en' | 'ko'

interface LanguageValue {
  lang: Lang
  setLang: (lang: Lang) => void
}

/** i18next가 언어의 단일 소스다 — API 호출에 쓰는 lang 값과 화면 문구 언어가 항상 같은 값이어야 하므로 분리하지 않는다. */
export function useLanguage(): LanguageValue {
  const { i18n } = useTranslation()
  const lang: Lang = i18n.language === 'ko' ? 'ko' : 'en'
  return { lang, setLang: (next) => void i18n.changeLanguage(next) }
}
