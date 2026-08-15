import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Container from '../components/Container'
import Card from '../components/Card'
import CardSkeleton from '../components/CardSkeleton'
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
    <Container className="screen-in py-9 pb-14">
      {/* 사진과 소개를 좌우로 나눈다 — 사진을 전체폭으로 깔면 소개 글이 접힌 화면 아래로 밀린다 */}
      <div className="flex flex-col items-start gap-9 md:flex-row">
        <div
          className="h-[200px] w-full flex-none border border-line bg-bg-elevated bg-cover bg-center md:h-[300px] md:w-[420px]"
          style={region?.images?.[0] ? { backgroundImage: `url(${region.images[0]})` } : undefined}
        />
        <div className="min-w-0 flex-1 md:pt-2">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-[family-name:var(--font-serif-heading)] text-2xl font-semibold md:text-[30px]">
              {region ? region.name : <span className="skeleton inline-block h-7 w-32 align-middle" />}
            </h1>
            {hubName && (
              <span className="rounded-[var(--radius-pill)] border border-line bg-[var(--glass-bg)] px-3 py-1 text-[11px] tracking-[0.08em] text-accent-text">
                {t('region.hubBadge', { hub: hubName })}
              </span>
            )}
          </div>

          {/* 소개 글 — 없으면 섹션째 숨긴다(화면 설계서 S2 엣지 케이스) */}
          {region?.description && (
            <p className="mt-4 max-w-[520px] text-sm leading-[1.8] text-text-soft md:text-[15px]">
              {region.description}
            </p>
          )}

          <div className="mt-5">
            {/* 이 지역이 미리 선택된 채로 추천 입력에 들어간다(ADR-019 결과 ④).
                파라미터 이름은 결과 화면(S4)과 같은 `regions`여야 한다 — 어긋나면 조용히 무시된다. */}
            <ButtonLink to={`/recommend?regions=${regionId}`}>{t('region.cta')}</ButtonLink>
          </div>
        </div>
      </div>

      {/* 대표 관광지 — 0개면 미리보기·더보기를 함께 숨긴다 */}
      {places === null || places.length > 0 ? (
        <section className="mt-10">
          <h2 className="mb-4 text-lg font-semibold">{t('region.highlights')}</h2>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-6">
            {places === null
              ? Array.from({ length: PREVIEW_SIZE }).map((_, i) => <CardSkeleton key={i} />)
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
          <div className="mt-5 text-center">
            <Link to={`/places?regionId=${regionId}`} className="text-[13px] font-semibold text-accent-text hover:text-accent-hover">
              {t('region.more')} →
            </Link>
          </div>
        </section>
      ) : null}
    </Container>
  )
}

export default RegionDetail
