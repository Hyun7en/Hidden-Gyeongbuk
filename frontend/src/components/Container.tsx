import type { ReactNode } from 'react'

/**
 * 페이지 본문 컨테이너. 폭은 프로토타입과 같은 1180px(`--container-lg`)이다.
 *
 * <p>좌우 여백은 프로토타입과 한 곳만 다르다. 프로토타입은 데스크톱에서 패딩이 0인데, 그건
 * 미리보기 폭이 항상 1400px로 고정돼 있어 여백을 신경 쓸 필요가 없었기 때문이다. 실제
 * 브라우저에서는 창 폭이 1180px 근처일 때 글자가 화면 가장자리에 딱 붙는다. 그래서 창이
 * 충분히 넓을 때(xl, 1280px~)만 0으로 두고, 그보다 좁으면 여백을 남긴다 — 넓은 화면에서는
 * 프로토타입과 정확히 같은 1180px이 된다.
 */
function Container({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`mx-auto w-full max-w-[var(--container-lg)] px-4 md:px-6 xl:px-0 ${className}`}>{children}</div>
  )
}

export default Container
