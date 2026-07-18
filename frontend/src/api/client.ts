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

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  })
  if (!res.ok) {
    throw new Error(`${path} failed with ${res.status}`)
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
