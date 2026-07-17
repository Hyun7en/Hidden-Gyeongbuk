import { useLocation, useNavigate, useParams } from 'react-router-dom'
import BackHeader from '../components/BackHeader'
import Container from '../components/Container'
import FiberBackground from '../components/FiberBackground'
import type { CourseResponse } from '../api/types'
import { translateCategory } from '../api/labels'

const BREAKDOWN_LABELS: Record<keyof CourseResponse['breakdown'], string> = {
  interest: 'Interest match',
  access: 'Access',
  stay: 'Stay quality',
  dispersion: 'Dispersion',
  completeness: 'Data completeness',
}

function CourseDetail() {
  const navigate = useNavigate()
  const { rank } = useParams()
  const location = useLocation()
  const state = location.state as { course?: CourseResponse; durationLabel?: string } | null
  const course = state?.course

  if (!course) {
    return (
      <div className="relative min-h-screen bg-bg">
        <FiberBackground />
        <div className="relative z-10">
          <BackHeader title={`Course ${rank ?? ''}`} onBack={() => navigate(-1)} />
          <Container className="py-16 text-center">
            <p className="text-sm text-text-soft">
              Course details aren't available directly — please go back and pick a course from the results list.
            </p>
          </Container>
        </div>
      </div>
    )
  }

  const placeCount = course.days.reduce((sum, d) => sum + d.places.length, 0)

  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10">
        <BackHeader title={`Course ${course.rank}`} onBack={() => navigate(-1)} rightSlot="Share" />

        <Container className="py-8 md:py-14">
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
                    <h2 className="mb-3 text-[15px] font-bold md:text-lg">Day {day.day}</h2>
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
                            {translateCategory(place.category)}
                          </span>
                        </li>
                      ))}
                    </ol>
                    {day.lodging ? (
                      <p className="mt-2 text-[13px] text-text-soft md:text-sm">Lodging: {day.lodging.title}</p>
                    ) : null}
                  </div>
                ))}
              </div>
            </div>

            <div className="mt-10 md:mt-0">
              <h2 className="mb-4 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">Score breakdown</h2>
              <div className="space-y-3">
                {(Object.keys(course.breakdown) as (keyof CourseResponse['breakdown'])[]).map((key) => (
                  <div key={key}>
                    <div className="mb-1 flex justify-between text-xs text-text-soft md:text-sm">
                      <span>{BREAKDOWN_LABELS[key]}</span>
                      <span className="tabular-nums">{course.breakdown[key].toFixed(0)}</span>
                    </div>
                    <div className="h-1.5 bg-line/40">
                      <div className="h-full bg-accent" style={{ width: `${course.breakdown[key]}%` }} />
                    </div>
                  </div>
                ))}
              </div>

              <div className="relative mt-8 h-[160px] border border-line bg-bg-elevated md:h-[220px]">
                <div className="flex h-full items-center justify-center text-[11px] text-text-soft">[ Route map ]</div>
              </div>
            </div>
          </div>
        </Container>
      </div>
    </div>
  )
}

export default CourseDetail
