import { BrowserRouter, Routes, Route, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import PatternDefs from './components/PatternDefs'
import Home from './pages/Home'
import RecommendInput from './pages/RecommendInput'
import RecommendResults from './pages/RecommendResults'
import CourseDetail from './pages/CourseDetail'
import PlaceExplore from './pages/PlaceExplore'
import PlaceDetail from './pages/PlaceDetail'
import Placeholder from './pages/Placeholder'
import { useHubs } from './hooks/useHubs'
import { useLanguage } from './context/LanguageContext'

function HubPlaceholder() {
  const { t } = useTranslation()
  const { hubId } = useParams()
  const { hubs } = useHubs()
  const { lang } = useLanguage()
  const hub = hubs.find((h) => String(h.hubId) === hubId)
  const hubName = hub ? (lang === 'ko' ? hub.nameKo : hub.nameEn) : null
  return (
    <Placeholder
      title={hubName ? t('placeholder.hubTitle', { name: hubName }) : t('placeholder.hubTitleFallback')}
      cta={{ to: `/recommend?hub=${hubId}`, label: t('home.ctaRecommend') }}
    />
  )
}

function App() {
  return (
    <BrowserRouter>
      <PatternDefs />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/recommend" element={<RecommendInput />} />
        <Route path="/recommend/results" element={<RecommendResults />} />
        <Route path="/recommend/results/:rank" element={<CourseDetail />} />
        <Route path="/places" element={<PlaceExplore />} />
        <Route path="/places/:placeId" element={<PlaceDetail />} />
        <Route path="/hubs/:hubId" element={<HubPlaceholder />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
