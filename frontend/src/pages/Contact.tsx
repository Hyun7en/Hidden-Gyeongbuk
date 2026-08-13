import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import Container from '../components/Container'
import Tag from '../components/Tag'
import { Button } from '../components/Button'

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
    <Container className="py-10 md:py-16">
      <h1 className="font-[family-name:var(--font-serif-heading)] text-2xl font-semibold md:text-3xl">
        {t('contact.title')}
      </h1>
      <p className="mt-3 max-w-[52ch] text-sm leading-[1.8] text-text-soft">{t('contact.sub')}</p>

      <section className="mt-10">
        <p className="mb-3 text-sm font-bold">{t('contact.type')}</p>
        <div className="flex flex-wrap gap-2">
          {types.map((x) => (
            <Tag key={x.key} active={selected === x.key} onClick={() => setSelected(x.key)}>
              {x.label}
            </Tag>
          ))}
        </div>
      </section>

      <div className="mt-8 flex flex-wrap items-center gap-3">
        <a href={mailto} className="hg-cta hg-btn-desktop inline-flex items-center justify-center">
          {t('contact.send')}
        </a>
        <Button onClick={copyEmail}>{copied ? t('contact.copied') : t('contact.copy')}</Button>
      </div>
      <p className="mt-3 text-xs text-text-soft">{EMAIL}</p>

      {/* FAQ — 문의 상당수를 여기서 자체 해결해 응답 부담을 줄인다(화면 설계서 S8).
          여러 개를 동시에 열 수 있다 — 서로 비교하며 읽는 편이 자연스럽다. */}
      <section className="mt-14">
        <h2 className="mb-4 text-lg font-semibold">{t('contact.faq')}</h2>
        <div className="border-t border-line">
          {[1, 2, 3, 4].map((i) => (
            <details key={i} className="border-b border-line">
              <summary className="cursor-pointer list-none py-4 text-sm font-medium marker:hidden hover:text-accent-text">
                {t(`contact.q${i}`)}
              </summary>
              <p className="pb-4 text-sm leading-[1.8] text-text-soft">{t(`contact.a${i}`)}</p>
            </details>
          ))}
        </div>
      </section>
    </Container>
  )
}

export default Contact
