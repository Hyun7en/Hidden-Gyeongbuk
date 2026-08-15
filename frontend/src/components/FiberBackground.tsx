/**
 * 한지 결 배경. 디자인 시스템이 남긴 전통 표현 <b>2채널 중 하나</b>다 — 이중선 프레임·코너마크를
 * 전부 걷어내고 타이포(세리프)와 이 텍스처만 남겼다(Design System v3).
 *
 * <p>`fixed`로 뷰포트에 고정한다. 문서 흐름을 따라가면 긴 페이지에서 텍스처가 스크롤과 함께
 * 밀려 올라가 아래쪽이 맨 배경으로 드러난다.
 *
 * <p>다크 테마에서는 `--texture-hanji-opacity`가 0이라 렌더링되지 않는다 — 어두운 배경에서
 * 종이 결은 얼룩으로 보인다.
 */
function FiberBackground() {
  return <div className="hanji-texture" aria-hidden="true" />
}

export default FiberBackground
