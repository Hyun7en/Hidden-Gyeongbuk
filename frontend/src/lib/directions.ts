// Deep links only — CLAUDE.md's "자체 라우팅 없음" decision means we never compute
// a route ourselves, just hand coordinates to an app that already has a routing
// engine. Both are plain documented URL schemes, no API key or SDK needed.

export function kakaoDirectionsUrl(name: string, lat: number, lng: number): string {
  return `https://map.kakao.com/link/to/${encodeURIComponent(name)},${lat},${lng}`
}

export function googleDirectionsUrl(lat: number, lng: number): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`
}
