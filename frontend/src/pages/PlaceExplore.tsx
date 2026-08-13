import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { searchPlaces } from '../api/client'
import { useLanguage } from '../context/LanguageContext'
import { useRegions } from '../hooks/useRegions'
import type { PlaceSummaryResponse } from '../api/types'
import Container from '../components/Container'
import Card from '../components/Card'
import Tag from '../components/Tag'
import SearchInput from '../components/SearchInput'
import { INTEREST_OPTIONS, type Interest } from '../types/course'

/** 분류 코드 → 표시 라벨. meta/options로 옮기는 중이라 현재는 i18n 사전을 쓴다. */
const labelKey = (code: string) => `options.interest.${code}`

const PAGE_SIZE = 20

/**
 * S5 장소 목록/검색.
 *
 * <b>필터는 한 줄이다</b> — 구 tag 축이 placeType과 같은 어휘로 합쳐졌다(ADR-024).
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

  function resetFilters() {
    setActiveType(null)
    setKeyword('')
    clearRegion()
  }

  return (
    <Container className="py-8 md:py-14">
      {/* 컨텍스트 바 — 지역 경유로 들어왔으면 그 지역을 보여주고 해제할 수 있게 한다 */}
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold md:text-3xl">
          {regionName ? t('placeExplore.inRegion', { region: regionName }) : t('placeExplore.title')}
        </h1>
        {regionId !== undefined && (
          <button
            type="button"
            onClick={clearRegion}
            className="border border-line px-2.5 py-1 text-xs text-text-soft hover:border-accent-hover hover:text-accent-text"
          >
            {t('placeExplore.clearRegion')} ×
          </button>
        )}
      </div>

      <SearchInput
        value={keyword}
        onChange={setKeyword}
        placeholder={t('placeExplore.searchPlaceholder') ?? undefined}
        size="lg"
        className="mb-5 md:max-w-md"
      />

      <div className="mb-8 flex flex-wrap gap-2 md:mb-10">
        <Tag active={activeType === null} onClick={() => setActiveType(null)}>
          {t('placeExplore.all')}
        </Tag>
        {INTEREST_OPTIONS.map((opt) => (
          <Tag key={opt.value} active={activeType === opt.value} onClick={() => setActiveType(opt.value)}>
            {t(opt.labelKey)}
          </Tag>
        ))}
      </div>

      {error ? (
        <p className="text-sm text-text-soft">{t('placeExplore.error', { error })}</p>
      ) : places === null ? (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-6 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="border border-line bg-bg-elevated">
              <div className="h-[140px] animate-pulse bg-line md:h-[180px]" />
              <div className="flex flex-col gap-2 p-3.5">
                <div className="h-3 w-3/5 animate-pulse bg-line" />
                <div className="h-2.5 w-2/5 animate-pulse bg-line" />
              </div>
            </div>
          ))}
        </div>
      ) : places.length === 0 ? (
        <div className="flex flex-col items-start gap-4">
          <p className="text-sm text-text-soft">{t('placeExplore.empty')}</p>
          <button
            type="button"
            onClick={resetFilters}
            className="border border-line px-3 py-1.5 text-xs text-text hover:border-accent-hover"
          >
            {t('placeExplore.reset')}
          </button>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-6 lg:grid-cols-4">
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
          </div>
          <div ref={sentinelRef} className="h-px" />
          <p className="mt-8 text-center text-xs text-text-soft">
            {loadingMore ? t('common.loading') : !hasMore ? t('placeExplore.endReached') : ''}
          </p>
        </>
      )}
    </Container>
  )
}

export default PlaceExplore
