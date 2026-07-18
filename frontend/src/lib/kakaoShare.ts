// Kakao's JS SDK has no npm package — it's loaded from their CDN on first use.
// No SRI `integrity` hash is pinned here: the exact current version/hash isn't
// verifiable from this environment, and a wrong hash would silently block the
// script from loading at all, which is worse than skipping SRI.
const SDK_URL = 'https://t1.kakaocdn.net/kakao_js_sdk/2.7.4/kakao.min.js'

interface KakaoSdk {
  isInitialized: () => boolean
  init: (key: string) => void
  Share: {
    sendDefault: (settings: Record<string, unknown>) => void
  }
}

declare global {
  interface Window {
    Kakao?: KakaoSdk
  }
}

let loadPromise: Promise<KakaoSdk> | null = null

function loadSdk(): Promise<KakaoSdk> {
  if (window.Kakao) return Promise.resolve(window.Kakao)
  if (!loadPromise) {
    loadPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = SDK_URL
      script.crossOrigin = 'anonymous'
      script.onload = () => {
        if (window.Kakao) resolve(window.Kakao)
        else reject(new Error('Kakao SDK loaded but window.Kakao is missing'))
      }
      script.onerror = () => reject(new Error('Failed to load Kakao SDK script'))
      document.head.appendChild(script)
    })
  }
  return loadPromise
}

export async function shareToKakao(params: {
  title: string
  description?: string
  imageUrl?: string
  url: string
}): Promise<void> {
  // Same JS key already used for the Kakao Maps SDK (VITE_KAKAO_MAP_KEY) — Kakao
  // ties it to the app, not the product, so it's valid for Kakao Share too. The
  // "카카오톡 공유" product still needs to be enabled for this app in the Kakao
  // Developers console, separately from Maps.
  const key = import.meta.env.VITE_KAKAO_MAP_KEY
  if (!key) throw new Error('VITE_KAKAO_MAP_KEY is not set')

  const kakao = await loadSdk()
  if (!kakao.isInitialized()) kakao.init(key)

  const link = { mobileWebUrl: params.url, webUrl: params.url }

  if (params.imageUrl) {
    kakao.Share.sendDefault({
      objectType: 'feed',
      content: {
        title: params.title,
        description: params.description,
        imageUrl: params.imageUrl,
        link,
      },
    })
  } else {
    // Feed template requires imageUrl; fall back to the text template when we
    // have no place image (e.g. CourseDetail has no single representative photo).
    kakao.Share.sendDefault({
      objectType: 'text',
      text: params.description ? `${params.title}\n${params.description}` : params.title,
      link,
    })
  }
}
