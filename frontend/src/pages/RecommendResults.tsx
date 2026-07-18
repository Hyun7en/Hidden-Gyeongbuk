import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { recommendCourses } from '../api/client'
import type { CourseResponse } from '../api/types'
import BackHeader from '../components/BackHeader'
import Container from '../components/Container'
import CourseCard from '../components/CourseCard'
import FiberBackground from '../components/FiberBackground'
import { useHubs } from '../hooks/useHubs'
import { useLanguage } from '../context/LanguageContext'
import { DIFFICULTY_OPTIONS, DURATION_OPTIONS, type Difficulty, type Duration, type Interest } from '../types/course'

function RecommendResults() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const { hubs } = useHubs()
  const { lang } = useLanguage()

  const hubId = Number(searchParams.get('hub'))
  const duration = (searchParams.get('duration') as Duration) ?? 'ONE_DAY'
  const difficulty = (searchParams.get('difficulty') as Difficulty) ?? 'NORMAL'
  const interests = (searchParams.get('interests')?.split(',').filter(Boolean) ?? []) as Interest[]

  const [courses, setCourses] = useState<CourseResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!hubId) return
    setCourses(null)
    setError(null)
    recommendCourses({ hubId, duration, difficulty, interests }, lang)
      .then((res) => setCourses(res.courses))
      .catch((err: Error) => setError(err.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hubId, duration, difficulty, searchParams.get('interests'), lang])

  const matchedHub = hubs.find((h) => h.hubId === hubId)
  const hubLabel = (lang === 'ko' ? matchedHub?.nameKo : matchedHub?.nameEn) ?? `Hub ${hubId}`
  const durationLabel = t(DURATION_OPTIONS.find((o) => o.value === duration)?.labelKey ?? duration)
  const difficultyLabel = t(DIFFICULTY_OPTIONS.find((o) => o.value === difficulty)?.labelKey ?? difficulty)

  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10">
        <BackHeader backTo="/recommend" />
        <Container className="py-8 md:py-14">
          <h1 className="mb-6 text-2xl font-bold md:mb-8 md:text-3xl">{t('recommendResults.title')}</h1>
          {error ? (
            <p className="text-sm text-text-soft">{t('recommendResults.error', { error })}</p>
          ) : courses === null ? (
            <p className="text-sm text-text-soft">{t('recommendResults.building')}</p>
          ) : courses.length === 0 ? (
            <p className="text-sm text-text-soft">{t('recommendResults.empty', { hub: hubLabel })}</p>
          ) : (
            <>
              <p className="mb-8 text-sm text-text-soft md:mb-12 md:text-base">
                {t('recommendResults.summary', {
                  count: courses.length,
                  hub: hubLabel,
                  duration: durationLabel,
                  difficulty: difficultyLabel,
                })}
              </p>
              <div className="grid gap-4 md:grid-cols-3 md:gap-6">
                {courses.map((course) => (
                  <CourseCard key={course.rank} course={course} durationLabel={durationLabel} hub={matchedHub} />
                ))}
              </div>
            </>
          )}
        </Container>
      </div>
    </div>
  )
}

export default RecommendResults
