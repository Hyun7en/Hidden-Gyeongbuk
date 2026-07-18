// Kakao Maps JS SDK — separate product/global namespace from the Kakao Share
// SDK in kakaoShare.ts (that one is `window.Kakao`, this one is `window.kakao`,
// lowercase). Both reuse the same VITE_KAKAO_MAP_KEY app key. No official TS
// types are published for this SDK, hence the `any` surface here.

declare global {
  interface Window {
    kakao?: any
  }
}

let loadPromise: Promise<any> | null = null

export function loadKakaoMaps(): Promise<any> {
  const key = import.meta.env.VITE_KAKAO_MAP_KEY
  if (!key) return Promise.reject(new Error('VITE_KAKAO_MAP_KEY is not set'))
  if (window.kakao?.maps) return Promise.resolve(window.kakao)

  if (!loadPromise) {
    loadPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      // autoload=false + kakao.maps.load(): the SDK's own recommended pattern
      // to avoid using the maps object before its internal init finishes.
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false&libraries=services`
      script.onload = () => {
        if (!window.kakao) {
          reject(new Error('Kakao Maps SDK loaded but window.kakao is missing'))
          return
        }
        window.kakao.maps.load(() => resolve(window.kakao))
      }
      script.onerror = () => reject(new Error('Failed to load Kakao Maps SDK script'))
      document.head.appendChild(script)
    })
  }
  return loadPromise
}
