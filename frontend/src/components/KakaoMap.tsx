import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { loadKakaoMaps } from '../lib/kakaoMaps'

// Route line color is a literal hex, not a CSS variable — Kakao's Polyline is
// canvas-rendered by the SDK, not a DOM element, so it can't read var(--seal).
// Kakao's own basemap tiles don't follow the app's light/dark theme either, so
// a single fixed color (the light-mode seal accent) is the right call here.
const ROUTE_COLOR = '#b23a2e'

export type MapMarker = {
  lat: number
  lng: number
  label: string
  order?: number
  isHub?: boolean
  isRegion?: boolean
}

function KakaoMap({ markers, drawRoute, className }: { markers: MapMarker[]; drawRoute?: boolean; className?: string }) {
  const { t } = useTranslation()
  const containerRef = useRef<HTMLDivElement>(null)
  const [error, setError] = useState(false)

  // 호출부가 markers 배열을 렌더마다 새로 만들기 때문에 배열 참조를 그대로 의존성에 넣으면
  // 부모가 리렌더링될 때마다(언어 전환, 허브 목록 도착 등) 지도가 통째로 재생성되며 깜빡인다.
  // 내용 기반 키로 비교해 실제 마커가 달라졌을 때만 다시 그린다.
  const markersKey = JSON.stringify(markers)
  const markersRef = useRef(markers)
  markersRef.current = markers

  useEffect(() => {
    const markers = markersRef.current
    if (!containerRef.current || markers.length === 0) return
    let cancelled = false
    setError(false)

    loadKakaoMaps()
      .then((kakao) => {
        if (cancelled || !containerRef.current) return

        const map = new kakao.maps.Map(containerRef.current, {
          center: new kakao.maps.LatLng(markers[0].lat, markers[0].lng),
          level: 6,
        })

        const bounds = new kakao.maps.LatLngBounds()
        markers.forEach((m) => {
          const position = new kakao.maps.LatLng(m.lat, m.lng)
          bounds.extend(position)

          const content = document.createElement('div')
          content.title = m.label
          content.className = m.isHub
            ? 'flex h-7 w-7 items-center justify-center rounded-full border-2 border-bg-elevated bg-accent shadow-sm'
            : m.order
              ? 'flex h-6 w-6 items-center justify-center rounded-full border border-line bg-accent text-[11px] font-bold text-bg-elevated shadow-sm'
              : m.isRegion
                ? 'h-3 w-3 rounded-full border-2 border-bg-elevated bg-accent shadow-sm'
                : 'h-3.5 w-3.5 rounded-full border-2 border-bg-elevated bg-fiber shadow-sm'
          if (m.order) content.textContent = String(m.order)

          new kakao.maps.CustomOverlay({ position, content, yAnchor: 0.5 }).setMap(map)
        })

        if (markers.length > 1) {
          map.setBounds(bounds)
          if (drawRoute) {
            new kakao.maps.Polyline({
              path: markers.map((m) => new kakao.maps.LatLng(m.lat, m.lng)),
              strokeWeight: 3,
              strokeColor: ROUTE_COLOR,
              strokeOpacity: 0.8,
              strokeStyle: 'solid',
            }).setMap(map)
          }
        }
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [markersKey, drawRoute])

  if (markers.length === 0 || error) {
    return (
      <div className={className}>
        <div className="flex h-full items-center justify-center text-[11px] text-text-soft">
          {error ? t('common.mapLoadFailed') : t('common.mapNoLocation')}
        </div>
      </div>
    )
  }

  return <div ref={containerRef} className={className} />
}

export default KakaoMap
