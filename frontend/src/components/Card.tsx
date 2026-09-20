import { useState } from 'react'
import { Link } from 'react-router-dom'

/**
 * 디자인 시스템 `PlaceCard`(`hg-card`). 장소와 지역이 같은 카드 구조를 쓴다 —
 * 홈 지역 그리드, 지역 소개의 대표 관광지, 장소 목록, 주변 장소가 전부 이것이다(Wireframe §4).
 *
 * 원본은 `<button>`이지만 여기서는 `<Link>`로 만든다. 자체 뒤로가기를 없앴으므로(ADR-022)
 * 브라우저 뒤로가기가 유일한 복귀 경로인데, 그게 성립하려면 이동이 실제 링크여야 한다.
 */
function Card({
  to,
  title,
  subtitle,
  badge,
  imageUrl,
  size = 'desktop',
}: {
  to: string
  title: string
  subtitle?: string | null
  badge?: string | null
  imageUrl?: string | null
  size?: 'mobile' | 'desktop'
}) {
  /**
   * 원본 CDN이 사진을 내리면 URL은 남고 로딩만 실패한다. 배경 이미지는 실패를 알려주지
   * 않으므로 같은 URL을 `<img>`로 한 번 더 걸어 `onError`를 받는다 — 브라우저 캐시를
   * 공유하니 요청은 늘지 않는다. 실패하면 사진이 아예 없는 것과 같은 자리로 떨어뜨린다.
   */
  const [failed, setFailed] = useState(false)
  const showImage = !!imageUrl && !failed

  return (
    <Link to={to} className={`hg-card hg-card-${size}`}>
      <div className="hg-inner">
        <div
          className={`hg-img${showImage ? '' : ' hg-img-empty'}`}
          style={showImage ? { backgroundImage: `url(${imageUrl})` } : undefined}
          role={showImage ? 'img' : undefined}
          aria-label={showImage ? title : undefined}
        >
          {showImage ? (
            <img src={imageUrl} alt="" aria-hidden="true" className="hg-img-probe" onError={() => setFailed(true)} />
          ) : (
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" aria-hidden="true">
              <rect x="3" y="5" width="18" height="14" rx="1" />
              <path d="M3 16l5-5 4 4 3-3 6 6" strokeLinejoin="round" />
              <circle cx="8.5" cy="9.5" r="1.2" />
            </svg>
          )}
        </div>
        <div className="hg-body">
          <p className="hg-title">{title}</p>
          {subtitle && <p className="hg-addr">{subtitle}</p>}
          {badge && (
            <div className="hg-tags">
              <span>{badge}</span>
            </div>
          )}
        </div>
      </div>
    </Link>
  )
}

export default Card
