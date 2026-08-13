import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import Container from '../components/Container'
import { ButtonLink } from '../components/Button'
import { fetchLanguages, fetchRegions, searchPlaces } from '../api/client'

/**
 * S7 소개. 본문은 정적이고 커버리지 숫자만 API에서 파생한다.
 *
 * 전용 집계 API를 두지 않는다 — 지역 수는 목록 길이, 장소 수는 검색의 `totalElements`,
 * 언어 수는 지원 언어 목록 길이로 나온다(화면 설계서 S7 데이터 바인딩). 숫자 세 개를 위해
 * 엔드포인트를 만들 이유가 없다.
 *
 * <b>집계가 실패하면 이 섹션만 숨긴다</b> — 페이지 전체를 막지 않고, 근거 없는 수를 대신
 * 박아두지도 않는다(S7 엣지 케이스).
 */
interface Coverage {
  regions: number
  places: number
  languages: number
}

function About() {
  const { t } = useTranslation()
  const [coverage, setCoverage] = useState<Coverage | null>(null)

  useEffect(() => {
    let cancelled = false
    // size=1로 첫 페이지만 받아 totalElements만 쓴다 — 전체 목록이 필요하지 않다.
    Promise.all([fetchRegions(), searchPlaces({ page: 0, size: 1 }), fetchLanguages()])
      .then(([regions, places, langs]) => {
        if (cancelled) return
        setCoverage({ regions: regions.length, places: places.totalElements, languages: langs.languages.length })
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [])
  const values = [
    { title: t('about.value1'), desc: t('about.value1Desc') },
    { title: t('about.value2'), desc: t('about.value2Desc') },
    { title: t('about.value3'), desc: t('about.value3Desc') },
  ]

  return (
    <Container className="py-10 md:py-16">
      <h1 className="font-[family-name:var(--font-serif-heading)] text-2xl font-semibold md:text-3xl">
        {t('about.title')}
      </h1>

      <section className="mt-10 max-w-[60ch]">
        <h2 className="font-[family-name:var(--font-serif-heading)] text-xl font-semibold md:text-2xl">
          {t('about.whyTitle')}
        </h2>
        <p className="mt-4 text-sm leading-[1.9] text-text-soft md:text-[15px]">{t('about.why1')}</p>
        <p className="mt-[18px] text-sm leading-[1.9] text-text-soft md:text-[15px]">{t('about.why2')}</p>
        <p className="mt-[18px] text-sm leading-[1.9] text-text-soft md:text-[15px]">{t('about.why3')}</p>
      </section>

      <section className="mt-14">
        <h2 className="font-[family-name:var(--font-serif-heading)] text-xl font-semibold md:text-2xl">
          {t('about.values')}
        </h2>
        <div className="mt-6 grid gap-5 md:grid-cols-3">
          {values.map((v) => (
            <div key={v.title} className="border border-line bg-bg-elevated p-5">
              <p className="text-[15px] font-bold">{v.title}</p>
              <p className="mt-2 text-sm leading-[1.8] text-text-soft">{v.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {coverage && (
        <section className="mt-14">
          <h2 className="font-[family-name:var(--font-serif-heading)] text-xl font-semibold md:text-2xl">
            {t('about.coverage')}
          </h2>
          <div className="mt-6 flex flex-col gap-6 md:flex-row md:gap-16">
            {[
              { value: coverage.regions, label: t('about.regions') },
              { value: coverage.places, label: t('about.places') },
              { value: coverage.languages, label: t('about.languages') },
            ].map((x) => (
              <div key={x.label}>
                <p className="font-[family-name:var(--font-serif-display)] text-4xl font-extrabold text-accent-text md:text-5xl">
                  {x.value.toLocaleString()}
                </p>
                <p className="mt-1 text-sm text-text-soft">{x.label}</p>
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="mt-14">
        <ButtonLink to="/">{t('about.cta')}</ButtonLink>
      </div>
    </Container>
  )
}

export default About
