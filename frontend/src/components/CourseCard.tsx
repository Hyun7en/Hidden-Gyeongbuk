import { Link } from 'react-router-dom'
import type { CourseResponse } from '../api/types'

function CourseCard({ course, durationLabel }: { course: CourseResponse; durationLabel: string }) {
  const route = course.days
    .flatMap((d) => d.places)
    .map((p) => p.title.split(' ')[0])
    .filter((v, i, arr) => arr.indexOf(v) === i)
    .slice(0, 3)
    .join(' · ')
  const placeCount = course.days.reduce((sum, d) => sum + d.places.length, 0)

  return (
    <Link
      to={`/recommend/results/${course.rank}`}
      state={{ course, durationLabel }}
      className="block border border-line bg-bg-elevated p-4 md:p-6"
    >
      <div className="mb-2.5 flex items-start justify-between">
        <span className="text-[11px] font-bold uppercase tracking-[0.05em] text-text-soft md:text-xs">Course {course.rank}</span>
        <span className="inline-flex items-baseline gap-[3px] bg-seal-tint px-[9px] py-[3px] text-sm font-bold tabular-nums text-seal md:text-base">
          {course.score.toFixed(1)}
          <small className="text-[10px] font-semibold">pt</small>
        </span>
      </div>
      <p className="mb-1 text-base font-bold md:text-lg">{route}</p>
      <p className="mb-3 text-xs text-text-soft md:text-sm">
        {durationLabel} · {placeCount} places · {course.totalDistanceKm.toFixed(1)}km
      </p>
      <div className="flex h-[72px] items-center justify-center border border-dashed border-line text-[11px] text-text-soft md:h-[110px]">
        [ Mini map ]
      </div>
    </Link>
  )
}

export default CourseCard
