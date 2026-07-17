import { BrowserRouter, Routes, Route, useParams } from 'react-router-dom'
import PatternDefs from './components/PatternDefs'
import Home from './pages/Home'
import RecommendInput from './pages/RecommendInput'
import RecommendResults from './pages/RecommendResults'
import CourseDetail from './pages/CourseDetail'
import PlaceExplore from './pages/PlaceExplore'
import PlaceDetail from './pages/PlaceDetail'
import Placeholder from './pages/Placeholder'
import { useHubs } from './hooks/useHubs'

function HubPlaceholder() {
  const { hubId } = useParams()
  const { hubs } = useHubs()
  const hub = hubs.find((h) => String(h.hubId) === hubId)
  return (
    <Placeholder
      title={hub ? `${hub.nameEn} Hub` : 'Hub'}
      cta={{ to: `/recommend?hub=${hubId}`, label: 'Get a course recommendation →' }}
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
