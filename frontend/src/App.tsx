import { BrowserRouter, Routes, Route } from 'react-router-dom'
import PatternDefs from './components/PatternDefs'
import Home from './pages/Home'
import RecommendInput from './pages/RecommendInput'
import RecommendResults from './pages/RecommendResults'
import CourseDetail from './pages/CourseDetail'
import PlaceExplore from './pages/PlaceExplore'
import PlaceDetail from './pages/PlaceDetail'
import HubDetail from './pages/HubDetail'

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
        <Route path="/hubs/:hubId" element={<HubDetail />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
