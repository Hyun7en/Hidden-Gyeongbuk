import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { recommendCourses } from '../api/client'
import type { CourseResponse } from '../api/types'
import BackHeader from '../components/BackHeader'
import Container from '../components/Container'
import CourseCard from '../components/CourseCard'
import FiberBackground from '../components/FiberBackground'
import { useHubs } from '../hooks/useHubs'
import { DIFFICULTY_OPTIONS, DURATION_OPTIONS, type Difficulty, type Duration, type Interest } from '../types/course'

function RecommendResults() {
  const [searchParams] = useSearchParams()
  const { hubs } = useHubs()

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
    recommendCourses({ hubId, duration, difficulty, interests })
      .then((res) => setCourses(res.courses))
      .catch((err: Error) => setError(err.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hubId, duration, difficulty, searchParams.get('interests')])

  const hubLabel = hubs.find((h) => h.hubId === hubId)?.nameEn ?? `Hub ${hubId}`
  const durationLabel = DURATION_OPTIONS.find((o) => o.value === duration)?.label ?? duration
  const difficultyLabel = DIFFICULTY_OPTIONS.find((o) => o.value === difficulty)?.label ?? difficulty

  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10">
        <BackHeader backTo="/recommend" />
        <Container className="py-8 md:py-14">
          <h1 className="mb-6 text-2xl font-bold md:mb-8 md:text-3xl">Recommended Courses</h1>
          {error ? (
            <p className="text-sm text-text-soft">Couldn't load recommendations — is the backend running? ({error})</p>
          ) : courses === null ? (
            <p className="text-sm text-text-soft">Building your courses…</p>
          ) : courses.length === 0 ? (
            <p className="text-sm text-text-soft">
              No courses matched yet — {hubLabel} may not have enough collected place data for this combination.
            </p>
          ) : (
            <>
              <p className="mb-8 text-sm text-text-soft md:mb-12 md:text-base">
                {courses.length} courses matched for <strong className="text-text">{hubLabel}</strong> · {durationLabel} ·{' '}
                {difficultyLabel}
              </p>
              <div className="grid gap-4 md:grid-cols-3 md:gap-6">
                {courses.map((course) => (
                  <CourseCard key={course.rank} course={course} durationLabel={durationLabel} />
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
