import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import Container from '../components/Container'
import Card from '../components/Card'
import { ButtonLink } from '../components/Button'
import { useRegions } from '../hooks/useRegions'

/**
 * S1 홈 — 지역 카탈로그(ADR-019).
 *
 * 구 구조(진입 카드 2개 + Hub 소개)는 폐기됐다. 첫 화면에서 서비스의 콘텐츠인 15개 지역을
 * 그대로 드러내고, 추천은 히어로 버튼이 담당한다.
 *
 * <b>고정 열 그리드를 쓰지 않는다</b> — 15개 지역을 추천/강조 구획 없이 동등하게 한 줄로
 * 나열한다. 모바일은 스와이프, 데스크톱은 화살표로 묶음 이동한다(Wireframe S1).
 */
function Home() {
  const { t } = useTranslation()
  const { regions, loading, error } = useRegions()
  const trackRef = useRef<HTMLDivElement>(null)

  function scrollBy(dir: 1 | -1) {
    const el = trackRef.current
    if (!el) return
    // 자유 스크롤이 아니라 카드 묶음 단위로 넘긴다 — 보이는 폭만큼 이동.
    el.scrollBy({ left: dir * el.clientWidth, behavior: 'smooth' })
  }

  return (
    <>
      {/*
        히어로. 설계는 영상(포스터 폴백)이지만 아직 에셋이 없어 그라데이션으로 자리를 잡아뒀다
        — 프로토타입도 같은 자리에 HERO VIDEO / POSTER 플레이스홀더를 둔다.
        화면을 꽉 채우지 않아 아래 지역 섹션이 살짝 걸쳐 보이게(peek) 높이를 잡는다.
      */}
      <section className="relative h-[72vh] max-h-[560px] min-h-[380px] overflow-hidden bg-[linear-gradient(160deg,#8c8577,#5f5b52)]">
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-8 px-6 text-center">
          <h1
            className="font-[family-name:var(--font-serif-display)] font-extrabold leading-[1.16] text-white [text-shadow:0_2px_18px_rgba(0,0,0,.45)]"
            style={{ fontSize: 'clamp(var(--fs-hero-mobile), 6vw, 56px)' }}
          >
            &ldquo;{t('home.heroCopy')}&rdquo;
          </h1>
          <ButtonLink to="/recommend">{t('home.heroCta')}</ButtonLink>
        </div>
      </section>

      <Container className="py-8 md:py-12">
        <h2 className="px-2 pb-4 text-lg font-semibold">
          {t('home.allRegions')}
          {regions && <span className="ml-2 text-sm font-normal text-text-soft">({regions.length})</span>}
        </h2>

        {error ? (
          <p className="px-2 text-sm text-text-soft">{t('home.regionsError')}</p>
        ) : (
          <div className="flex items-center gap-3 md:gap-4">
            <button
              type="button"
              aria-label="이전"
              onClick={() => scrollBy(-1)}
              className="hidden h-10 w-10 flex-none items-center justify-center rounded-full bg-[var(--glass-bg-strong)] text-[17px] text-text backdrop-blur-[10px] transition-colors hover:bg-[var(--glass-bg)] md:flex"
            >
              &lsaquo;
            </button>

            <div ref={trackRef} className="hg-hscroll flex flex-1 gap-4 overflow-x-auto md:gap-[18px]">
              {loading
                ? Array.from({ length: 5 }).map((_, i) => (
                    <div key={i} className="w-[220px] flex-none border border-line bg-bg-elevated">
                      <div className="h-[180px] animate-pulse bg-line" />
                      <div className="flex flex-col gap-2 p-3.5">
                        <div className="h-3 w-3/5 animate-pulse bg-line" />
                        <div className="h-2.5 w-2/5 animate-pulse bg-line" />
                      </div>
                    </div>
                  ))
                : regions?.map((r) => (
                    <div key={r.regionId} className="flex-none">
                      <Card
                        to={`/regions/${r.regionId}`}
                        title={r.name}
                        subtitle={t('home.regionSub')}
                        imageUrl={r.firstImage}
                      />
                    </div>
                  ))}
            </div>

            <button
              type="button"
              aria-label="다음"
              onClick={() => scrollBy(1)}
              className="hidden h-10 w-10 flex-none items-center justify-center rounded-full bg-[var(--glass-bg-strong)] text-[17px] text-text backdrop-blur-[10px] transition-colors hover:bg-[var(--glass-bg)] md:flex"
            >
              &rsaquo;
            </button>
          </div>
        )}
      </Container>
    </>
  )
}

export default Home
