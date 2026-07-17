import { Link } from 'react-router-dom'
import Container from '../components/Container'
import FiberBackground from '../components/FiberBackground'
import KeyfretStrip from '../components/KeyfretStrip'
import LatticeDivider from '../components/LatticeDivider'
import { useHubs } from '../hooks/useHubs'

const HUB_PRESENTATION: Record<string, { desc: string; gradient: string }> = {
  Andong: { desc: 'Tradition · Nature · Temples', gradient: 'from-[#2a3d63] to-[#4c5f87]' },
  Dongdaegu: { desc: 'Daegu gateway · Transfer', gradient: 'from-[#4c5f87] to-[#7c93c4]' },
  Pohang: { desc: 'East coast · Sea', gradient: 'from-[#6b5f47] to-[#94856a]' },
  'Bukbu Naeryuk': { desc: 'Inland · Mountains', gradient: 'from-[#3d5a4a] to-[#6b8a76]' },
}
const FALLBACK_PRESENTATION = { desc: '', gradient: 'from-[#6b6153] to-[#a6a08d]' }

function Home() {
  const { hubs, loading, error } = useHubs()
  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />

      <div className="relative z-10">
        <header className="sticky top-0 z-10 bg-bg-elevated">
          <Container className="flex items-center justify-between py-4 md:py-5">
            <div className="flex items-center gap-2">
              <span className="h-4 w-4 shrink-0 bg-seal md:h-5 md:w-5" />
              <span className="text-[15px] font-extrabold tracking-[-0.01em] md:text-lg">Hidden Gyeongbuk</span>
            </div>
            <div className="flex border border-line">
              <button type="button" aria-current="true" className="bg-accent px-[9px] py-[5px] text-[11px] font-bold text-bg-elevated md:px-3 md:py-1.5 md:text-xs">
                EN
              </button>
              <button type="button" className="px-[9px] py-[5px] text-[11px] font-bold text-text-soft md:px-3 md:py-1.5 md:text-xs">
                KO
              </button>
            </div>
          </Container>
        </header>
        <KeyfretStrip />

        <section
          className="text-[#f5f1e7]"
          style={{
            background:
              'radial-gradient(circle at 15% 0%, rgba(178,58,46,0.10), transparent 55%), linear-gradient(165deg, var(--accent) 0%, #1c2942 100%)',
          }}
        >
          <Container className="py-10 md:py-20 lg:py-28">
            <div className="max-w-2xl">
              <p className="mb-3.5 text-xs font-bold uppercase tracking-[0.24em] text-[#c9d3e6] md:text-sm">
                Gyeongsangbuk-do, South Korea
              </p>
              <h1 className="mb-3.5 text-balance text-[34px] font-extrabold leading-[1.16] tracking-[-0.01em] md:text-5xl lg:text-6xl">
                The Korea beyond Seoul is fifteen quiet counties wide.
              </h1>
              <p className="mb-6 max-w-[34ch] text-sm leading-relaxed text-[#d9dfec] md:max-w-[46ch] md:text-base">
                Hub-and-spoke day trips and overnight courses built from real local data — temples, markets, mountains
                no guidebook covers.
              </p>
              <div className="flex flex-col gap-2.5 sm:flex-row">
                <Link to="/recommend" className="flex h-12 items-center justify-center bg-seal px-[22px] text-[15px] font-bold text-[#f5f1e7] md:h-14 md:px-7 md:text-base">
                  Get a course recommendation →
                </Link>
                <Link
                  to="/places"
                  className="flex h-12 items-center justify-center border border-[rgba(245,241,231,0.4)] px-[22px] text-[15px] font-bold text-[#f5f1e7] md:h-14 md:px-7 md:text-base"
                >
                  Browse places
                </Link>
              </div>
            </div>
          </Container>
        </section>

        <LatticeDivider />

        <section>
          <Container className="py-9 md:py-16">
            <div className="md:grid md:grid-cols-2 md:items-center md:gap-16">
              <div>
                <p className="mb-2.5 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">Where is this</p>
                <h2 className="mb-3 text-balance text-[21px] font-bold md:text-3xl">Gyeongsangbuk-do sits inland, east of Daegu.</h2>
                <p className="max-w-[42ch] text-sm leading-[1.7] text-text-soft md:text-base">
                  Korea's largest province by area, wrapping the country's southeastern coast and mountainous interior.
                  Most visitors pass through on the KTX without stopping — this app is built around four transit hubs
                  you can actually reach.
                </p>
              </div>
              <div className="relative mt-5 h-[132px] overflow-hidden border border-line bg-bg-elevated md:mt-0 md:h-[260px]">
                <svg viewBox="0 0 390 132" preserveAspectRatio="xMidYMid meet" className="block h-full w-full">
                  <rect width="390" height="132" fill="none" />
                  <path
                    d="M170 10 L230 22 L255 55 L245 95 L280 115 L230 128 L175 120 L140 90 L120 60 L135 30 Z"
                    fill="var(--accent)"
                    opacity={0.16}
                    stroke="var(--accent)"
                    strokeWidth={1.5}
                  />
                  <circle cx={180} cy={55} r={4} fill="var(--seal)" />
                  <circle cx={205} cy={70} r={3} fill="var(--accent)" />
                  <circle cx={230} cy={45} r={3} fill="var(--accent)" />
                  <circle cx={215} cy={100} r={3} fill="var(--accent)" />
                </svg>
                <span className="absolute bottom-2.5 left-3 border border-line bg-bg px-2 py-[3px] text-[11px] text-text-soft">
                  ● Andong Hub
                </span>
              </div>
            </div>
          </Container>
        </section>

        <LatticeDivider />

        <section>
          <Container className="py-9 md:py-16">
            <div className="md:grid md:grid-cols-2 md:items-center md:gap-16">
              <div>
                <p className="mb-2.5 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">Why these 15 counties</p>
                <h2 className="mb-3 text-balance text-[21px] font-bold md:text-3xl">Officially declining. Still worth the detour.</h2>
                <p className="max-w-[42ch] text-sm leading-[1.7] text-text-soft md:text-base">
                  The Korean government designates counties losing population fastest as "population-declining
                  regions." All 15 in Gyeongsangbuk-do are here — not because they're empty, but because they're where
                  local life hasn't been rebuilt for tourists yet.
                </p>
              </div>
              <div className="mt-5 md:mt-0">
                <div className="flex gap-2.5 md:gap-4">
                  <div className="flex-1 border border-line bg-bg-elevated p-3.5 md:p-6">
                    <div className="text-2xl font-extrabold tabular-nums text-accent md:text-4xl">15</div>
                    <div className="mt-0.5 text-[11px] leading-[1.4] text-text-soft md:text-sm">counties covered</div>
                  </div>
                  <div className="flex-1 border border-line bg-bg-elevated p-3.5 md:p-6">
                    <div className="text-2xl font-extrabold tabular-nums text-accent md:text-4xl">4</div>
                    <div className="mt-0.5 text-[11px] leading-[1.4] text-text-soft md:text-sm">transit hubs</div>
                  </div>
                </div>
                <div className="mt-[18px] inline-flex items-center gap-1.5 text-[11px] font-semibold text-seal md:text-xs">
                  <span className="flex h-4 w-4 shrink-0 items-center justify-center bg-seal text-[9px] text-bg-elevated">印</span>
                  Sourced from public tourism data (TourAPI)
                </div>
              </div>
            </div>
          </Container>
        </section>

        <LatticeDivider />

        <section>
          <Container className="py-8 pb-10 md:py-16">
            <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">Start from</p>
            <h2 className="mb-1 text-[21px] font-bold md:text-3xl">Four hubs, fifteen spokes</h2>
            <p className="mb-[18px] text-[13px] text-text-soft md:mb-8 md:text-base">Pick the transit hub you're arriving from.</p>
            {error ? (
              <p className="text-sm text-text-soft">Couldn't load hubs — is the backend running? ({error})</p>
            ) : loading ? (
              <p className="text-sm text-text-soft">Loading hubs…</p>
            ) : (
              <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4 md:gap-5">
                {hubs.map((hub) => {
                  const presentation = HUB_PRESENTATION[hub.nameEn] ?? FALLBACK_PRESENTATION
                  return (
                    <Link
                      key={hub.hubId}
                      to={`/hubs/${hub.hubId}`}
                      className="block border border-line bg-bg-elevated transition-opacity hover:opacity-90"
                    >
                      <div className={`relative h-[84px] bg-gradient-to-br md:h-[160px] ${presentation.gradient}`}>
                        <span className="absolute bottom-2 left-2.5 text-sm font-bold text-[#f5f1e7] md:text-base">{hub.nameEn}</span>
                      </div>
                      <div className="px-2.5 pb-3 pt-2 text-[11px] text-text-soft md:px-4 md:pb-4 md:pt-3 md:text-sm">
                        {presentation.desc}
                      </div>
                    </Link>
                  )
                })}
              </div>
            )}
          </Container>
        </section>

        <footer className="py-5 pb-7 text-center md:py-8">
          <p className="text-[11px] text-text-soft md:text-xs">Hidden Gyeongbuk · Built from Korea Tourism Organization open data</p>
        </footer>
      </div>
    </div>
  )
}

export default Home
