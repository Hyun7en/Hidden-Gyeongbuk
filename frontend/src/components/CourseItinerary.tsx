import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { CourseResponse, HubResponse } from '../api/types'
import KakaoMap, { type MapMarker } from './KakaoMap'
import ShareButton from './ShareButton'
import { ButtonLink } from './Button'
import { googleRouteUrl } from '../lib/directions'

/**
 * 코스 하나의 일정 본문. S4의 모바일 아코디언 패널과 데스크톱 우측 영역이 <b>같은 것</b>을
 * 보여줘야 해서 컴포넌트로 뺐다 — 두 벌로 두면 한쪽만 고치는 사고가 난다.
 *
 * <p>일자별 장소는 번호 박스가 아니라 <b>이름을 가운뎃점으로 이은 한 줄</b>이다(프로토타입 S4).
 * 코스는 훑어보고 고르는 대상이라, 카드 목록으로 쌓으면 세 코스를 비교하려고 계속 스크롤하게 된다.
 *
 * <p>순위·점수는 여기 없다. 고를 때 쓰는 정보가 아니라 내부 정렬 기준이라 노출하지 않는다
 * (화면 설계서 S4).
 */
function CourseItinerary({
  course,
  hub,
  title,
  compact = false,
}: {
  course: CourseResponse
  hub?: HubResponse
  title?: string
  /** 모바일 아코디언 패널. 지도와 글자를 한 단계씩 작게 쓴다. */
  compact?: boolean
}) {
  const { t } = useTranslation()

  // 지도는 하루가 아니라 여정 전체를 한 줄로 그린다 — CoursePlaceResponse.order는 날마다 1로
  // 돌아가므로 쓸 수 없어 통짜 순번을 다시 매긴다. 거점을 맨 앞에 둬야 선이 출발지에서
  // 시작한다(Hub-and-Spoke). 좌표 없는 장소는 찍을 수 없어 뺀다(수집 데이터에 결측이 있다).
  const located = course.days
    .flatMap((d) => d.places)
    .flatMap((p) => (p.lat != null && p.lng != null ? [{ lat: p.lat, lng: p.lng, title: p.title ?? '' }] : []))
  const routeMarkers: MapMarker[] = [
    ...(hub ? [{ lat: hub.lat, lng: hub.lng, label: hub.name, isHub: true }] : []),
    ...located.map((p, i) => ({ lat: p.lat, lng: p.lng, label: p.title, order: i + 1 })),
  ]
  const routeUrl = googleRouteUrl(located)
  const lodging = course.days.flatMap((d) => (d.lodging ? [d.lodging.title ?? ''] : []))

  return (
    <div>
      <div className={`relative ${compact ? 'h-[180px]' : 'h-[340px]'} border border-line bg-bg-elevated`}>
        <ShareButton title={title ?? t('recommendResults.title')} description={factsOf(course)} />
        <KakaoMap className="h-full w-full" markers={routeMarkers} drawRoute />
      </div>

      <div className={compact ? 'mt-3' : 'mt-5'}>
        {title && !compact ? (
          <p className="font-[family-name:var(--font-serif-heading)] text-lg font-semibold">{title}</p>
        ) : null}

        {course.days.map((day) => (
          <div key={day.day} className={compact ? 'mt-3 first:mt-0' : 'mt-3.5'}>
            <p className={`font-semibold ${compact ? 'text-xs' : 'text-[13px]'}`}>
              {t('courseDetail.day', { day: day.day })}
            </p>
            <p className={`mt-1.5 flex flex-wrap items-center gap-2 ${compact ? 'text-xs' : 'text-[13px]'}`}>
              {day.places.map((place, index) => (
                <Fragment key={place.placeId}>
                  {index > 0 ? <span className="text-text-soft">·</span> : null}
                  <Link
                    to={`/places/${place.placeId}`}
                    className="border-b border-transparent text-accent-text transition-colors hover:border-accent"
                  >
                    {place.title}
                  </Link>
                </Fragment>
              ))}
            </p>
          </div>
        ))}

        <div className={`flex flex-wrap items-center gap-3 ${compact ? 'mt-3.5' : 'mt-4'}`}>
          {routeUrl ? (
            <ButtonLink to={routeUrl} size={compact ? 'mobile' : 'desktop'} external>
              {t('recommendResults.directions')}
            </ButtonLink>
          ) : null}
          {lodging.length > 0 ? (
            <span className="text-xs text-text-soft">{t('courseDetail.lodging', { title: lodging.join(', ') })}</span>
          ) : null}
        </div>
      </div>
    </div>
  )
}

/** 공유 메시지에 실릴 한 줄 요약. */
function factsOf(course: CourseResponse): string {
  const placeCount = course.days.reduce((sum, d) => sum + d.places.length, 0)
  return `${placeCount} places · ${course.totalDistanceKm.toFixed(1)}km`
}

export default CourseItinerary
