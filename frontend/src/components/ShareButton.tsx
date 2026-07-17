import { useState } from 'react'
import { useTranslation } from 'react-i18next'

// FR-C-06(코스 URL 공유)은 아직 미구현 — CourseDetail은 router state로만 데이터를 받아서
// 지금 공유하는 URL은 받는 사람 쪽에서 새로고침/새 탭으로 열면 동작하지 않는다.
// 우선 네이티브 공유 시트만 띄우고, 실제로 여는 링크를 만드는 건 별도 작업으로 남겨둔다.
function ShareButton({ title, className }: { title?: string; className?: string }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  async function handleShare() {
    const url = window.location.href
    if (navigator.share) {
      try {
        await navigator.share({ title, url })
      } catch {
        // 사용자가 공유 시트를 취소한 것 — 별도 처리 불필요
      }
      return
    }
    await navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <button
      type="button"
      onClick={handleShare}
      className={
        className ??
        'absolute right-2 top-2 z-10 border border-line bg-bg px-2.5 py-1 text-[11px] font-semibold text-accent shadow-sm md:right-3 md:top-3 md:text-xs'
      }
    >
      {copied ? t('common.copied') : t('common.share')}
    </button>
  )
}

export default ShareButton
