import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { searchPlaces } from '../api/client'
import { translateTag } from '../api/labels'
import { useLanguage } from '../context/LanguageContext'
import type { PlaceSummaryResponse } from '../api/types'
import BackHeader from '../components/BackHeader'
import Container from '../components/Container'
import FiberBackground from '../components/FiberBackground'
import PlaceCard from '../components/PlaceCard'
import Tag from '../components/Tag'
import { INTEREST_OPTIONS, type Interest } from '../types/course'

function PlaceExplore() {
  const { t } = useTranslation()
  const { lang } = useLanguage()
  const [activeTag, setActiveTag] = useState<Interest | null>(null)
  const [keyword, setKeyword] = useState('')
  const [places, setPlaces] = useState<PlaceSummaryResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setError(null)
    // cancelled 없이 setTimeout만 지우면, 이미 발사된 이전 요청이 늦게 도착해
    // 최신 필터의 결과를 stale 응답으로 덮어쓸 수 있다.
    let cancelled = false
    const handle = setTimeout(() => {
      searchPlaces({ tag: activeTag ?? undefined, keyword: keyword || undefined }, lang)
        .then((res) => {
          if (!cancelled) setPlaces(res.content)
        })
        .catch((err: Error) => {
          if (!cancelled) setError(err.message)
        })
    }, 250)
    return () => {
      cancelled = true
      clearTimeout(handle)
    }
  }, [activeTag, keyword, lang])

  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10">
        <BackHeader backTo="/" />
        <Container className="py-8 md:py-14">
          <h1 className="mb-6 text-2xl font-bold md:mb-8 md:text-3xl">{t('placeExplore.title')}</h1>
          <input
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder={t('placeExplore.searchPlaceholder') ?? undefined}
            className="mb-4 h-11 w-full border border-line bg-bg-elevated px-3 text-sm text-text placeholder:text-text-soft md:h-12 md:max-w-sm md:text-base"
          />
          <div className="mb-8 flex flex-wrap gap-2 md:mb-10">
            <Tag active={activeTag === null} onClick={() => setActiveTag(null)}>
              {t('placeExplore.all')}
            </Tag>
            {INTEREST_OPTIONS.map((opt) => (
              <Tag key={opt.value} active={activeTag === opt.value} onClick={() => setActiveTag(opt.value)}>
                {t(opt.labelKey)}
              </Tag>
            ))}
          </div>
          {error ? (
            <p className="text-sm text-text-soft">{t('placeExplore.error', { error })}</p>
          ) : places === null ? (
            <p className="text-sm text-text-soft">{t('common.loading')}</p>
          ) : places.length === 0 ? (
            <p className="text-sm text-text-soft">{t('placeExplore.empty')}</p>
          ) : (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3 md:gap-6 lg:grid-cols-4">
              {places.map((place) => (
                <PlaceCard
                  key={place.placeId}
                  place={{
                    placeId: place.placeId,
                    title: place.title,
                    addr: place.addr,
                    tags: lang === 'en' ? place.tags.map(translateTag) : place.tags,
                    hasImage: Boolean(place.firstImage),
                  }}
                />
              ))}
            </div>
          )}
        </Container>
      </div>
    </div>
  )
}

export default PlaceExplore
