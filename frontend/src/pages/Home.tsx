import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Container from '../components/Container'
import FiberBackground from '../components/FiberBackground'
import KeyfretStrip from '../components/KeyfretStrip'
import LanguageSwitch from '../components/LanguageSwitch'
import LatticeDivider from '../components/LatticeDivider'
import { useHubs } from '../hooks/useHubs'
import { useLanguage } from '../context/LanguageContext'

const HUB_PRESENTATION: Record<string, { descEn: string; descKo: string; gradient: string }> = {
  Andong: { descEn: 'Tradition · Nature · Temples', descKo: '전통 · 자연 · 사찰', gradient: 'from-[#2a3d63] to-[#4c5f87]' },
  Dongdaegu: { descEn: 'Daegu gateway · Transfer', descKo: '대구 진입 거점 · 환승', gradient: 'from-[#4c5f87] to-[#7c93c4]' },
  Pohang: { descEn: 'East coast · Sea', descKo: '동해안 · 바다', gradient: 'from-[#6b5f47] to-[#94856a]' },
  'Bukbu Naeryuk': { descEn: 'Inland · Mountains', descKo: '내륙 · 산악', gradient: 'from-[#3d5a4a] to-[#6b8a76]' },
}
const FALLBACK_PRESENTATION = { descEn: '', descKo: '', gradient: 'from-[#6b6153] to-[#a6a08d]' }

function Home() {
  const { t } = useTranslation()
  const { hubs, loading, error } = useHubs()
  const { lang } = useLanguage()
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
            <LanguageSwitch />
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
                {t('home.eyebrow')}
              </p>
              <h1 className="mb-3.5 text-balance text-[34px] font-extrabold leading-[1.16] tracking-[-0.01em] md:text-5xl lg:text-6xl">
                {t('home.heroTitle')}
              </h1>
              <p className="mb-6 max-w-[34ch] text-sm leading-relaxed text-[#d9dfec] md:max-w-[46ch] md:text-base">
                {t('home.heroSubtitle')}
              </p>
              <div className="flex flex-col gap-2.5 sm:flex-row">
                <Link to="/recommend" className="flex h-12 items-center justify-center bg-seal px-[22px] text-[15px] font-bold text-[#f5f1e7] md:h-14 md:px-7 md:text-base">
                  {t('home.ctaRecommend')}
                </Link>
                <Link
                  to="/places"
                  className="flex h-12 items-center justify-center border border-[rgba(245,241,231,0.4)] px-[22px] text-[15px] font-bold text-[#f5f1e7] md:h-14 md:px-7 md:text-base"
                >
                  {t('home.ctaBrowse')}
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
                <p className="mb-2.5 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  {t('home.whereEyebrow')}
                </p>
                <h2 className="mb-3 text-balance text-[21px] font-bold md:text-3xl">{t('home.whereTitle')}</h2>
                <p className="max-w-[42ch] text-sm leading-[1.7] text-text-soft md:text-base">{t('home.whereBody')}</p>
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
                  {t('home.mapPin')}
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
                <p className="mb-2.5 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                  {t('home.whyEyebrow')}
                </p>
                <h2 className="mb-3 text-balance text-[21px] font-bold md:text-3xl">{t('home.whyTitle')}</h2>
                <p className="max-w-[42ch] text-sm leading-[1.7] text-text-soft md:text-base">{t('home.whyBody')}</p>
              </div>
              <div className="mt-5 md:mt-0">
                <div className="flex gap-2.5 md:gap-4">
                  <div className="flex-1 border border-line bg-bg-elevated p-3.5 md:p-6">
                    <div className="text-2xl font-extrabold tabular-nums text-accent md:text-4xl">15</div>
                    <div className="mt-0.5 text-[11px] leading-[1.4] text-text-soft md:text-sm">{t('home.statCounties')}</div>
                  </div>
                  <div className="flex-1 border border-line bg-bg-elevated p-3.5 md:p-6">
                    <div className="text-2xl font-extrabold tabular-nums text-accent md:text-4xl">4</div>
                    <div className="mt-0.5 text-[11px] leading-[1.4] text-text-soft md:text-sm">{t('home.statHubs')}</div>
                  </div>
                </div>
                <div className="mt-[18px] inline-flex items-center gap-1.5 text-[11px] font-semibold text-seal md:text-xs">
                  <span className="flex h-4 w-4 shrink-0 items-center justify-center bg-seal text-[9px] text-bg-elevated">印</span>
                  {t('home.sourcedFrom')}
                </div>
              </div>
            </div>
          </Container>
        </section>

        <LatticeDivider />

        <section>
          <Container className="py-8 pb-10 md:py-16">
            <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">{t('home.startEyebrow')}</p>
            <h2 className="mb-1 text-[21px] font-bold md:text-3xl">{t('home.startTitle')}</h2>
            <p className="mb-[18px] text-[13px] text-text-soft md:mb-8 md:text-base">{t('home.startSubtitle')}</p>
            {error ? (
              <p className="text-sm text-text-soft">{t('home.hubsError', { error })}</p>
            ) : loading ? (
              <p className="text-sm text-text-soft">{t('home.loadingHubs')}</p>
            ) : (
              <div className="grid grid-cols-2 gap-2.5 md:grid-cols-4 md:gap-5">
                {hubs.map((hub) => {
                  // 프리셋 조회는 항상 nameEn 키로 한다 — 화면 표시 언어와 무관하게 고정된 식별자로 써야 하므로.
                  const presentation = HUB_PRESENTATION[hub.nameEn] ?? FALLBACK_PRESENTATION
                  const hubName = lang === 'ko' ? hub.nameKo : hub.nameEn
                  const hubDesc = lang === 'ko' ? presentation.descKo : presentation.descEn
                  return (
                    <Link
                      key={hub.hubId}
                      to={`/hubs/${hub.hubId}`}
                      className="block border border-line bg-bg-elevated transition-opacity hover:opacity-90"
                    >
                      <div className={`relative h-[84px] bg-gradient-to-br md:h-[160px] ${presentation.gradient}`}>
                        <span className="absolute bottom-2 left-2.5 text-sm font-bold text-[#f5f1e7] md:text-base">{hubName}</span>
                      </div>
                      <div className="px-2.5 pb-3 pt-2 text-[11px] text-text-soft md:px-4 md:pb-4 md:pt-3 md:text-sm">
                        {hubDesc}
                      </div>
                    </Link>
                  )
                })}
              </div>
            )}
          </Container>
        </section>

        <footer className="py-5 pb-7 text-center md:py-8">
          <p className="text-[11px] text-text-soft md:text-xs">{t('home.footer')}</p>
        </footer>
      </div>
    </div>
  )
}

export default Home
