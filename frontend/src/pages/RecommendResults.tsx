import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ApiRequestError, recommendCourses } from '../api/client'
import type { CourseConstraints, CourseResponse } from '../api/types'
import Container from '../components/Container'
import CourseItinerary from '../components/CourseItinerary'
import { useHubs } from '../hooks/useHubs'
import { useLanguage } from '../context/LanguageContext'
import { formatDateRange, isPastTrip, tripLengthLabel } from '../lib/tripDates'
import { DIFFICULTY_OPTIONS, type Difficulty, type Interest } from '../types/course'

/**
 * S4 추천 결과. 결과 목록과 코스 상세가 <b>한 화면</b>이다(화면 설계서 S4) — 모바일은 아코디언,
 * 데스크톱은 좌측 목록 + 우측 상세 분할이다. 코스를 비교하려면 목록과 내용을 오가야 하는데,
 * 화면을 나누면 그 왕복이 매번 페이지 전환이 된다.
 *
 * <p>URL은 화면의 정본이다. 추천 조건이 전부 쿼리에 있어 이 주소만으로 재계산되고(무상태,
 * ADR-003/009), `/recommend/results/:rank`로 특정 코스를 펼친 상태까지 공유된다.
 */
function RecommendResults() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { rank: rankParam } = useParams()
  const { hubs } = useHubs()
  const { lang } = useLanguage()

  const hubId = Number(searchParams.get('hub'))
  const startDate = searchParams.get('startDate')
  const endDate = searchParams.get('endDate')
  const difficulty = (searchParams.get('difficulty') as Difficulty) ?? 'NORMAL'
  const interestsParam = searchParams.get('interests')
  const regionsParam = searchParams.get('regions')

  const interests = useMemo(
    () => (interestsParam?.split(',').filter(Boolean) ?? []) as Interest[],
    [interestsParam],
  )
  const regionIds = useMemo(
    () => regionsParam?.split(',').map(Number).filter((n) => !Number.isNaN(n)) ?? [],
    [regionsParam],
  )

  const [courses, setCourses] = useState<CourseResponse[] | null>(null)
  const [constraints, setConstraints] = useState<CourseConstraints | null>(null)
  const [error, setError] = useState<{ code: string; message: string } | null>(null)
  const [openRank, setOpenRank] = useState<number | null>(rankParam ? Number(rankParam) : null)

  useEffect(() => {
    if (!hubId || !startDate || !endDate) return
    let cancelled = false
    setCourses(null)
    setConstraints(null)
    setError(null)
    recommendCourses({ hubId, regionIds, startDate, endDate, difficulty, interests }, lang)
      .then((res) => {
        if (cancelled) return
        setCourses(res.courses)
        setConstraints(res.constraints)
        // 아무것도 안 펼쳐진 상태로 두면 데스크톱 우측이 빈 채로 남는다 — 1위를 기본으로 연다.
        setOpenRank((prev) => prev ?? res.courses[0]?.rank ?? null)
      })
      .catch((err: Error) => {
        if (cancelled) return
        setError({
          code: err instanceof ApiRequestError ? err.code : 'INTERNAL_ERROR',
          message: err.message,
        })
      })
    return () => {
      cancelled = true
    }
  }, [hubId, startDate, endDate, difficulty, interests, regionIds, lang])

  const matchedHub = hubs.find((h) => h.hubId === hubId)
  const hubLabel = matchedHub?.name ?? `Hub ${hubId}`
  const tripLabel = startDate && endDate ? tripLengthLabel(t, startDate, endDate) : ''
  const difficultyLabel = t(DIFFICULTY_OPTIONS.find((o) => o.value === difficulty)?.labelKey ?? difficulty)

  function selectCourse(rank: number) {
    const next = openRank === rank ? null : rank
    setOpenRank(next)
    // 펼친 코스를 주소에 남겨 공유·새로고침이 같은 상태로 돌아오게 한다. 조건 쿼리는 유지한다.
    navigate(next === null ? { pathname: '/recommend/results', search: `?${searchParams}` } : { pathname: `/recommend/results/${next}`, search: `?${searchParams}` }, { replace: true })
  }

  if (!hubId || !startDate || !endDate) {
    return (
      <Shell>
        <h1 className="mb-6 text-2xl font-bold md:text-3xl">{t('recommendResults.title')}</h1>
        <p className="text-sm text-text-soft">{t('recommendResults.missingParams')}</p>
      </Shell>
    )
  }

  const openCourse = courses?.find((c) => c.rank === openRank) ?? null

  return (
    <Shell>
      <h1 className="mb-3 text-2xl font-bold md:text-3xl">{t('recommendResults.title')}</h1>
      <p className="text-sm text-text-soft md:text-base">
        {hubLabel} · {formatDateRange(lang, startDate, endDate)} · {tripLabel} · {difficultyLabel}
      </p>
      {/* 과거 날짜 링크는 막지 않고 알리기만 한다(ADR-020 결과 ④) — 오래된 공유 링크로 들어온 경우다. */}
      {isPastTrip(startDate) ? (
        <p className="mt-3 border border-accent/40 bg-accent/10 px-3 py-2 text-[13px] text-text-soft md:text-sm">
          {t('recommendResults.pastTrip')}
        </p>
      ) : null}
      {constraints ? (
        <p className="mt-3 text-xs text-text-soft md:text-[13px]">
          {t('recommendResults.constraints', {
            places: constraints.placeCount,
            distance: constraints.maxDistanceKm,
            lodging: constraints.lodgingCount,
          })}
        </p>
      ) : null}

      <div className="mt-8 md:mt-12">
        {error ? (
          <ErrorPanel code={error.code} message={error.message} />
        ) : courses === null ? (
          <p className="text-sm text-text-soft">{t('recommendResults.building')}</p>
        ) : courses.length === 0 ? (
          <p className="text-sm text-text-soft">{t('recommendResults.empty', { hub: hubLabel })}</p>
        ) : (
          <div className="md:grid md:grid-cols-[minmax(0,300px)_1fr] md:items-start md:gap-10">
            <ul className="border-t border-line md:border-t-0">
              {courses.map((course) => {
                const open = course.rank === openRank
                return (
                  <li key={course.rank} className="border-b border-line md:mb-3 md:border md:border-line">
                    <button
                      type="button"
                      onClick={() => selectCourse(course.rank)}
                      aria-expanded={open}
                      className={`flex w-full items-center justify-between gap-3 px-1 py-4 text-left md:px-4 ${
                        open ? 'md:bg-bg-elevated' : ''
                      }`}
                    >
                      <span>
                        <span className="block text-[15px] font-bold md:text-base">{routeLabel(course)}</span>
                        <span className="mt-1 block text-xs text-text-soft md:text-[13px]">
                          {t('recommendResults.courseFacts', {
                            places: course.days.reduce((sum, d) => sum + d.places.length, 0),
                            regions: course.regionCount,
                            distance: course.totalDistanceKm.toFixed(1),
                          })}
                        </span>
                      </span>
                      {/* 데스크톱은 우측 패널이 상세를 맡으므로 펼침 화살표가 의미 없다. */}
                      <span className="shrink-0 text-text-soft md:hidden" aria-hidden="true">
                        {open ? '−' : '+'}
                      </span>
                    </button>
                    {open ? (
                      <div className="pb-6 md:hidden">
                        <CourseItinerary course={course} hub={matchedHub} tripLabel={tripLabel} />
                      </div>
                    ) : null}
                  </li>
                )
              })}
            </ul>

            <div className="hidden md:block">
              {openCourse ? (
                <CourseItinerary course={openCourse} hub={matchedHub} tripLabel={tripLabel} />
              ) : (
                <p className="text-sm text-text-soft">{t('recommendResults.pickOne')}</p>
              )}
            </div>
          </div>
        )}
      </div>
    </Shell>
  )
}

/**
 * 422는 에러지만 <b>고장이 아니다</b> — 조건이 좁아 코스를 못 만든 것이라 재시도 버튼을 주면
 * 안 된다(화면 설계서 C3: 에러/빈 상태/정상 흐름 구분). 할 일은 조건을 넓히는 것이다.
 */
function ErrorPanel({ code, message }: { code: string; message: string }) {
  const { t } = useTranslation()
  if (code === 'NO_COURSE_FOUND') {
    return (
      <div className="border border-line bg-bg-elevated p-5">
        <p className="text-[15px] font-bold">{t('recommendResults.noCourseTitle')}</p>
        <p className="mt-2 text-sm leading-relaxed text-text-soft">{t('recommendResults.noCourseBody')}</p>
      </div>
    )
  }
  return <p className="text-sm text-text-soft">{t('recommendResults.error', { error: message })}</p>
}

function routeLabel(course: CourseResponse): string {
  return course.days
    .flatMap((d) => d.places)
    .map((p) => (p.title ?? '').split(' ')[0])
    .filter((v, i, arr) => v !== '' && arr.indexOf(v) === i)
    .slice(0, 3)
    .join(' · ')
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative min-h-screen bg-bg">
      <div className="relative z-10">
        <Container className="py-8 md:py-14">{children}</Container>
      </div>
    </div>
  )
}

export default RecommendResults
