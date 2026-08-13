import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import Container from '../components/Container'

/**
 * S9 이용약관 / S10 개인정보처리방침의 공통 골격. 두 화면은 구조가 같고 목차 항목과
 * 하단 링크만 다르다(화면 설계서 S9·S10).
 *
 * <b>원문이 아직 없어 본문은 "준비 중" placeholder다.</b> 특히 개인정보처리방침은
 * 개인정보보호법상 필수 기재사항이 있어 <b>원문 없이 배포하면 안 된다</b> — 화면 설계서가
 * 이용약관보다 우선순위를 높게 잡은 이유다.
 */
function LegalPage({ kind }: { kind: 'terms' | 'privacy' }) {
  const { t } = useTranslation()
  const title = kind === 'terms' ? t('legal.terms') : t('legal.privacy')

  const sections =
    kind === 'terms'
      ? ['제1조 (목적)', '제2조 (정의)', '제3조 (약관의 효력)', '제4조 (서비스의 제공)', '제5조 (면책)', '제6조 (분쟁해결)']
      : ['수집 항목', '수집 목적', '보유 기간', '제3자 제공', '이용자 권리', '쿠키', '개인정보보호책임자']

  return (
    <Container className="py-10 md:py-16">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="font-[family-name:var(--font-serif-heading)] text-2xl font-semibold md:text-3xl">{title}</h1>
        <button type="button" onClick={() => window.print()} className="border border-line px-3 py-1.5 text-xs text-text-soft hover:border-accent-hover hover:text-accent-text">
          {t('legal.print')}
        </button>
      </div>
      <p className="mt-2 text-xs text-text-soft">
        {t('legal.effective')} — <span className="text-accent-text">{t('legal.inPreparation')}</span>
      </p>

      <section className="mt-8 border border-line bg-bg-elevated p-5">
        <p className="text-sm font-bold">{t('legal.summary')}</p>
        <p className="mt-2 text-sm leading-[1.8] text-text-soft">{t('legal.summaryNote')}</p>
      </section>

      <div className="mt-10 md:grid md:grid-cols-[200px_1fr] md:gap-10">
        <nav className="mb-8 md:mb-0">
          <p className="mb-3 text-[11px] font-bold uppercase tracking-[0.14em] text-accent-text">{t('legal.toc')}</p>
          <ul className="flex flex-col gap-2 text-sm text-text-soft">
            {sections.map((s, i) => (
              <li key={s}>
                <a href={`#sec-${i}`} className="hover:text-accent-text">
                  {s}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="flex flex-col gap-8">
          {sections.map((s, i) => (
            <section key={s} id={`sec-${i}`}>
              <h2 className="text-[15px] font-bold">{s}</h2>
              <p className="mt-2 text-sm leading-[1.8] text-text-soft">{t('legal.inPreparation')}</p>
            </section>
          ))}
        </div>
      </div>

      {kind === 'terms' && (
        <div className="mt-12">
          <Link to="/privacy" className="text-sm text-accent-text hover:text-accent-hover">
            {t('legal.privacyLink')}
          </Link>
        </div>
      )}
    </Container>
  )
}

export default LegalPage
