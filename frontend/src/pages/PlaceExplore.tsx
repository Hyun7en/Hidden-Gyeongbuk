import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { searchPlaces } from '../api/client'
import { useLanguage } from '../context/LanguageContext'
import { useRegions } from '../hooks/useRegions'
import type { PlaceSummaryResponse } from '../api/types'
import Container from '../components/Container'
import Card from '../components/Card'
import CardSkeleton from '../components/CardSkeleton'
import Tag from '../components/Tag'
import SearchInput from '../components/SearchInput'
import { INTEREST_OPTIONS, type Interest } from '../types/course'

/** 분류 코드 → 표시 라벨. meta/options로 옮기는 중이라 현재는 i18n 사전을 쓴다. */
const labelKey = (code: string) => `options.interest.${code}`

const PAGE_SIZE = 20

/**
 * S5 장소 목록/검색. 레이아웃은 프로토타입을 따른다 — 제목과 검색창이 한 줄, 그 아래 좌측
 * 220px 필터 사이드바 + 우측 4열 그리드.
 *
 * <p><b>필터를 위가 아니라 옆에 두는 이유</b>: 분류가 9종이라 가로로 늘어놓으면 두 줄을
 * 차지하고, 그만큼 결과가 접힌다. 세로로 세우면 목록을 보면서 필터를 바꿀 수 있다.
 *
 * <p>필터축은 하나다 — 구 tag 축이 placeType과 같은 어휘로 합쳐졌다(ADR-024).
 * 페이징은 버튼이 아니라 <b>무한 스크롤</b>이다(Wireframe S5).
 */
function PlaceExplore() {
  const { t } = useTranslation()
  const { lang } = useLanguage()
  const [searchParams, setSearchParams] = useSearchParams()
  const regionParam = searchParams.get('regionId')
  const regionId = regionParam ? Number(regionParam) : undefined
  const { regions } = useRegions()

  const [activeType, setActiveType] = useState<Interest | null>(null)
  const [keyword, setKeyword] = useState('')
  const [places, setPlaces] = useState<PlaceSummaryResponse[] | null>(null)
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const regionName = regions?.find((r) => r.regionId === regionId)?.name

  // 필터가 바뀌면 첫 페이지부터 다시 받는다. 디바운스는 키워드 때문에 필요하고,
  // cancelled 플래그가 없으면 늦게 도착한 이전 응답이 최신 결과를 덮어쓴다.
  useEffect(() => {
    let cancelled = false
    setError(null)
    setPlaces(null)
    const handle = setTimeout(() => {
      searchPlaces(
        { regionId, placeType: activeType ?? undefined, keyword: keyword || undefined, page: 0, size: PAGE_SIZE },
        lang,
      )
        .then((res) => {
          if (cancelled) return
          setPlaces(res.content)
          setTotal(res.totalElements)
          setPage(0)
        })
        .catch((err: Error) => {
          if (!cancelled) setError(err.message)
        })
    }, 250)
    return () => {
      cancelled = true
      clearTimeout(handle)
    }
  }, [regionId, activeType, keyword, lang])

  const hasMore = places !== null && places.length < total

  const loadMore = useCallback(() => {
    if (!hasMore || loadingMore) return
    setLoadingMore(true)
    const next = page + 1
    searchPlaces(
      { regionId, placeType: activeType ?? undefined, keyword: keyword || undefined, page: next, size: PAGE_SIZE },
      lang,
    )
      .then((res) => {
        setPlaces((prev) => [...(prev ?? []), ...res.content])
        setPage(next)
      })
      .catch(() => undefined)
      .finally(() => setLoadingMore(false))
  }, [hasMore, loadingMore, page, regionId, activeType, keyword, lang])

  // 하단 감지 요소가 보이면 다음 페이지를 붙인다.
  const sentinelRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const el = sentinelRef.current
    if (!el) return
    const io = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) loadMore()
    })
    io.observe(el)
    return () => io.disconnect()
  }, [loadMore])

  function clearRegion() {
    const next = new URLSearchParams(searchParams)
    next.delete('regionId')
    setSearchParams(next)
  }

  return (
    <Container className="screen-in py-6 pb-14">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-[family-name:var(--font-serif-heading)] text-2xl font-semibold">
          {regionName ? t('placeExplore.inRegion', { region: regionName }) : t('placeExplore.title')}
        </h1>
        <SearchInput
          value={keyword}
          onChange={setKeyword}
          placeholder={t('placeExplore.searchPlaceholder') ?? undefined}
          size="md"
          className="w-full md:w-[280px]"
        />
      </div>

      {/* 지역 경유로 들어왔으면 그 지역을 보여주고 해제할 수 있게 한다 */}
      {regionId !== undefined && (
        <button
          type="button"
          onClick={clearRegion}
          className="mt-3 border border-line px-2.5 py-1 text-xs text-text-soft transition-colors hover:border-accent-hover hover:text-accent-text"
        >
          {t('placeExplore.clearRegion')} ×
        </button>
      )}

      <div className="mt-4 flex flex-col items-start gap-5 md:flex-row md:gap-8">
        <aside className="w-full flex-none md:w-[220px]">
          <p className="mb-2.5 text-[13px] font-semibold">{t('placeExplore.placeType')}</p>
          <div className="flex flex-wrap items-start gap-2 md:flex-col">
            <Tag active={activeType === null} onClick={() => setActiveType(null)}>
              {t('placeExplore.all')}
            </Tag>
            {INTEREST_OPTIONS.map((opt) => (
              <Tag key={opt.value} active={activeType === opt.value} onClick={() => setActiveType(opt.value)}>
                {t(opt.labelKey)}
              </Tag>
            ))}
          </div>
        </aside>

        <div className="min-w-0 flex-1">
          {error ? (
            <p className="text-sm text-text-soft">{t('placeExplore.error', { error })}</p>
          ) : places === null ? (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-4 md:gap-5">
              {Array.from({ length: 8 }).map((_, i) => (
                <CardSkeleton key={i} />
              ))}
            </div>
          ) : places.length === 0 ? (
            <p className="py-16 text-center text-sm text-text-soft">{t('placeExplore.empty')}</p>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4 md:grid-cols-4 md:gap-5">
                {places.map((place) => (
                  <Card
                    key={place.placeId}
                    to={`/places/${place.placeId}`}
                    title={place.title ?? ''}
                    subtitle={place.addr}
                    badge={t(labelKey(place.placeType))}
                    imageUrl={place.firstImage}
                  />
                ))}
                {/* 다음 페이지를 기다리는 동안 그리드 안에 자리를 채운다 — 목록이 튀지 않는다 */}
                {loadingMore ? Array.from({ length: 4 }).map((_, i) => <CardSkeleton key={`more-${i}`} />) : null}
              </div>
              <div ref={sentinelRef} className="h-px" />
              {!hasMore && !loadingMore ? (
                <p className="mt-8 text-center text-xs text-text-soft">{t('placeExplore.endReached')}</p>
              ) : null}
            </>
          )}
        </div>
      </div>
    </Container>
  )
}

export default PlaceExplore
