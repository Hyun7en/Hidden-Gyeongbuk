// Deep links only — CLAUDE.md's "자체 라우팅 없음" decision means we never compute
// a route ourselves, just hand coordinates to an app that already has a routing
// engine. Both are plain documented URL schemes, no API key or SDK needed.

export function kakaoDirectionsUrl(name: string, lat: number, lng: number): string {
  return `https://map.kakao.com/link/to/${encodeURIComponent(name)},${lat},${lng}`
}

export function googleDirectionsUrl(lat: number, lng: number): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`
}

/**
 * 코스 전체를 한 번에 여는 경로 링크. 마지막 지점이 목적지, 나머지가 경유지다.
 *
 * <p>구글 지도의 경유지 상한은 9곳이라 그보다 길면 앞쪽을 솎아낸다 — 넘겨도 조용히 잘리는
 * 것보다 우리가 어디를 뺐는지 아는 편이 낫다. 좌표가 없는 장소는 애초에 넘기지 않는다.
 */
export function googleRouteUrl(points: { lat: number; lng: number }[]): string | null {
  if (points.length === 0) return null
  const destination = points[points.length - 1]
  const waypoints = points.slice(0, -1).slice(-9)
  const query = new URLSearchParams({ api: '1', destination: `${destination.lat},${destination.lng}` })
  if (waypoints.length > 0) {
    query.set('waypoints', waypoints.map((p) => `${p.lat},${p.lng}`).join('|'))
  }
  return `https://www.google.com/maps/dir/?${query.toString()}`
}
