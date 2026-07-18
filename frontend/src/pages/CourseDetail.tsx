import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import BackHeader from '../components/BackHeader'
import Container from '../components/Container'
import FiberBackground from '../components/FiberBackground'
import KakaoMap from '../components/KakaoMap'
import ShareButton from '../components/ShareButton'
import type { CourseResponse } from '../api/types'
import { translateCategory } from '../api/labels'
import { useHubs } from '../hooks/useHubs'
import { useLanguage } from '../context/LanguageContext'
import { googleDirectionsUrl } from '../lib/directions'

function DirectionsIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="currentColor" aria-hidden="true">
      <path d="M3 11l18-8-8 18-2-8-8-2z" />
    </svg>
  )
}

const BREAKDOWN_LABEL_KEYS: Record<keyof CourseResponse['breakdown'], string> = {
  interest: 'courseDetail.breakdown.interest',
  access: 'courseDetail.breakdown.access',
  stay: 'courseDetail.breakdown.stay',
  dispersion: 'courseDetail.breakdown.dispersion',
  completeness: 'courseDetail.breakdown.completeness',
}

function CourseDetail() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { lang } = useLanguage()
  const { hubs } = useHubs()
  const { rank } = useParams()
  const location = useLocation()
  const state = location.state as { course?: CourseResponse; durationLabel?: string } | null
  const course = state?.course

  if (!course) {
    return (
      <div className="relative min-h-screen bg-bg">
        <FiberBackground />
        <div className="relative z-10">
          <BackHeader onBack={() => navigate(-1)} />
          <Container className="py-16 text-center">
            <h1 className="mb-6 text-2xl font-bold md:text-3xl">{t('common.courseRank', { rank: rank ?? '' })}</h1>
            <p className="text-sm text-text-soft">{t('courseDetail.notAvailable')}</p>
          </Container>
        </div>
      </div>
    )
  }

  const placeCount = course.days.reduce((sum, d) => sum + d.places.length, 0)
  const hub = hubs.find((h) => h.hubId === course.hubId)
  // Global sequence across the whole trip, not CoursePlaceResponse.order (which
  // restarts at 1 each day) — the map should show one continuous route. Hub goes
  // first so the route line actually starts where the trip starts (hub-and-spoke
  // design) instead of jumping straight between places with no origin.
  const routeMarkers = [
    ...(hub ? [{ lat: hub.lat, lng: hub.lng, label: hub.nameEn, isHub: true }] : []),
    ...course.days.flatMap((d) => d.places).map((p, i) => ({ lat: p.lat, lng: p.lng, label: p.title, order: i + 1 })),
  ]

  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10">
        <BackHeader onBack={() => navigate(-1)} />

        <Container className="py-8 md:py-14">
          <h1 className="mb-6 text-2xl font-bold md:text-3xl">{t('common.courseRank', { rank: course.rank })}</h1>
          <div className="md:grid md:grid-cols-[1.3fr_1fr] md:gap-16">
            <div>
              <div className="mb-6 flex items-center gap-3">
                <span className="inline-flex items-baseline gap-[3px] bg-seal-tint px-3 py-1.5 text-xl font-bold tabular-nums text-seal md:text-2xl">
                  {course.score.toFixed(1)}
                  <small className="text-xs font-semibold">pt</small>
                </span>
                <span className="text-sm text-text-soft md:text-base">
                  {state?.durationLabel ?? ''} · {placeCount} places · {course.totalDistanceKm.toFixed(1)}km
                </span>
              </div>

              <div className="space-y-6 md:space-y-8">
                {course.days.map((day) => (
                  <div key={day.day}>
                    <h2 className="mb-3 text-[15px] font-bold md:text-lg">{t('courseDetail.day', { day: day.day })}</h2>
                    <ol className="space-y-2">
                      {day.places.map((place) => (
                        <li
                          key={place.order}
                          className="flex items-center gap-3 border border-line bg-bg-elevated px-3 py-2.5 md:px-4 md:py-3"
                        >
                          <span className="flex h-6 w-6 shrink-0 items-center justify-center bg-accent text-xs font-bold text-bg-elevated">
                            {place.order}
                          </span>
                          <span className="text-sm md:text-base">{place.title}</span>
                          <span className="ml-auto text-[11px] uppercase text-text-soft md:text-xs">
                            {lang === 'en' ? translateCategory(place.category) : place.category}
                          </span>
                          <a
                            href={googleDirectionsUrl(place.lat, place.lng)}
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
            </div>

            <div className="mt-10 md:mt-0">
              <h2 className="mb-4 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                {t('courseDetail.scoreBreakdown')}
              </h2>
              <div className="space-y-3">
                {(Object.keys(course.breakdown) as (keyof CourseResponse['breakdown'])[]).map((key) => (
                  <div key={key}>
                    <div className="mb-1 flex justify-between text-xs text-text-soft md:text-sm">
                      <span>{t(BREAKDOWN_LABEL_KEYS[key])}</span>
                      <span className="tabular-nums">{course.breakdown[key].toFixed(0)}</span>
                    </div>
                    <div className="h-1.5 bg-line/40">
                      <div className="h-full bg-accent" style={{ width: `${course.breakdown[key]}%` }} />
                    </div>
                  </div>
                ))}
              </div>

              <div className="relative mt-8 h-[160px] border border-line bg-bg-elevated md:h-[220px]">
                <ShareButton
                  title={t('common.courseRank', { rank: course.rank })}
                  description={`${state?.durationLabel ?? ''} · ${placeCount} places · ${course.totalDistanceKm.toFixed(1)}km`}
                />
                <KakaoMap className="h-full w-full" markers={routeMarkers} drawRoute />
              </div>
            </div>
          </div>
        </Container>
      </div>
    </div>
  )
}

export default CourseDetail
