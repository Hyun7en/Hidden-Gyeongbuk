import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import BackHeader from '../components/BackHeader'
import Container from '../components/Container'
import FiberBackground from '../components/FiberBackground'
import Tag from '../components/Tag'
import { useHubs } from '../hooks/useHubs'
import {
  DIFFICULTY_OPTIONS,
  DURATION_OPTIONS,
  INTEREST_OPTIONS,
  type Difficulty,
  type Duration,
  type Interest,
} from '../types/course'

const HUB_ACCESS: Record<string, string> = {
  Andong: 'Train from Cheongnyangni (Seoul) to Andong Station, about 3h — or express bus from Seoul, about 3.5h.',
  Dongdaegu: 'KTX/SRT from Seoul to Dongdaegu Station, about 1h50m — the fastest way into the region.',
  Pohang: 'Direct KTX from Seoul to Pohang Station, about 2h15m.',
  'Bukbu Naeryuk': 'Train or intercity bus to Yeongju Station, the inland region’s rail gateway, about 3h from Seoul.',
}

function RecommendInput() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { hubs, loading: hubsLoading, error: hubsError } = useHubs()

  const [hubId, setHubId] = useState<number | null>(() => {
    const raw = searchParams.get('hub')
    return raw ? Number(raw) : null
  })
  const [duration, setDuration] = useState<Duration | null>(null)
  const [difficulty, setDifficulty] = useState<Difficulty | null>(null)
  const [interests, setInterests] = useState<Interest[]>([])

  const canSubmit = hubId !== null && duration !== null && difficulty !== null

  function toggleInterest(value: Interest) {
    setInterests((prev) => (prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]))
  }

  function handleSubmit() {
    if (!canSubmit) return
    const params = new URLSearchParams({ hub: String(hubId), duration: duration!, difficulty: difficulty! })
    if (interests.length > 0) params.set('interests', interests.join(','))
    navigate(`/recommend/results?${params.toString()}`)
  }

  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10">
        <BackHeader title="Course Recommendation" backTo="/" />

        <Container className="py-8 md:py-14">
          <div className="mx-auto max-w-2xl">
            <p className="mb-8 text-sm leading-relaxed text-text-soft md:mb-12 md:text-base">
              Tell us what you're after — we'll match it against real place data for your hub and build a course
              around it.
            </p>

            <div className="space-y-8 md:space-y-10">
              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  Starting from
                </legend>
                <p className="mb-3 text-[13px] leading-relaxed text-text-soft md:text-sm">
                  Hidden Gyeongbuk is hub-and-spoke: these four are the transit points you can actually reach from
                  Seoul or Busan. Every course starts and ends at the hub you pick — the 15 counties fan out from
                  there.
                </p>
                {hubsError ? (
                  <p className="text-sm text-text-soft">Couldn't load hubs — is the backend running? ({hubsError})</p>
                ) : hubsLoading ? (
                  <p className="text-sm text-text-soft">Loading hubs…</p>
                ) : (
                  <>
                    <div className="flex flex-wrap gap-2">
                      {hubs.map((hub) => (
                        <Tag key={hub.hubId} active={hubId === hub.hubId} onClick={() => setHubId(hub.hubId)}>
                          {hub.nameEn}
                        </Tag>
                      ))}
                    </div>
                    {hubId !== null ? (
                      <p className="mt-3 border border-line bg-bg-elevated px-3 py-2.5 text-[13px] leading-relaxed text-text-soft md:text-sm">
                        {HUB_ACCESS[hubs.find((h) => h.hubId === hubId)?.nameEn ?? ''] ??
                          'Getting there: check the nearest KTX/SRT station or intercity bus terminal for this hub.'}
                      </p>
                    ) : null}
                  </>
                )}
              </fieldset>

              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  Trip length
                </legend>
                <div className="flex flex-wrap gap-2">
                  {DURATION_OPTIONS.map((opt) => (
                    <Tag key={opt.value} active={duration === opt.value} onClick={() => setDuration(opt.value)}>
                      {opt.label}
                    </Tag>
                  ))}
                </div>
              </fieldset>

              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  Pace
                </legend>
                <div className="flex flex-wrap gap-2">
                  {DIFFICULTY_OPTIONS.map((opt) => (
                    <Tag key={opt.value} active={difficulty === opt.value} onClick={() => setDifficulty(opt.value)}>
                      {opt.label}
                    </Tag>
                  ))}
                </div>
              </fieldset>

              <fieldset>
                <legend className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  Interests (optional, pick any)
                </legend>
                <div className="flex flex-wrap gap-2">
                  {INTEREST_OPTIONS.map((opt) => (
                    <Tag key={opt.value} active={interests.includes(opt.value)} onClick={() => toggleInterest(opt.value)}>
                      {opt.label}
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
              Get recommendations →
            </button>
          </div>
        </Container>
      </div>
    </div>
  )
}

export default RecommendInput
