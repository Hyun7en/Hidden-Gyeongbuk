import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { CourseResponse, HubResponse } from '../api/types'
import KakaoMap, { type MapMarker } from './KakaoMap'

function CourseCard({
  course,
  durationLabel,
  hub,
  search,
}: {
  course: CourseResponse
  durationLabel: string
  hub?: HubResponse
  // 추천 조건 쿼리스트링(?hub=..&duration=..). 상세 URL에 그대로 실어야 그 주소만으로
  // 코스를 재계산할 수 있어 공유·새로고침이 동작한다(FR-C-06).
  search: string
}) {
  const { t } = useTranslation()
  const route = course.days
    .flatMap((d) => d.places)
    .map((p) => (p.title ?? '').split(' ')[0])
    .filter((v, i, arr) => v !== '' && arr.indexOf(v) === i)
    .slice(0, 3)
    .join(' · ')
  const placeCount = course.days.reduce((sum, d) => sum + d.places.length, 0)
  // 좌표 없는 장소는 지도에 찍을 수 없어 제외한다(수집 데이터에 결측이 있다).
  const routeMarkers: MapMarker[] = [
    ...(hub ? [{ lat: hub.lat, lng: hub.lng, label: hub.name, isHub: true }] : []),
    ...course.days
      .flatMap((d) => d.places)
      .flatMap((p, i) =>
        p.lat != null && p.lng != null ? [{ lat: p.lat, lng: p.lng, label: p.title ?? '', order: i + 1 }] : [],
      ),
  ]

  return (
    <Link
      to={{ pathname: `/recommend/results/${course.rank}`, search }}
      state={{ course, durationLabel }}
      className="block border border-line bg-bg-elevated p-4 md:p-6"
    >
      <div className="mb-2.5 flex items-start justify-between">
        <span className="text-[11px] font-bold uppercase tracking-[0.05em] text-text-soft md:text-xs">
          {t('common.courseRank', { rank: course.rank })}
        </span>
        <span className="inline-flex items-baseline gap-[3px] bg-seal-tint px-[9px] py-[3px] text-sm font-bold tabular-nums text-seal md:text-base">
          {course.score.toFixed(1)}
          <small className="text-[10px] font-semibold">pt</small>
        </span>
      </div>
      <p className="mb-1 text-base font-bold md:text-lg">{route}</p>
      <p className="mb-3 text-xs text-text-soft md:text-sm">
        {durationLabel} · {placeCount} places · {course.totalDistanceKm.toFixed(1)}km
      </p>
      <KakaoMap className="h-[72px] border border-line md:h-[110px]" markers={routeMarkers} drawRoute />
    </Link>
  )
}

export default CourseCard
