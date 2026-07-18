import type {
  CourseListResponse,
  CourseRecommendRequest,
  HubResponse,
  MetaOptionsResponse,
  PageResponse,
  PlaceDetailResponse,
  PlaceSummaryResponse,
  RegionResponse,
} from './types'

// 기본값은 same-origin 상대 경로 — 개발은 Vite 프록시(vite.config.ts)가 /api를
// 백엔드로 전달하고, 배포에서 백엔드 도메인이 다르면 VITE_API_BASE_URL로 지정한다.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  })
  if (!res.ok) {
    // 백엔드 표준 에러 포맷 { error: { code, message } }를 살려서 던진다 —
    // 상태코드만 보여주면 서버가 알려준 원인이 사용자에게 전달되지 않는다.
    let detail = `${res.status}`
    try {
      const body = (await res.json()) as { error?: { code?: string; message?: string } }
      if (body.error?.message) detail = `${body.error.code ?? res.status}: ${body.error.message}`
    } catch {
      // 에러 바디가 JSON이 아니면 상태코드만 사용
    }
    throw new Error(detail)
  }
  return res.json() as Promise<T>
}

export function fetchHubs(): Promise<HubResponse[]> {
  return request('/api/hubs')
}

export function fetchMetaOptions(): Promise<MetaOptionsResponse> {
  return request('/api/meta/options')
}

export function fetchRegions(hubId: number): Promise<RegionResponse[]> {
  return request(`/api/regions?hubId=${hubId}`)
}

export function recommendCourses(body: CourseRecommendRequest, lang = 'en'): Promise<CourseListResponse> {
  return request(`/api/courses/recommend?lang=${lang}`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

/** 공유 링크로 들어온 사용자를 위한 무상태 재계산(GET /api/courses) — POST와 같은 결과를 준다. */
export function fetchCourses(params: CourseRecommendRequest, lang = 'en'): Promise<CourseListResponse> {
  const query = new URLSearchParams({
    hubId: String(params.hubId),
    duration: params.duration,
    difficulty: params.difficulty,
    lang,
  })
  if (params.interests.length > 0) query.set('interests', params.interests.join(','))
  return request(`/api/courses?${query.toString()}`)
}

export function searchPlaces(
  params: { regionId?: number; category?: string; tag?: string; keyword?: string; page?: number; size?: number },
  lang = 'en',
): Promise<PageResponse<PlaceSummaryResponse>> {
  const query = new URLSearchParams({ lang })
  if (params.regionId !== undefined) query.set('regionId', String(params.regionId))
  if (params.category) query.set('category', params.category)
  if (params.tag) query.set('tag', params.tag)
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  return request(`/api/places?${query.toString()}`)
}

export function fetchPlaceDetail(placeId: number, lang = 'en'): Promise<PlaceDetailResponse> {
  return request(`/api/places/${placeId}?lang=${lang}`)
}
