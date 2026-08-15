import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Container from '../components/Container'
import Card from '../components/Card'
import { ButtonLink } from '../components/Button'
import { fetchRegion, searchPlaces } from '../api/client'
import type { PlaceSummaryResponse, RegionDetailResponse } from '../api/types'
import { useLanguage } from '../context/LanguageContext'
import { useHubs } from '../hooks/useHubs'

const PREVIEW_SIZE = 6

/**
 * S2 지역 소개(ADR-019 신규).
 *
 * 소개와 미리보기를 한 페이지에 담고 전체 목록은 "더보기"로 S5에 넘긴다 — 지역 상세가
 * 장소 목록을 중복해서 갖지 않게 하기 위함이다(화면 설계서 S2).
 */
function RegionDetail() {
  const { t } = useTranslation()
  const { lang } = useLanguage()
  const { regionId } = useParams()
  const { hubs } = useHubs()
  const [region, setRegion] = useState<RegionDetailResponse | null>(null)
  const [places, setPlaces] = useState<PlaceSummaryResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!regionId) return
    let cancelled = false
    setRegion(null)
    setPlaces(null)
    setError(null)
    fetchRegion(Number(regionId), lang)
      .then((r) => !cancelled && setRegion(r))
      .catch((e: Error) => !cancelled && setError(e.message))
    searchPlaces({ regionId: Number(regionId), size: PREVIEW_SIZE }, lang)
      .then((res) => !cancelled && setPlaces(res.content))
      .catch(() => !cancelled && setPlaces([]))
    return () => {
      cancelled = true
    }
  }, [regionId, lang])

  if (error) {
    return (
      <Container className="py-16">
        <p className="text-sm text-text-soft">{t('region.error')}</p>
      </Container>
    )
  }

  const hubName = hubs.find((h) => h.hubId === region?.hubId)?.name

  return (
    <Container className="py-8 md:py-14">
      {/* 지역 헤더 — 대표 이미지 + 지역명 + Hub 배지 */}
      <div
        className="h-[200px] border border-line bg-bg-elevated bg-cover bg-center md:h-[300px]"
        style={region?.images?.[0] ? { backgroundImage: `url(${region.images[0]})` } : undefined}
      />
      <div className="mt-5 flex flex-wrap items-baseline gap-3">
        <h1 className="font-[family-name:var(--font-serif-heading)] text-2xl font-semibold md:text-3xl">
          {region ? region.name : <span className="inline-block h-7 w-32 animate-pulse bg-line align-middle" />}
        </h1>
        {hubName && (
          <span className="border border-line px-2.5 py-1 text-[11px] text-text-soft md:text-xs">
            {t('region.hubBadge', { hub: hubName })}
          </span>
        )}
      </div>

      {/* 소개 글 — 없으면 섹션째 숨긴다(화면 설계서 S2 엣지 케이스) */}
      {region?.description && (
        <p className="mt-5 max-w-[60ch] text-sm leading-[1.9] text-text-soft md:text-[15px]">{region.description}</p>
      )}

      <div className="mt-8">
        {/* 이 지역이 미리 선택된 채로 추천 입력에 들어간다(ADR-019 결과 ④).
            파라미터 이름은 결과 화면(S4)과 같은 `regions`여야 한다 — 어긋나면 조용히 무시된다. */}
        <ButtonLink to={`/recommend?regions=${regionId}`}>{t('region.cta')}</ButtonLink>
      </div>

      {/* 대표 관광지 — 0개면 미리보기·더보기를 함께 숨긴다 */}
      {places === null || places.length > 0 ? (
        <section className="mt-14">
          <h2 className="mb-4 text-lg font-semibold">{t('region.highlights')}</h2>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-6">
            {places === null
              ? Array.from({ length: PREVIEW_SIZE }).map((_, i) => (
                  <div key={i} className="border border-line bg-bg-elevated">
                    <div className="h-[140px] animate-pulse bg-line md:h-[180px]" />
                    <div className="flex flex-col gap-2 p-3.5">
                      <div className="h-3 w-3/5 animate-pulse bg-line" />
                      <div className="h-2.5 w-2/5 animate-pulse bg-line" />
                    </div>
                  </div>
                ))
              : places.map((p) => (
                  <Card
                    key={p.placeId}
                    to={`/places/${p.placeId}`}
                    title={p.title ?? ''}
                    subtitle={p.addr}
                    imageUrl={p.firstImage}
                  />
                ))}
          </div>
          <div className="mt-6">
            <Link to={`/places?regionId=${regionId}`} className="text-sm text-accent-text hover:text-accent-hover">
              {t('region.more')} →
            </Link>
          </div>
        </section>
      ) : null}
    </Container>
  )
}

export default RegionDetail
