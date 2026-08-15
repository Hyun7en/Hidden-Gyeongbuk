import { useTranslation } from 'react-i18next'
import type { CourseResponse, HubResponse } from '../api/types'
import KakaoMap, { type MapMarker } from './KakaoMap'
import ShareButton from './ShareButton'
import { googleDirectionsUrl } from '../lib/directions'

function DirectionsIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="currentColor" aria-hidden="true">
      <path d="M3 11l18-8-8 18-2-8-8-2z" />
    </svg>
  )
}

/**
 * 코스 하나의 일정 본문. S4의 모바일 아코디언 패널과 데스크톱 상세 영역이 <b>같은 것</b>을
 * 보여줘야 해서 컴포넌트로 뺐다 — 두 벌로 두면 한쪽만 고치는 사고가 난다.
 *
 * <p>순위·점수는 여기 없다. 사용자가 고를 때 쓰는 정보가 아니라 내부 정렬 기준이라
 * 노출하지 않기로 했다(화면 설계서 S4).
 */
function CourseItinerary({
  course,
  hub,
  tripLabel,
}: {
  course: CourseResponse
  hub?: HubResponse
  tripLabel: string
}) {
  const { t } = useTranslation()
  // 지도는 하루가 아니라 여정 전체를 한 줄로 그린다 — CoursePlaceResponse.order는 날마다 1로
  // 돌아가므로 쓸 수 없어 통짜 순번을 다시 매긴다. 거점을 맨 앞에 둬야 선이 출발지에서
  // 시작한다(Hub-and-Spoke). 좌표 없는 장소는 찍을 수 없어 뺀다(수집 데이터에 결측이 있다).
  const routeMarkers: MapMarker[] = [
    ...(hub ? [{ lat: hub.lat, lng: hub.lng, label: hub.name, isHub: true }] : []),
    ...course.days
      .flatMap((d) => d.places)
      .flatMap((p, i) =>
        p.lat != null && p.lng != null ? [{ lat: p.lat, lng: p.lng, label: p.title ?? '', order: i + 1 }] : [],
      ),
  ]

  return (
    <div className="md:grid md:grid-cols-[1fr_minmax(0,320px)] md:gap-8">
      <div className="space-y-6">
        {course.days.map((day) => (
          <div key={day.day}>
            <h3 className="mb-3 text-[15px] font-bold md:text-base">{t('courseDetail.day', { day: day.day })}</h3>
            <ol className="space-y-2">
              {day.places.map((place) => (
                <li
                  key={place.order}
                  className="flex items-center gap-3 border border-line bg-bg-elevated px-3 py-2.5 md:px-4 md:py-3"
                >
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center bg-accent text-xs font-bold text-bg-elevated">
                    {place.order}
                  </span>
                  <span className="text-sm md:text-[15px]">{place.title}</span>
                  <span className="ml-auto text-[11px] uppercase text-text-soft md:text-xs">
                    {t(`options.interest.${place.placeType}`)}
                  </span>
                  <a
                    href={place.lat != null && place.lng != null ? googleDirectionsUrl(place.lat, place.lng) : undefined}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label={t('courseDetail.directions')}
                    title={t('courseDetail.directions')}
                    className="shrink-0 text-accent"
                  >
                    <DirectionsIcon />
                  </a>
                </li>
              ))}
            </ol>
            {day.lodging ? (
              <p className="mt-2 text-[13px] text-text-soft md:text-sm">
                {t('courseDetail.lodging', { title: day.lodging.title })}
              </p>
            ) : null}
          </div>
        ))}
      </div>

      <div className="relative mt-6 h-[180px] border border-line bg-bg-elevated md:mt-0 md:h-[260px] md:self-start">
        <ShareButton title={tripLabel} description={courseFacts(course, tripLabel)} />
        <KakaoMap className="h-full w-full" markers={routeMarkers} drawRoute />
      </div>
    </div>
  )
}

/** 공유 메시지에 실릴 한 줄 요약. */
function courseFacts(course: CourseResponse, tripLabel: string): string {
  const placeCount = course.days.reduce((sum, d) => sum + d.places.length, 0)
  return `${tripLabel} · ${placeCount} places · ${course.totalDistanceKm.toFixed(1)}km`
}

export default CourseItinerary
