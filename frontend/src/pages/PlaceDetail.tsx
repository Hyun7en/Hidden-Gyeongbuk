import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { fetchPlaceDetail } from '../api/client'
import { containsHangul, translateCategory, translateTag } from '../api/labels'
import type { PlaceDetailResponse } from '../api/types'
import BackHeader from '../components/BackHeader'
import Container from '../components/Container'
import FiberBackground from '../components/FiberBackground'

function PlaceDetail() {
  const navigate = useNavigate()
  const { placeId } = useParams()
  const [place, setPlace] = useState<PlaceDetailResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!placeId) return
    setPlace(null)
    setError(null)
    fetchPlaceDetail(Number(placeId))
      .then(setPlace)
      .catch((err: Error) => setError(err.message))
  }, [placeId])

  if (error) {
    return (
      <div className="relative min-h-screen bg-bg">
        <FiberBackground />
        <div className="relative z-10">
          <BackHeader onBack={() => navigate(-1)} />
          <Container className="py-16 text-center">
            <p className="text-sm text-text-soft">Couldn't load this place — is the backend running? ({error})</p>
          </Container>
        </div>
      </div>
    )
  }

  if (!place) {
    return (
      <div className="relative min-h-screen bg-bg">
        <FiberBackground />
        <div className="relative z-10">
          <BackHeader onBack={() => navigate(-1)} />
          <Container className="py-16 text-center">
            <p className="text-sm text-text-soft">Loading…</p>
          </Container>
        </div>
      </div>
    )
  }

  const titleKo = place.titleKo !== place.title ? place.titleKo : null
  const translatedAddr = containsHangul(place.addr) ? place.summary?.addrEn : null

  return (
    <div className="relative min-h-screen bg-bg">
      <FiberBackground />
      <div className="relative z-10">
        <BackHeader onBack={() => navigate(-1)} rightSlot="Share" />
        <Container className="py-8 md:py-14">
          <h1 className="mb-6 text-2xl font-bold md:text-3xl">
            {titleKo ? (
              <>
                {place.title} <span className="text-lg font-normal text-text-soft md:text-xl">({titleKo})</span>
              </>
            ) : (
              place.title
            )}
          </h1>
          <div className="md:grid md:grid-cols-[1.2fr_1fr] md:gap-16">
            <div>
              <div
                className="h-[200px] md:h-[320px]"
                style={
                  place.firstImage
                    ? { backgroundImage: `url(${place.firstImage})`, backgroundSize: 'cover', backgroundPosition: 'center' }
                    : {
                        backgroundImage:
                          'repeating-linear-gradient(45deg, var(--line), var(--line) 8px, var(--bg-elevated) 8px, var(--bg-elevated) 16px)',
                      }
                }
              />
              <div className="mt-5 flex flex-wrap gap-1.5">
                <span className="border border-line px-2.5 py-1 text-[11px] uppercase text-text-soft md:text-xs">
                  {translateCategory(place.category)}
                </span>
                {place.tags.map((tag) => (
                  <span key={tag} className="border border-line px-2.5 py-1 text-[11px] md:text-xs">
                    {translateTag(tag)}
                  </span>
                ))}
              </div>
              <p className="mt-4 text-sm text-text-soft md:text-base">
                {translatedAddr ? `${translatedAddr} (${place.addr})` : place.addr}
              </p>

              {place.summary ? (
                <>
                  <div className="mt-6 inline-flex items-center gap-1.5 text-[11px] font-semibold text-seal md:text-xs">
                    <span className="flex h-4 w-4 shrink-0 items-center justify-center bg-seal text-[9px] text-bg-elevated">印</span>
                    Translated from Korean (AI)
                  </div>
                  <p className="mt-2 text-sm leading-relaxed text-text md:text-base">{place.summary.text}</p>
                </>
              ) : place.overview && containsHangul(place.overview) ? (
                <>
                  <p className="mt-6 text-[11px] font-semibold uppercase tracking-[0.05em] text-text-soft md:text-xs">
                    English description not available yet — original Korean text:
                  </p>
                  <p className="mt-2 text-sm leading-relaxed text-text md:text-base" lang="ko">
                    {place.overview}
                  </p>
                </>
              ) : place.overview ? (
                <p className="mt-6 text-sm leading-relaxed text-text md:text-base">{place.overview}</p>
              ) : null}
            </div>

            <div className="mt-8 h-[200px] border border-line bg-bg-elevated md:mt-0 md:h-full">
              <div className="flex h-full items-center justify-center text-[11px] text-text-soft">[ Map ]</div>
            </div>
          </div>
        </Container>
      </div>
    </div>
  )
}

export default PlaceDetail
