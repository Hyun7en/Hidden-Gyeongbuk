import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { fetchNearby, fetchPlaceDetail } from '../api/client'
import { googleDirectionsUrl, kakaoDirectionsUrl } from '../lib/directions'
import { useLanguage } from '../context/LanguageContext'
import type { PlaceDetailResponse, PlaceNearbyResponse } from '../api/types'
import Container from '../components/Container'
import Card from '../components/Card'
import KakaoMap from '../components/KakaoMap'
import ShareButton from '../components/ShareButton'

/**
 * S6 장소 상세.
 *
 * 데스크톱은 좌(이미지·제목·설명) / 우(주소·지도) 2열이다 — 설명이 길어도 우측 고정 정보로
 * 스크롤을 줄이기 위함(화면 설계서 S6).
 *
 * <b>설명은 접지 않는다</b>. P3에게 `overview`가 핵심 콘텐츠이고, 더보기로 감추면 가치가
 * 사라진다(User Persona §4-5).
 */
function PlaceDetail() {
  const { t } = useTranslation()
  const { lang } = useLanguage()
  const { placeId } = useParams()
  const [place, setPlace] = useState<PlaceDetailResponse | null>(null)
  const [nearby, setNearby] = useState<PlaceNearbyResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [photo, setPhoto] = useState(0)
  const [copied, setCopied] = useState(false)
  const touchX = useRef<number | null>(null)

  useEffect(() => {
    if (!placeId) return
    let cancelled = false
    setPlace(null)
    setNearby([])
    setError(null)
    setPhoto(0)
    fetchPlaceDetail(Number(placeId), lang)
      .then((p) => {
        if (cancelled) return
        setPlace(p)
        if (p.lat != null && p.lng != null) {
          fetchNearby({ lat: p.lat, lng: p.lng }, lang)
            .then((n) => !cancelled && setNearby(n.filter((x) => x.place.placeId !== p.placeId)))
            .catch(() => undefined)
        }
      })
      .catch((err: Error) => !cancelled && setError(err.message))
    return () => {
      cancelled = true
    }
  }, [placeId, lang])

  if (error) {
    return (
      <Container className="py-16 text-center">
        <p className="text-sm text-text-soft">{t('placeDetail.error', { error })}</p>
      </Container>
    )
  }

  if (!place) {
    return (
      <Container className="screen-in py-9 pb-14">
        <div className="flex flex-col items-start gap-9 md:flex-row">
          <div className="flex min-w-0 flex-1 flex-col gap-3.5">
            <div className="skeleton h-[220px] md:h-[320px]" />
            <div className="skeleton h-6 w-2/5" />
            <div className="skeleton h-3 w-11/12" />
            <div className="skeleton h-3 w-4/5" />
          </div>
          <div className="flex w-full flex-none flex-col gap-4 md:w-[360px]">
            <div className="skeleton h-[120px]" />
            <div className="skeleton h-[260px]" />
          </div>
        </div>
      </Container>
    )
  }

  const photos = place.images.length > 0 ? place.images : place.firstImage ? [place.firstImage] : []
  const hasPhoto = photos.length > 0
  const showArrows = photos.length > 1

  /**
   * 주소 복사. 국문 주소를 우선한다 — 택시 기사·현지인에게 보여주는 용도라 번역된 주소로는
   * 실제 이동이 안 된다(User Persona §4-1). 국문이 없으면 표시 중인 주소라도 복사한다.
   */
  async function copyLocalAddress() {
    const target = place?.addrLocal ?? place?.addr
    if (!target) return
    await navigator.clipboard.writeText(target)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Container className="screen-in py-9 pb-14">
      {/* 우측은 360px 고정이다 — 비율(1fr)로 두면 창이 넓어질수록 지도·주소 박스가 같이 커진다 */}
      <div className="flex flex-col items-start gap-9 md:flex-row">
        <div className="min-w-0 flex-1">
          {/* 이미지 캐러셀 — 데스크톱은 화살표, 모바일은 스와이프 */}
          <div
            className="relative touch-pan-y"
            onTouchStart={(e) => (touchX.current = e.touches[0]?.clientX ?? null)}
            onTouchEnd={(e) => {
              const start = touchX.current
              const end = e.changedTouches[0]?.clientX
              if (start == null || end == null || !showArrows) return
              if (Math.abs(end - start) < 40) return
              setPhoto((i) => (end < start ? (i + 1) % photos.length : (i - 1 + photos.length) % photos.length))
            }}
          >
            {hasPhoto ? (
              <div
                className="h-[220px] bg-bg-elevated bg-cover bg-center md:h-[320px]"
                style={{ backgroundImage: `url(${photos[photo]})` }}
                role="img"
                aria-label={place.title ?? ''}
              />
            ) : (
              <div className="flex h-[220px] flex-col items-center justify-center gap-2.5 border border-dashed border-line bg-bg-elevated md:h-[320px]">
                <svg width="40" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--fiber)" strokeWidth="1.2" aria-hidden="true">
                  <rect x="3" y="5" width="18" height="14" rx="1" />
                  <path d="M3 16l5-5 4 4 3-3 6 6" strokeLinejoin="round" />
                  <circle cx="8.5" cy="9.5" r="1.2" />
                </svg>
                <span className="text-xs text-text-soft">{t('placeDetail.noPhoto')}</span>
              </div>
            )}

            {showArrows && (
              <>
                <button
                  type="button"
                  aria-label="이전 사진"
                  onClick={() => setPhoto((i) => (i - 1 + photos.length) % photos.length)}
                  className="absolute left-4 top-1/2 hidden h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-[var(--glass-bg-strong)] text-base text-text backdrop-blur-[6px] md:flex"
                >
                  &lsaquo;
                </button>
                <button
                  type="button"
                  aria-label="다음 사진"
                  onClick={() => setPhoto((i) => (i + 1) % photos.length)}
                  className="absolute right-4 top-1/2 hidden h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-[var(--glass-bg-strong)] text-base text-text backdrop-blur-[6px] md:flex"
                >
                  &rsaquo;
                </button>
                <span className="absolute bottom-3.5 right-3.5 bg-[var(--glass-bg-strong)] px-2.5 py-1 text-[11px] text-text backdrop-blur-[6px]">
                  {photo + 1} / {photos.length}
                </span>
              </>
            )}
          </div>

          <h1 className="mt-5 font-[family-name:var(--font-serif-heading)] text-2xl font-semibold md:text-[28px]">
            {place.title}
          </h1>

          {/* 분류 배지는 하나다 — 장소당 place_type이 정확히 하나이고 별도 태그 축은 없다(ADR-024) */}
          <div className="mt-2.5 flex flex-wrap items-center gap-2">
            <span className="hg-tag hg-tag-desktop hg-tag-unselected">{t(`options.interest.${place.placeType}`)}</span>
            {place.festival && (
              <span className="text-xs text-text-soft">
                {t('placeDetail.festival')} · {place.festival.startDate} ~ {place.festival.endDate}
              </span>
            )}
          </div>

          {/*
            설명이 없는 장소가 적지 않다 — 관광공사 원본이 음식점에는 overview를 거의 주지
            않는다(실측: FD 8%, 전체 43%). 자리를 그냥 비워두면 화면이 고장 난 것처럼 보이므로
            없다는 사실을 밝힌다. 추천 점수에서는 데이터 완성도로 이미 감점된다(PlaceCompleteness).
          */}
          {place.overview ? (
            <>
              {place.translationSource === 'TRANSLATE' && (
                <p className="mt-5 inline-block border border-line px-2 py-1 text-[11px] text-text-soft">
                  {t('placeDetail.machineTranslation')}
                </p>
              )}
              <p className="mt-4 max-w-[560px] text-[15px] leading-[1.8] text-text-soft">{place.overview}</p>
            </>
          ) : (
            <p className="mt-4 max-w-[560px] text-[13px] leading-[1.8] text-text-soft/80">
              {t('placeDetail.noOverview')}
            </p>
          )}
        </div>

        {/* 우측: 주소 · 지도 */}
        <div className="flex w-full flex-none flex-col gap-4 md:w-[360px]">
          <div className="border border-line bg-[var(--glass-bg)] p-4">
            <p className="text-[13px] text-text">{place.title}</p>
            {place.addr && <p className="mt-2 text-[13px] text-text-soft">{place.addr}</p>}
            {/*
              현지 표기 주소는 요청 언어 주소와 다를 때만 따로 보여준다 — 국문 요청이면 둘이 같다.
            */}
            {place.addrLocal && place.addrLocal !== place.addr && (
              <div className="mt-3 border-t border-line pt-3">
                <p className="text-[11px] text-text-soft">{t('placeDetail.localAddress')}</p>
                <p className="mt-1 text-[13px] text-text" lang="ko">
                  {place.addrLocal}
                </p>
              </div>
            )}
            {/*
              복사 버튼은 주소가 있으면 <b>항상</b> 낸다. 예전엔 현지 주소 블록 안에 있어서
              국문으로 보거나 현지 주소가 없으면 버튼 자체가 사라졌다 — 정작 주소를 복사해
              택시 기사에게 보여주는 것이 이 화면의 핵심 동작인데(User Persona §4-1).
              복사되는 값은 국문 주소를 우선한다 — 번역된 주소로는 실제 이동이 안 된다.
            */}
            {(place.addrLocal || place.addr) && (
              <div className="mt-3 flex flex-wrap items-center gap-2.5">
                <button
                  type="button"
                  onClick={copyLocalAddress}
                  className="border border-line bg-bg-elevated px-3.5 py-1.5 text-[11px] text-text-soft transition-colors hover:border-accent hover:text-accent-text"
                >
                  {t('placeDetail.copy')}
                </button>
                {copied && <span className="text-[11px] text-accent-text">{t('placeDetail.copied')}</span>}
              </div>
            )}
            {/*
              연락처(화면 설계서 S6 ③). 타입에만 있고 렌더링이 빠져 있던 것을 채웠다.
              whitespace-pre-line인 이유 — 소스가 연락처를 여러 개 줄 때 <br>로 끊어 보내고
              백엔드가 그 자리를 줄바꿈으로 남긴다(MergeWriter.displayableTel). 실측 예:
              1330 관광통역안내 + 축제사무국 번호가 한 칸에 들어온다. 한 줄로 이으면 100자가
              넘어 읽을 수 없다.
            */}
            {place.tel && (
              <p className="mt-3 whitespace-pre-line text-[13px] text-text-soft">{place.tel}</p>
            )}
            {place.lat != null && place.lng != null && (
              // 지도 앱은 둘 다 둔다 — 국내는 카카오가 정확하고, 해외 이용자는 구글만 쓰는 경우가 많다.
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <a
                  href={kakaoDirectionsUrl(place.title ?? '', place.lat, place.lng)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hg-cta hg-btn-mobile inline-flex items-center justify-center px-4 text-[13px]"
                >
                  Kakao Map
                </a>
                <a
                  href={googleDirectionsUrl(place.lat, place.lng)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hg-cta hg-btn-mobile inline-flex items-center justify-center px-4 text-[13px]"
                >
                  Google Maps
                </a>
              </div>
            )}
          </div>

          <div className="relative h-[260px] border border-line bg-bg-elevated">
            <ShareButton title={place.title ?? ''} description={place.overview ?? undefined} imageUrl={place.firstImage ?? undefined} />
            <KakaoMap
              className="h-full w-full"
              markers={place.lat != null && place.lng != null ? [{ lat: place.lat, lng: place.lng, label: place.title ?? '' }] : []}
            />
          </div>
        </div>
      </div>

      {/* 주변 장소 — 0개면 섹션째 숨긴다 */}
      {nearby.length > 0 && (
        <section className="mt-10">
          <h2 className="mb-4 text-lg font-semibold">{t('placeDetail.nearby')}</h2>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-6">
            {nearby.slice(0, 6).map((n) => (
              <Card
                key={n.place.placeId}
                to={`/places/${n.place.placeId}`}
                title={n.place.title ?? ''}
                subtitle={`${n.distanceKm.toFixed(1)}km`}
                imageUrl={n.place.firstImage}
              />
            ))}
          </div>
        </section>
      )}
    </Container>
  )
}

export default PlaceDetail
