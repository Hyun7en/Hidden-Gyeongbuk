import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import Tag from '../components/Tag'

const EMAIL = import.meta.env.VITE_CONTACT_EMAIL ?? 'hidden.gyeongbuk@gmail.com'

/**
 * S8 문의. 폼 없이 mailto 링크로만 구성한다 — 솔로 개발 구조에서 폼·수신 서버를 두는 것은
 * 과잉이라고 판단했다(화면 설계서 S8).
 *
 * 메일 앱이 없는 기기에서는 mailto가 아무 반응이 없으므로 <b>주소 복사 버튼을 함께 둔다</b>.
 */
function Contact() {
  const { t } = useTranslation()
  const types = [
    { key: 'bug', label: t('contact.bug') },
    { key: 'translation', label: t('contact.translation') },
    { key: 'partnership', label: t('contact.partnership') },
    { key: 'etc', label: t('contact.etc') },
  ]
  const [selected, setSelected] = useState(types[0].key)
  const [copied, setCopied] = useState(false)

  const subject = types.find((x) => x.key === selected)?.label ?? ''
  const mailto = `mailto:${EMAIL}?subject=${encodeURIComponent(`[Hidden Gyeongbuk] ${subject}`)}`

  async function copyEmail() {
    await navigator.clipboard.writeText(EMAIL)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    // 문의는 읽고 한 번 누르는 화면이라 640px로 좁힌다.
    <div className="screen-in mx-auto flex w-full max-w-[640px] flex-col gap-9 px-4 py-14 pb-16 md:px-0">
      <div className="text-center">
        <h1 className="font-[family-name:var(--font-serif-heading)] text-[26px] font-semibold">
          {t('contact.title')}
        </h1>
        <p className="mt-2.5 text-[13px] leading-relaxed text-text-soft">{t('contact.sub')}</p>
      </div>

      <section>
        <p className="mb-2.5 text-sm font-semibold">{t('contact.type')}</p>
        <div className="flex flex-wrap gap-2">
          {types.map((x) => (
            <Tag key={x.key} active={selected === x.key} onClick={() => setSelected(x.key)}>
              {x.label}
            </Tag>
          ))}
        </div>
      </section>

      {/* 메일 앱이 없는 기기에서는 mailto가 아무 반응이 없다 — 주소와 복사 버튼을 한 상자에 둔다 */}
      <div className="flex flex-wrap items-center gap-3">
        <a href={mailto} className="hg-cta hg-btn-desktop inline-flex items-center justify-center">
          {t('contact.send')}
        </a>
        <div className="flex flex-wrap items-center gap-2.5 border border-line bg-bg-elevated px-4 py-2.5">
          <span className="text-[13px]">{EMAIL}</span>
          <button
            type="button"
            onClick={copyEmail}
            className="border border-line bg-bg-elevated px-3 py-1.5 text-[11px] text-text-soft transition-colors hover:border-accent"
          >
            {t('contact.copy')}
          </button>
          {copied && <span className="text-[11px] text-accent-text">{t('contact.copied')}</span>}
        </div>
      </div>

      {/* FAQ — 문의 상당수를 여기서 자체 해결해 응답 부담을 줄인다(화면 설계서 S8).
          여러 개를 동시에 열 수 있다 — 서로 비교하며 읽는 편이 자연스럽다. */}
      <section>
        <h2 className="mb-3 text-sm font-semibold">{t('contact.faq')}</h2>
        {[1, 2, 3, 4].map((i) => (
          <details key={i} className="group border-t border-line">
            <summary className="flex cursor-pointer list-none items-center justify-between py-4 text-sm marker:hidden hover:text-accent-text">
              {t(`contact.q${i}`)}
              <span aria-hidden="true" className="text-xs text-text-soft group-open:hidden">
                ▸
              </span>
              <span aria-hidden="true" className="hidden text-xs text-text-soft group-open:inline">
                ▾
              </span>
            </summary>
            <p className="pb-4 text-[13px] leading-[1.7] text-text-soft">{t(`contact.a${i}`)}</p>
          </details>
        ))}
      </section>
    </div>
  )
}

export default Contact
