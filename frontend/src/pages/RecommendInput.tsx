import { useState, type ReactNode } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Tag from '../components/Tag'
import { Button } from '../components/Button'
import DateRangeCalendar from '../components/DateRangeCalendar'
import { useHubs } from '../hooks/useHubs'
import { useRegions } from '../hooks/useRegions'
import { useLanguage } from '../context/LanguageContext'
import { parseIsoDate, tripLengthLabel } from '../lib/tripDates'
import { DIFFICULTY_OPTIONS, INTEREST_OPTIONS, type Difficulty, type Interest } from '../types/course'

/**
 * 거점까지 어떻게 가는지. 프로토타입에는 없지만 <b>지우지 않는다</b> — 서울·부산에서 출발하는
 * 외국인 관광객에게 "여기서 갈 수 있는가"는 거점 선택의 실제 판단 근거이고, Hub-and-Spoke
 * 설계의 근간이다(ADR-019). 프로토타입 어휘에 맞춰 작은 보조 문구로만 둔다.
 *
 * 키는 hub.code다 — 표시명은 요청 언어로 바뀌므로 식별자로 쓸 수 없다.
 */
const HUB_ACCESS: Record<string, { en: string; ko: string }> = {
  ANDONG: {
    en: 'Train from Cheongnyangni (Seoul) to Andong Station, about 3h — or express bus from Seoul, about 3.5h.',
    ko: '서울 청량리에서 안동역까지 기차로 약 3시간 — 또는 서울에서 고속버스로 약 3시간 30분.',
  },
  DONGDAEGU: {
    en: 'KTX/SRT from Seoul to Dongdaegu Station, about 1h50m — the fastest way into the region.',
    ko: '서울에서 동대구역까지 KTX/SRT로 약 1시간 50분 — 이 지역으로 가는 가장 빠른 방법입니다.',
  },
  POHANG: {
    en: 'Direct KTX from Seoul to Pohang Station, about 2h15m.',
    ko: '서울에서 포항역까지 KTX 직통으로 약 2시간 15분.',
  },
  'Bukbu Naeryuk': {
    en: 'Train or intercity bus to Yeongju Station, the inland region’s rail gateway, about 3h from Seoul.',
    ko: '내륙 지역의 철도 관문인 영주역까지 기차 또는 시외버스로, 서울에서 약 3시간.',
  },
}

/**
 * S3 추천 입력. 레이아웃은 프로토타입을 그대로 따른다 — 480px 단일 컬럼, 가운데 정렬 제목,
 * 섹션마다 굵은 라벨 + accent 색 선택 힌트.
 *
 * <p>순서는 거점 → 지역 → 관심사 → 날짜 → 난이도다. <b>날짜가 뒤에 있는 것은 의도적이다</b> —
 * 무엇을 보고 싶은지 먼저 정하고 일정을 맞추는 순서이고, 캘린더가 위에 있으면 화면을 다 차지해
 * 나머지 선택이 접힌다.
 *
 * <p>기간은 칩이 아니라 날짜 범위다(ADR-020). 지역은 선택 사항인 다중선택이고, 비워 두면 거점
 * 주변 전체에서 코스가 짜인다(ADR-019).
 */
function RecommendInput() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { hubs, loading: hubsLoading, error: hubsError } = useHubs()
  const { lang } = useLanguage()

  const [hubId, setHubId] = useState<number | null>(() => {
    const raw = searchParams.get('hub')
    return raw ? Number(raw) : null
  })
  const [startDate, setStartDate] = useState<string | null>(null)
  const [endDate, setEndDate] = useState<string | null>(null)
  const [calendarOpen, setCalendarOpen] = useState(false)
  // 지역 상세(S2)의 "이 지역 코스 추천받기"가 지역을 미리 채워 보낸다(ADR-019 결과 ④).
  const [regionIds, setRegionIds] = useState<number[]>(() => {
    const raw = searchParams.get('regions')
    return raw ? raw.split(',').map(Number).filter((n) => !Number.isNaN(n)) : []
  })
  // 프로토타입처럼 기본값을 미리 골라 둔다 — 난이도는 안 고르면 막히는 항목이 아니라 기본이 있는 항목이다.
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY')
  const [interests, setInterests] = useState<Interest[]>([])

  // 거점을 고르면 그 거점 소속 지역이 위로 온다 — 위치 권한 없이 선택을 돕는다(ADR-019 (3)).
  const { regions, error: regionsError } = useRegions(hubId ?? undefined)

  const canSubmit = hubId !== null && startDate !== null && endDate !== null && interests.length > 0

  function toggleInterest(value: Interest) {
    setInterests((prev) => (prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]))
  }

  function toggleRegion(value: number) {
    setRegionIds((prev) => (prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]))
  }

  function handleSubmit() {
    if (!canSubmit) return
    const params = new URLSearchParams({
      hub: String(hubId),
      startDate: startDate!,
      endDate: endDate!,
      difficulty,
    })
    if (regionIds.length > 0) params.set('regions', regionIds.join(','))
    params.set('interests', interests.join(','))
    navigate(`/recommend/results?${params.toString()}`)
  }

  const selectedHubAccess = HUB_ACCESS[hubs.find((h) => h.hubId === hubId)?.code ?? '']
  const dateFormat = new Intl.DateTimeFormat(lang, { month: 'short', day: 'numeric' })
  const labelOf = (iso: string | null) => {
    const parsed = parseIsoDate(iso)
    return parsed ? dateFormat.format(parsed) : t('recommendInput.pickDate')
  }

  return (
    <div className="screen-in mx-auto w-full max-w-[480px] px-4 py-11 pb-16 md:px-0">
      <div className="text-center">
        <h1 className="font-[family-name:var(--font-serif-heading)] text-[26px] font-semibold">
          {t('recommendInput.title')}
        </h1>
        <p className="mt-2 text-[13px] leading-relaxed text-text-soft">{t('recommendInput.subtitle')}</p>
      </div>

      <div className="mt-8 flex flex-col gap-8">
        <Field label={t('recommendInput.startingFrom')} hint={t('recommendInput.singleSelect')}>
          {hubsError ? (
            <p className="text-sm text-text-soft">{t('recommendInput.hubsError', { error: hubsError })}</p>
          ) : hubsLoading ? (
            <p className="text-sm text-text-soft">{t('recommendInput.loadingHubs')}</p>
          ) : (
            <>
              <div className="flex flex-wrap gap-2">
                {hubs.map((hub) => (
                  <Tag key={hub.hubId} active={hubId === hub.hubId} onClick={() => setHubId(hub.hubId)}>
                    {hub.name}
                  </Tag>
                ))}
              </div>
              {hubId !== null ? (
                <p className="mt-2.5 text-xs leading-relaxed text-text-soft">
                  {selectedHubAccess ? selectedHubAccess[lang] : t('recommendInput.gettingThereFallback')}
                </p>
              ) : null}
            </>
          )}
        </Field>

        <Field label={t('recommendInput.regions')} hint={t('recommendInput.multiSelect')}>
          {regionsError ? (
            <p className="text-sm text-text-soft">{t('recommendInput.regionsError', { error: regionsError })}</p>
          ) : regions === null ? (
            <p className="text-sm text-text-soft">{t('common.loading')}</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {regions.map((region) => (
                <Tag
                  key={region.regionId}
                  active={regionIds.includes(region.regionId)}
                  onClick={() => toggleRegion(region.regionId)}
                >
                  {region.name}
                </Tag>
              ))}
            </div>
          )}
        </Field>

        <Field label={t('recommendInput.interests')} hint={t('recommendInput.minOne')}>
          <div className="flex flex-wrap gap-2">
            {INTEREST_OPTIONS.map((opt) => (
              <Tag key={opt.value} active={interests.includes(opt.value)} onClick={() => toggleInterest(opt.value)}>
                {t(opt.labelKey)}
              </Tag>
            ))}
          </div>
        </Field>

        <Field label={t('recommendInput.tripDates')} hint={t('recommendInput.requiredMark')}>
          {/*
            캘린더를 항상 펼쳐 두지 않는다 — 480px 폼에서 화면을 통째로 차지해 나머지 선택이
            스크롤 아래로 밀린다. 두 칸이 현재 값을 보여주고, 누르면 그때 펼친다(프로토타입 동일).
          */}
          <div className="flex items-center gap-2.5">
            <DateSlot label={t('recommendInput.startDate')} value={labelOf(startDate)} onClick={() => setCalendarOpen((v) => !v)} />
            <span aria-hidden="true" className="text-[13px] text-text-soft">
              →
            </span>
            <DateSlot label={t('recommendInput.endDate')} value={labelOf(endDate)} onClick={() => setCalendarOpen((v) => !v)} />
          </div>
          {calendarOpen ? (
            <div className="mt-2.5">
              <DateRangeCalendar
                start={startDate}
                end={endDate}
                onChange={(nextStart, nextEnd) => {
                  setStartDate(nextStart)
                  setEndDate(nextEnd)
                  if (nextEnd) setCalendarOpen(false)
                }}
              />
            </div>
          ) : null}
          {startDate && endDate ? (
            <p className="mt-2 inline-block border border-dashed border-line px-3 py-1 text-[11px] text-text-soft">
              {tripLengthLabel(t, startDate, endDate)}
            </p>
          ) : null}
        </Field>

        <Field label={t('recommendInput.pace')} hint={t('recommendInput.singleSelect')}>
          <div className="flex flex-col gap-2">
            {DIFFICULTY_OPTIONS.map((opt) => (
              <div key={opt.value} className="flex items-center gap-3">
                <Tag active={difficulty === opt.value} onClick={() => setDifficulty(opt.value)}>
                  {t(opt.labelKey)}
                </Tag>
                <span className="text-xs text-text-soft">{t(opt.descKey)}</span>
              </div>
            ))}
          </div>
        </Field>
      </div>

      <div className="mt-2 text-center">
        <Button disabled={!canSubmit} onClick={handleSubmit}>
          {t('recommendInput.submit')}
        </Button>
      </div>
    </div>
  )
}

/** 섹션 하나. 굵은 라벨 옆에 accent 색으로 선택 방식을 알린다(프로토타입 S3). */
function Field({ label, hint, children }: { label: string; hint: string; children: ReactNode }) {
  return (
    <div>
      <p className="mb-2.5 text-sm font-semibold">
        {label} <span className="font-normal text-accent-text">{hint}</span>
      </p>
      {children}
    </div>
  )
}

function DateSlot({ label, value, onClick }: { label: string; value: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex flex-1 flex-col gap-0.5 border border-line bg-bg-elevated px-3.5 py-2.5 text-left transition-colors hover:border-accent"
    >
      <span className="text-[10px] text-text-soft">{label}</span>
      <span className="text-[13px]">{value}</span>
    </button>
  )
}

export default RecommendInput
