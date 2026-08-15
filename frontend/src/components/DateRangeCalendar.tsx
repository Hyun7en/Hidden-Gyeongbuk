import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { MAX_NIGHTS, addDays, isSameDay, nightsBetween, parseIsoDate, toIsoDate, today } from '../lib/tripDates'

/**
 * 날짜 범위 선택 캘린더(S3). `duration` enum을 대체한다(ADR-020).
 *
 * <p><b>직접 만든 이유</b>: 네이티브 `input[type=date]`는 값이 하나뿐이라 범위를 표현할 수 없고,
 * 두 개를 나란히 두면 "종료일이 시작일보다 앞" 같은 상태를 사용자가 만들 수 있다. 한 그리드에서
 * 두 번 누르게 하면 그 상태 자체가 생기지 않는다(Browser Compatibility §2.9).
 *
 * <p>한 달만 보여준다 — 프로토타입과 같다. 두 달을 나란히 놓으면 480px 폼 안에서 셀이 뭉개진다.
 */
function DateRangeCalendar({
  start,
  end,
  onChange,
}: {
  start: string | null
  end: string | null
  onChange: (start: string | null, end: string | null) => void
}) {
  const { t, i18n } = useTranslation()
  const min = today()
  const [cursor, setCursor] = useState(() => {
    // `new Date("2026-05-01")`은 UTC로 해석돼 시간대에 따라 하루 어긋난다 — parseIsoDate를 쓴다.
    const anchor = parseIsoDate(start) ?? min
    return new Date(anchor.getFullYear(), anchor.getMonth(), 1)
  })
  // 시작일만 고른 상태에서 마우스를 올린 날까지를 미리 칠해준다 — 몇 박이 되는지 누르기 전에 보인다.
  const [hovered, setHovered] = useState<Date | null>(null)

  const startDate = parseIsoDate(start)
  const endDate = parseIsoDate(end)
  const previewEnd = endDate ?? (startDate && hovered && hovered > startDate ? hovered : null)

  const weekdays = useMemo(() => {
    // 요일 이름을 직접 갖지 않고 Intl에서 얻는다 — 언어를 늘려도 이 컴포넌트는 그대로다.
    const formatter = new Intl.DateTimeFormat(i18n.language, { weekday: 'short' })
    return Array.from({ length: 7 }, (_, i) => formatter.format(new Date(2024, 0, 7 + i)))
  }, [i18n.language])

  function select(day: Date) {
    if (!startDate || endDate) {
      onChange(toIsoDate(day), null)
      return
    }
    // 시작일보다 앞을 누르면 "범위 축소"가 아니라 "다시 고르기"로 읽는 편이 자연스럽다.
    if (day < startDate || nightsBetween(startDate, day) > MAX_NIGHTS) {
      onChange(toIsoDate(day), null)
      return
    }
    onChange(toIsoDate(startDate), toIsoDate(day))
  }

  return (
    <div className="border border-line bg-bg-elevated p-3.5">
      <div className="mb-2.5 flex items-center justify-between">
        <NavButton
          label={t('calendar.prevMonth')}
          onClick={() => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() - 1, 1))}
          disabled={cursor <= new Date(min.getFullYear(), min.getMonth(), 1)}
        >
          ‹
        </NavButton>
        <p className="text-[13px] font-semibold" aria-live="polite">
          {new Intl.DateTimeFormat(i18n.language, { year: 'numeric', month: 'long' }).format(cursor)}
        </p>
        <NavButton
          label={t('calendar.nextMonth')}
          onClick={() => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1))}
        >
          ›
        </NavButton>
      </div>

      <div className="mb-1 grid grid-cols-7 gap-0.5">
        {weekdays.map((name) => (
          <div key={name} className="py-1 text-center text-[10px] text-text-soft">
            {name}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-0.5" onMouseLeave={() => setHovered(null)}>
        {monthCells(cursor).map((day, index) =>
          day === null ? (
            <div key={`empty-${index}`} />
          ) : (
            <DayCell
              key={day.getTime()}
              day={day}
              disabled={day < min}
              selected={
                (startDate !== null && isSameDay(day, startDate)) || (endDate !== null && isSameDay(day, endDate))
              }
              inRange={startDate !== null && previewEnd !== null && day > startDate && day < previewEnd}
              onSelect={() => select(day)}
              onHover={() => setHovered(day)}
            />
          ),
        )}
      </div>
    </div>
  )
}

function NavButton({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string
  onClick: () => void
  disabled?: boolean
  children: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="h-7 w-7 text-[15px] text-text-soft transition-colors hover:text-accent-text disabled:cursor-not-allowed disabled:opacity-30"
    >
      {children}
    </button>
  )
}

function DayCell({
  day,
  disabled,
  selected,
  inRange,
  onSelect,
  onHover,
}: {
  day: Date
  disabled: boolean
  selected: boolean
  inRange: boolean
  onSelect: () => void
  onHover: () => void
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onSelect}
      onMouseEnter={onHover}
      onFocus={onHover}
      aria-pressed={selected}
      className={[
        'h-[34px] text-xs tabular-nums transition-colors',
        disabled ? 'cursor-not-allowed text-text-soft/35' : 'hover:bg-accent/15',
        selected ? 'bg-accent font-semibold text-on-accent' : '',
        inRange && !selected ? 'bg-accent/15' : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {day.getDate()}
    </button>
  )
}

/** 앞쪽 빈칸 + 그 달의 날짜들. 주 시작은 일요일. */
function monthCells(month: Date): (Date | null)[] {
  const first = new Date(month.getFullYear(), month.getMonth(), 1)
  const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  const cells: (Date | null)[] = Array.from({ length: first.getDay() }, () => null)
  for (let d = 0; d < daysInMonth; d += 1) {
    cells.push(addDays(first, d))
  }
  return cells
}

export default DateRangeCalendar
