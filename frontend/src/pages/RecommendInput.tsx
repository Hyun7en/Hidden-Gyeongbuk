import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Container from '../components/Container'
import Tag from '../components/Tag'
import DateRangeCalendar from '../components/DateRangeCalendar'
import { useHubs } from '../hooks/useHubs'
import { useRegions } from '../hooks/useRegions'
import { useLanguage } from '../context/LanguageContext'
import { formatDateRange, tripLengthLabel } from '../lib/tripDates'
import { DIFFICULTY_OPTIONS, INTEREST_OPTIONS, type Difficulty, type Interest } from '../types/course'

// 키는 hub.code다 — 표시명은 요청 언어로 바뀌므로 식별자로 쓸 수 없다.
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
 * S3 추천 입력. 거점 → 날짜 → 지역 → 속도 → 관심사 순으로 좁혀 간다.
 *
 * <p>기간은 칩이 아니라 <b>날짜 범위 캘린더</b>다(ADR-020). 지역은 <b>선택 사항인 다중선택</b>이고,
 * 거점만 고르고 넘어가면 그 거점 주변 전체에서 코스가 짜인다(ADR-019).
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
  // 지역 상세(S2)의 "이 지역 코스 추천받기"가 지역을 미리 채워 보낸다(ADR-019 결과 ④).
  const [regionIds, setRegionIds] = useState<number[]>(() => {
    const raw = searchParams.get('regions')
    return raw ? raw.split(',').map(Number).filter((n) => !Number.isNaN(n)) : []
  })
  const [difficulty, setDifficulty] = useState<Difficulty | null>(null)
  const [interests, setInterests] = useState<Interest[]>([])

  // 거점을 고르면 그 거점 소속 지역이 위로 온다 — 위치 권한 없이 선택을 돕는다(ADR-019 (3)).
  const { regions, error: regionsError } = useRegions(hubId ?? undefined)

  const canSubmit = hubId !== null && startDate !== null && endDate !== null && difficulty !== null

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
      difficulty: difficulty!,
    })
    if (regionIds.length > 0) params.set('regions', regionIds.join(','))
    if (interests.length > 0) params.set('interests', interests.join(','))
    navigate(`/recommend/results?${params.toString()}`)
  }

  const selectedHubAccess = HUB_ACCESS[hubs.find((h) => h.hubId === hubId)?.code ?? '']

  return (
    <div className="relative min-h-screen bg-bg">
      <div className="relative z-10">
        <Container className="py-8 md:py-14">
          <div className="mx-auto max-w-2xl">
            <h1 className="mb-6 text-2xl font-bold md:mb-8 md:text-3xl">{t('recommendInput.title')}</h1>
            <p className="mb-8 text-sm leading-relaxed text-text-soft md:mb-12 md:text-base">
              {t('recommendInput.subtitle')}
            </p>

            <div className="space-y-8 md:space-y-10">
              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  {t('recommendInput.startingFrom')}
                </legend>
                <p className="mb-3 text-[13px] leading-relaxed text-text-soft md:text-sm">
                  {t('recommendInput.startingFromBody')}
                </p>
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
                      <p className="mt-3 border border-line bg-bg-elevated px-3 py-2.5 text-[13px] leading-relaxed text-text-soft md:text-sm">
                        {selectedHubAccess ? selectedHubAccess[lang] : t('recommendInput.gettingThereFallback')}
                      </p>
                    ) : null}
                  </>
                )}
              </fieldset>

              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  {t('recommendInput.tripDates')}
                </legend>
                <div className="border border-line bg-bg-elevated p-3 md:p-4">
                  <DateRangeCalendar
                    start={startDate}
                    end={endDate}
                    onChange={(nextStart, nextEnd) => {
                      setStartDate(nextStart)
                      setEndDate(nextEnd)
                    }}
                  />
                </div>
                {startDate && endDate ? (
                  <p className="mt-3 text-[13px] text-text-soft md:text-sm">
                    {formatDateRange(lang, startDate, endDate)} · {tripLengthLabel(t, startDate, endDate)}
                  </p>
                ) : null}
              </fieldset>

              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  {t('recommendInput.regions')}
                </legend>
                <p className="mb-3 text-[13px] leading-relaxed text-text-soft md:text-sm">
                  {t('recommendInput.regionsBody')}
                </p>
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
              </fieldset>

              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  {t('recommendInput.pace')}
                </legend>
                <div className="flex flex-wrap gap-2">
                  {DIFFICULTY_OPTIONS.map((opt) => (
                    <Tag key={opt.value} active={difficulty === opt.value} onClick={() => setDifficulty(opt.value)}>
                      {t(opt.labelKey)}
                    </Tag>
                  ))}
                </div>
              </fieldset>

              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  {t('recommendInput.interests')}
                </legend>
                <div className="flex flex-wrap gap-2">
                  {INTEREST_OPTIONS.map((opt) => (
                    <Tag key={opt.value} active={interests.includes(opt.value)} onClick={() => toggleInterest(opt.value)}>
                      {t(opt.labelKey)}
                    </Tag>
                  ))}
                </div>
              </fieldset>
            </div>

            <button
              type="button"
              disabled={!canSubmit}
              onClick={handleSubmit}
              className="mt-10 flex h-12 w-full items-center justify-center bg-seal px-[22px] text-[15px] font-bold text-[#f5f1e7] disabled:cursor-not-allowed disabled:opacity-35 md:mt-14 md:h-14 md:w-auto md:px-8 md:text-base"
            >
              {t('recommendInput.submit')}
            </button>
          </div>
        </Container>
      </div>
    </div>
  )
}

export default RecommendInput
