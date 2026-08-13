import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import Container from '../components/Container'

interface Section {
  t: string
  b: string[]
}

/** 개정 이력 한 건. 개정이 생기면 i18n의 `versions` 배열에 쌓인다. */
interface Version {
  effective: string
  note: string
}

/**
 * S9 이용약관 / S10 개인정보처리방침. 두 화면은 구조가 같고 본문만 다르다(화면 설계서 S9·S10).
 *
 * <p><b>본문은 초안이다.</b> 서비스가 실제로 무엇을 수집하는지(회원가입 없음, GPS 미수집,
 * 쿠키 미사용, Kakao SDK가 브라우저에서 직접 통신)에 맞춰 작성했으나, 법적 효력을 주장하려면
 * 전문가 검토가 필요하다. 사람이 정해야 하는 자리는 본문에 【확인 필요: …】로 표시돼 있다.
 *
 * <p>개인정보처리방침은 개인정보보호법상 필수 기재사항이 있어 <b>검토 전 배포하면 안 된다</b>.
 */
function LegalPage({ kind }: { kind: 'terms' | 'privacy' }) {
  const { t } = useTranslation()
  const doc = kind === 'terms' ? 'legal.termsDoc' : 'legal.privacyDoc'
  const title = t(`${doc}.title`)
  // 키가 없으면 t()가 키 문자열을 돌려주므로 배열인지 확인하고 쓴다 — 아니면 .map에서 터진다.
  const rawSummary = t(`${doc}.summary`, { returnObjects: true })
  const rawSections = t(`${doc}.sections`, { returnObjects: true })
  const summary: string[] = Array.isArray(rawSummary) ? (rawSummary as string[]) : []
  const sections: Section[] = Array.isArray(rawSections) ? (rawSections as Section[]) : []

  const effectiveDate = t(`${doc}.effectiveDate`)
  const rawVersions = t(`${doc}.versions`, { returnObjects: true })
  const versions: Version[] = Array.isArray(rawVersions) ? (rawVersions as Version[]) : []
  const [historyOpen, setHistoryOpen] = useState(false)

  return (
    <Container className="py-10 md:py-16">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="font-[family-name:var(--font-serif-heading)] text-2xl font-semibold md:text-3xl">{title}</h1>
        <button
          type="button"
          onClick={() => window.print()}
          className="border border-line px-3 py-1.5 text-xs text-text-soft hover:border-accent-hover hover:text-accent-text"
        >
          {t('legal.print')}
        </button>
      </div>
      <p className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-text-soft">
        <span>
          {t('legal.effective')}:{' '}
          {effectiveDate ? effectiveDate : <span className="text-accent-text">{t('legal.draft')}</span>}
        </span>
        {/*
          개정 이력은 표로 펼쳐두지 않는다 — 대부분의 이용자에겐 불필요한 정보라 본문 앞을
          막기만 한다. 필요한 사람만 열어 보게 하고, 보관 자체는 유지한다(화면 설계서 S9).
          개정 전에는 링크 자체를 띄우지 않는다.
        */}
        {versions.length > 0 && (
          <button
            type="button"
            onClick={() => setHistoryOpen((v) => !v)}
            className="text-accent-text underline hover:text-accent-hover"
          >
            {historyOpen ? t('legal.close') : t('legal.prevVersion')}
          </button>
        )}
      </p>
      {!effectiveDate && <p className="mt-1 text-xs text-accent-text">{t('legal.draftNotice')}</p>}

      {historyOpen && (
        <section className="mt-4 border border-line bg-bg-elevated p-4">
          <p className="text-sm font-bold">{t('legal.history')}</p>
          <ul className="mt-3 flex flex-col gap-2 text-sm text-text-soft">
            {versions.map((v) => (
              <li key={v.effective}>
                <span className="text-text">{v.effective}</span> — {v.note}
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* 친화적 요약 — 조항 전문 앞에 핵심만. 법적 효력은 아래 조항에 있다 */}
      <section className="mt-8 border border-line bg-bg-elevated p-5">
        <p className="text-sm font-bold">{t('legal.summary')}</p>
        <ul className="mt-3 flex list-disc flex-col gap-2 pl-5 text-sm leading-[1.8] text-text-soft">
          {summary.map((s) => (
            <li key={s}>{s}</li>
          ))}
        </ul>
        <p className="mt-4 text-xs text-text-soft">{t('legal.summaryNote')}</p>
      </section>

      <div className="mt-10 md:grid md:grid-cols-[220px_1fr] md:gap-10">
        <nav className="mb-8 md:sticky md:top-6 md:mb-0 md:self-start">
          <p className="mb-3 text-[11px] font-bold uppercase tracking-[0.14em] text-accent-text">{t('legal.toc')}</p>
          <ul className="flex flex-col gap-2 text-sm text-text-soft">
            {sections.map((s, i) => (
              <li key={s.t}>
                <a href={`#sec-${i}`} className="hover:text-accent-text">
                  {s.t}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        {/* 접지 않고 전문 노출 — 약관은 찾아 읽는 문서라 펼침 조작을 요구하지 않는다 */}
        <div className="flex max-w-[70ch] flex-col gap-8">
          {sections.map((s, i) => (
            <section key={s.t} id={`sec-${i}`} className="scroll-mt-6">
              <h2 className="text-[15px] font-bold">{s.t}</h2>
              {s.b.map((p) => (
                <p key={p} className="mt-2 text-sm leading-[1.9] text-text-soft">
                  {p}
                </p>
              ))}
            </section>
          ))}
          <p className="border-t border-line pt-6 text-xs text-text-soft">{t('legal.authoritative')}</p>
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
