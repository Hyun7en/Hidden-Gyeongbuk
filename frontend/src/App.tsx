import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Home from './pages/Home'
import RecommendInput from './pages/RecommendInput'
import RecommendResults from './pages/RecommendResults'
import PlaceExplore from './pages/PlaceExplore'
import PlaceDetail from './pages/PlaceDetail'
import RegionDetail from './pages/RegionDetail'
import About from './pages/About'
import Contact from './pages/Contact'
import LegalPage from './pages/LegalPage'

/**
 * 모든 화면이 {@link Layout}(헤더·푸터 공통 셸) 아래에 놓인다. 헤더가 라우트 바깥이라
 * 화면을 옮겨도 언어·다크모드 선택이 유지된다.
 */
function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Home />} />
          <Route path="/recommend" element={<RecommendInput />} />
          <Route path="/recommend/results" element={<RecommendResults />} />
          {/* 결과와 상세가 한 화면이라 :rank는 "이 코스를 펼친 상태"를 뜻한다(화면 설계서 S4). */}
          <Route path="/recommend/results/:rank" element={<RecommendResults />} />
          <Route path="/places" element={<PlaceExplore />} />
          <Route path="/places/:placeId" element={<PlaceDetail />} />
          <Route path="/regions/:regionId" element={<RegionDetail />} />
          <Route path="/about" element={<About />} />
          <Route path="/contact" element={<Contact />} />
          <Route path="/terms" element={<LegalPage kind="terms" />} />
          <Route path="/privacy" element={<LegalPage kind="privacy" />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
