import { Outlet } from 'react-router-dom'
import Header from './Header'
import Footer from './Footer'
import FiberBackground from './FiberBackground'

/**
 * 전 화면 공통 셸. 헤더·푸터가 라우트 바깥에 있어 화면을 옮겨도 다시 마운트되지 않는다 —
 * 언어·다크모드 선택이 화면 이동에도 유지되어야 하기 때문이다(Prototype 검증 항목).
 *
 * `min-h-screen` + 푸터의 `mt-auto`로 내용이 짧은 화면에서도 푸터가 바닥에 붙는다.
 */
function Layout() {
  return (
    <div className="relative flex min-h-screen flex-col bg-bg">
      <FiberBackground />
      <Header />
      <main className="relative z-[var(--z-content)] flex flex-1 flex-col">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}

export default Layout
