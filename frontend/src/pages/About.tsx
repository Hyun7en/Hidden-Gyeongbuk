import { useTranslation } from 'react-i18next'
import Container from '../components/Container'
import { ButtonLink } from '../components/Button'

/**
 * S7 소개. 정적 페이지 — 커버리지 숫자만 나중에 집계 API로 채운다.
 *
 * 지금은 숫자를 하드코딩하지 않고 섹션 자체를 비워둔다. 근거 없는 수를 화면에 박아두면
 * 실제 값이 붙기 전까지 사실과 다른 정보를 노출하게 된다(화면 설계서 S7 엣지 케이스는
 * 집계 실패 시 해당 섹션만 숨기라고 규정한다).
 */
function About() {
  const { t } = useTranslation()
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

      <div className="mt-14">
        <ButtonLink to="/">{t('about.cta')}</ButtonLink>
      </div>
    </Container>
  )
}

export default About
