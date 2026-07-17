import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'
import ko from './ko.json'

const STORAGE_KEY = 'hg-lang'

function readStoredLang(): 'en' | 'ko' {
  return localStorage.getItem(STORAGE_KEY) === 'ko' ? 'ko' : 'en'
}

i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    ko: { translation: ko },
  },
  lng: readStoredLang(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

i18n.on('languageChanged', (lng) => {
  localStorage.setItem(STORAGE_KEY, lng)
})

export default i18n
