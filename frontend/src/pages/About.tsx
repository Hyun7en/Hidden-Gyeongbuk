import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
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
    // 읽는 화면이라 본문 컨테이너(1180px)가 아니라 760px로 좁힌다 — 한 줄이 길면 눈이 행을 놓친다.
    <div className="screen-in mx-auto flex w-full max-w-[760px] flex-col gap-14 px-4 py-14 pb-16 md:px-0">
      {/* 브랜드 — 소개 화면에서만 이름과 태그라인을 정면에 내건다 */}
      <div className="flex flex-col items-center gap-4 text-center">
        <p className="font-[family-name:var(--font-serif-display)] text-[34px]">Hidden Gyeongbuk</p>
        <p className="text-[15px] text-text-soft">{t('about.tagline')}</p>
      </div>

      <section>
        <h2 className="mb-3.5 font-[family-name:var(--font-serif-heading)] text-xl font-semibold">
          {t('about.whyTitle')}
        </h2>
        <p className="text-sm leading-[1.9] text-text-soft">{t('about.why1')}</p>
        <p className="mt-[18px] text-sm leading-[1.9] text-text-soft">{t('about.why2')}</p>
        <p className="mt-[18px] text-sm leading-[1.9] text-text-soft">{t('about.why3')}</p>
      </section>

      <section>
        <h2 className="mb-4.5 font-[family-name:var(--font-serif-heading)] text-xl font-semibold">
          {t('about.values')}
        </h2>
        <div className="grid gap-5 md:grid-cols-3">
          {values.map((v, i) => (
            <div key={v.title} className="border border-line p-[22px]">
              {/* 번호는 장식이 아니라 읽는 순서를 만든다 — 카드 셋이 나란히 있을 때 눈이 어디부터 갈지 정해준다 */}
              <p className="font-[family-name:var(--font-serif-display)] text-xl text-accent-text">
                {String(i + 1).padStart(2, '0')}
              </p>
              <p className="mt-2.5 text-sm font-semibold">{v.title}</p>
              <p className="mt-1.5 text-xs leading-[1.6] text-text-soft">{v.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {coverage && (
        <section className="grid grid-cols-3 gap-4 border-y border-line py-8 text-center">
          {[
            { value: coverage.regions, label: t('about.regions') },
            { value: coverage.places, label: t('about.places') },
            { value: coverage.languages, label: t('about.languages') },
          ].map((x) => (
            <div key={x.label}>
              <p className="font-[family-name:var(--font-serif-display)] text-4xl">{x.value.toLocaleString()}</p>
              <p className="mt-1.5 text-xs text-text-soft">{x.label}</p>
            </div>
          ))}
        </section>
      )}

      <div className="text-center">
        <ButtonLink to="/">{t('about.cta')}</ButtonLink>
      </div>
    </div>
  )
}

export default About
