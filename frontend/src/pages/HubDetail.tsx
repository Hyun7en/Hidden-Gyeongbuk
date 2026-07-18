import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { fetchRegions } from '../api/client'
import { useHubs } from '../hooks/useHubs'
import { useLanguage } from '../context/LanguageContext'
import type { RegionResponse } from '../api/types'
import BackHeader from '../components/BackHeader'
import Container from '../components/Container'
import FiberBackground from '../components/FiberBackground'
import KakaoMap from '../components/KakaoMap'

function HubDetail() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { hubId } = useParams()
  const { hubs, loading: hubsLoading } = useHubs()
  const { lang } = useLanguage()
  const [regions, setRegions] = useState<RegionResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const hub = hubs.find((h) => String(h.hubId) === hubId)

  useEffect(() => {
    if (!hubId) return
    setRegions(null)
    setError(null)
    fetchRegions(Number(hubId))
      .then(setRegions)
      .catch((err: Error) => setError(err.message))
  }, [hubId])

  if (hubsLoading || (!error && regions === null)) {
    return (
      <div className="relative min-h-screen bg-bg">
        <FiberBackground />
        <div className="relative z-10">
          <BackHeader backTo="/" />
          <Container className="py-16 text-center">
            <p className="text-sm text-text-soft">{t('common.loading')}</p>
          </Container>
        </div>
      </div>
    )
  }

  if (!hub || error) {
    return (
      <div className="relative min-h-screen bg-bg">
        <FiberBackground />
        <div className="relative z-10">
          <BackHeader backTo="/" />
          <Container className="py-16 text-center">
            <p className="text-sm text-text-soft">
              {error ? t('hubDetail.error', { error }) : t('hubDetail.notFound')}
            </p>
          </Container>
        </div>
      </div>
    )
  }

  const hubName = lang === 'ko' ? hub.nameKo : hub.nameEn
  const markers = [
    { lat: hub.lat, lng: hub.lng, label: hubName, isHub: true },
    ...(regions ?? []).map((r) => ({ lat: r.lat, lng: r.lng, label: lang === 'ko' ? r.nameKo : r.nameEn, isRegion: true })),
  ]

  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10">
        <BackHeader backTo="/" />
        <Container className="py-8 md:py-14">
          <h1 className="mb-3 text-2xl font-bold md:text-3xl">{hubName}</h1>
          <p className="mb-6 max-w-[60ch] text-sm leading-relaxed text-text-soft md:text-base">{hub.description}</p>

          <div className="md:grid md:grid-cols-[1fr_1.2fr] md:gap-16">
            <div>
              <h2 className="mb-3 text-[11px] font-bold uppercase tracking-[0.16em] text-accent md:text-xs">
                {t('hubDetail.regionsHeading', { count: regions?.length ?? 0 })}
              </h2>
              <div className="flex flex-wrap gap-2">
                {(regions ?? []).map((r) => (
                  <span key={r.regionId} className="border border-line px-2.5 py-1 text-[11px] md:text-xs">
                    {lang === 'ko' ? r.nameKo : r.nameEn}
                  </span>
                ))}
              </div>

              <button
                type="button"
                onClick={() => navigate(`/recommend?hub=${hub.hubId}`)}
                className="mt-8 flex h-12 items-center justify-center bg-seal px-[22px] text-[15px] font-bold text-[#f5f1e7] md:h-14 md:px-7 md:text-base"
              >
                {t('home.ctaRecommend')}
              </button>
            </div>

            <div className="mt-8 h-[220px] border border-line md:mt-0 md:h-full">
              <KakaoMap className="h-full w-full" markers={markers} />
            </div>
          </div>
        </Container>
      </div>
    </div>
  )
}

export default HubDetail
