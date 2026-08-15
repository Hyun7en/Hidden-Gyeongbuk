import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ApiRequestError, recommendCourses } from '../api/client'
import type { CourseConstraints, CourseResponse } from '../api/types'
import CourseItinerary from '../components/CourseItinerary'
import { ButtonLink } from '../components/Button'
import { useHubs } from '../hooks/useHubs'
import { useLanguage } from '../context/LanguageContext'
import { formatDateRange, isPastTrip, tripLengthLabel } from '../lib/tripDates'
import { type Difficulty, type Interest } from '../types/course'

/**
 * S4 추천 결과. 결과 목록과 코스 상세가 <b>한 화면</b>이다(화면 설계서 S4) — 모바일은 아코디언,
 * 데스크톱은 좌측 목록(420px) + 우측 상세 분할이다. 코스를 비교하려면 목록과 내용을 오가야 하는데,
 * 화면을 나누면 그 왕복이 매번 페이지 전환이 된다.
 *
 * <p>URL이 화면의 정본이다. 추천 조건이 전부 쿼리에 있어 이 주소만으로 재계산되고(무상태,
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
        setError({ code: err instanceof ApiRequestError ? err.code : 'INTERNAL_ERROR', message: err.message })
      })
    return () => {
      cancelled = true
    }
  }, [hubId, startDate, endDate, difficulty, interests, regionIds, lang])

  const matchedHub = hubs.find((h) => h.hubId === hubId)
  const tripLabel = startDate && endDate ? tripLengthLabel(t, startDate, endDate) : ''

  function selectCourse(rank: number, collapsible: boolean) {
    const next = collapsible && openRank === rank ? null : rank
    setOpenRank(next)
    // 펼친 코스를 주소에 남겨 공유·새로고침이 같은 상태로 돌아오게 한다. 조건 쿼리는 유지한다.
    const pathname = next === null ? '/recommend/results' : `/recommend/results/${next}`
    navigate({ pathname, search: `?${searchParams}` }, { replace: true })
  }

  if (!hubId || !startDate || !endDate) {
    return (
      <Shell>
        <h1 className="font-[family-name:var(--font-serif-heading)] text-[26px] font-semibold">
          {t('recommendResults.title')}
        </h1>
        <p className="mt-4 text-sm text-text-soft">{t('recommendResults.missingParams')}</p>
      </Shell>
    )
  }

  const openCourse = courses?.find((c) => c.rank === openRank) ?? null

  return (
    <Shell>
      <h1 className="font-[family-name:var(--font-serif-heading)] text-[26px] font-semibold">
        {t('recommendResults.title')}
      </h1>

      {/* 적용된 조건·제약을 한 줄 띠로 — 결과가 왜 이렇게 나왔는지의 근거다. */}
      <div className="mt-3 inline-flex flex-wrap gap-x-5 gap-y-1.5 border border-line bg-[var(--glass-bg)] px-4 py-2.5 text-xs text-text-soft">
        <span>{matchedHub?.name ?? `Hub ${hubId}`}</span>
        <span>{formatDateRange(lang, startDate, endDate)}</span>
        <span>{tripLabel}</span>
        {constraints ? (
          <>
            <span>{t('recommendResults.constraintPlaces', { count: constraints.placeCount })}</span>
            <span>{t('recommendResults.constraintDistance', { km: constraints.maxDistanceKm })}</span>
          </>
        ) : null}
      </div>

      {/* 과거 날짜 링크는 막지 않고 알리기만 한다(ADR-020 결과 ④) — 오래된 공유 링크로 들어온 경우다. */}
      {isPastTrip(startDate) ? (
        <p className="mt-3 border border-accent/40 bg-accent/10 px-3 py-2 text-[13px] text-text-soft">
          {t('recommendResults.pastTrip')}
        </p>
      ) : null}

      {error ? (
        <ErrorPanel code={error.code} message={error.message} search={`?${searchParams}`} />
      ) : courses === null ? (
        <Loading />
      ) : courses.length === 0 ? (
        <EmptyPanel search={`?${searchParams}`} />
      ) : (
        <>
          {/* 데스크톱: 좌 목록 420px + 우 상세 */}
          <div className="mt-5 hidden items-start gap-7 md:flex">
            <div className="flex w-[420px] flex-none flex-col gap-3">
              {courses.map((course) => (
                <CourseListItem
                  key={course.rank}
                  course={course}
                  selected={course.rank === openRank}
                  onClick={() => selectCourse(course.rank, false)}
                />
              ))}
            </div>
            <div className="min-w-0 flex-1">
              {openCourse ? (
                <CourseItinerary course={openCourse} hub={matchedHub} title={routeLabel(openCourse)} />
              ) : (
                <p className="text-sm text-text-soft">{t('recommendResults.pickOne')}</p>
              )}
            </div>
          </div>

          {/* 모바일: 아코디언 */}
          <div className="mt-4 flex flex-col gap-3 md:hidden">
            {courses.map((course) => {
              const open = course.rank === openRank
              return (
                <div
                  key={course.rank}
                  className={`border bg-bg-elevated transition-colors ${open ? 'border-accent' : 'border-line'}`}
                >
                  <button
                    type="button"
                    onClick={() => selectCourse(course.rank, true)}
                    aria-expanded={open}
                    className="flex w-full items-center justify-between gap-3 p-3.5 text-left"
                  >
                    <span>
                      <span className="block text-sm font-semibold">{routeLabel(course)}</span>
                      <span className="mt-0.5 block text-[11px] text-text-soft">{metaOf(t, course)}</span>
                    </span>
                    <span aria-hidden="true" className="text-xs text-text-soft">
                      {open ? '▾' : '▸'}
                    </span>
                  </button>
                  {open ? (
                    <div className="border-t border-line px-3.5 pb-4 pt-3">
                      <CourseItinerary course={course} hub={matchedHub} title={routeLabel(course)} compact />
                    </div>
                  ) : null}
                </div>
              )
            })}
          </div>
        </>
      )}
    </Shell>
  )
}

function CourseListItem({
  course,
  selected,
  onClick,
}: {
  course: CourseResponse
  selected: boolean
  onClick: () => void
}) {
  const { t } = useTranslation()
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={`border bg-bg-elevated p-4 text-left transition-colors ${
        selected ? 'border-accent' : 'border-line hover:border-accent-hover'
      }`}
    >
      <span className="block text-[15px] font-semibold">{routeLabel(course)}</span>
      <span className="mt-1 block text-xs text-text-soft">{metaOf(t, course)}</span>
    </button>
  )
}

function Loading() {
  const { t } = useTranslation()
  return (
    <div className="mt-10 flex flex-col items-center gap-3.5 py-20">
      <div className="h-[34px] w-[34px] animate-spin rounded-full border-2 border-line border-t-accent" />
      <p className="text-sm">{t('recommendResults.searching')}</p>
      <p className="text-xs text-text-soft">{t('recommendResults.searchingSub')}</p>
    </div>
  )
}

/**
 * 422와 빈 결과는 <b>고장이 아니다</b> — 조건이 좁아 코스를 못 만든 것이라 재시도 버튼을 주면
 * 안 된다(화면 설계서 C3: 에러/빈 상태/정상 흐름 구분). 할 일은 조건을 넓히는 것이다.
 */
function EmptyPanel({ search }: { search: string }) {
  const { t } = useTranslation()
  return (
    <div className="mt-10 flex flex-col items-center gap-3 border border-dashed border-line px-6 py-14 text-center">
      <p className="text-base font-semibold">{t('recommendResults.emptyTitle')}</p>
      <p className="max-w-[420px] text-[13px] leading-relaxed text-text-soft">{t('recommendResults.emptyDesc')}</p>
      <div className="mt-1.5">
        <ButtonLink to={`/recommend${search}`}>{t('recommendResults.resetConditions')}</ButtonLink>
      </div>
    </div>
  )
}

function ErrorPanel({ code, message, search }: { code: string; message: string; search: string }) {
  const { t } = useTranslation()
  if (code === 'NO_COURSE_FOUND') {
    return <EmptyPanel search={search} />
  }
  return <p className="mt-10 text-sm text-text-soft">{t('recommendResults.error', { error: message })}</p>
}

/** 코스를 알아보게 하는 이름 — 앞쪽 장소 셋을 잇는다. */
function routeLabel(course: CourseResponse): string {
  return course.days
    .flatMap((d) => d.places)
    .map((p) => (p.title ?? '').split(' ')[0])
    .filter((v, i, arr) => v !== '' && arr.indexOf(v) === i)
    .slice(0, 3)
    .join(' · ')
}

function metaOf(t: ReturnType<typeof useTranslation>['t'], course: CourseResponse): string {
  return t('recommendResults.courseFacts', {
    places: course.days.reduce((sum, d) => sum + d.places.length, 0),
    regions: course.regionCount,
    distance: course.totalDistanceKm.toFixed(1),
  })
}

function Shell({ children }: { children: React.ReactNode }) {
  return <div className="screen-in mx-auto w-full max-w-[1180px] px-4 py-8 pb-14 md:px-6">{children}</div>
}

export default RecommendResults
