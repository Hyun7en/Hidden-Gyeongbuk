import type {
  CourseListResponse,
  CourseRecommendRequest,
  HubResponse,
  MetaOptionsResponse,
  MetaLanguagesResponse,
  PageResponse,
  PlaceDetailResponse,
  PlaceSummaryResponse,
  RegionResponse,
  RegionDetailResponse,
  PlaceNearbyResponse,
} from './types'

// 기본값은 same-origin 상대 경로 — 개발은 Vite 프록시(vite.config.ts)가 /api를
// 백엔드로 전달하고, 배포에서 백엔드 도메인이 다르면 VITE_API_BASE_URL로 지정한다.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/**
 * 백엔드 표준 에러 포맷 `{ error: { code, message } }`를 그대로 실어 나른다.
 *
 * <p><b>코드를 문자열에 녹이지 않고 필드로 남기는 이유</b>: 화면이 코드에 따라 다르게 반응해야
 * 한다. 422 `NO_COURSE_FOUND`는 고장이 아니라 "조건이 좁다"는 뜻이라 재시도 버튼이 아니라
 * 조건 완화 안내를 띄워야 한다(화면 설계서 C3).
 */
export class ApiRequestError extends Error {
  // 생성자 파라미터 프로퍼티는 쓰지 않는다 — tsconfig의 erasableSyntaxOnly가 막는다(타입만
  // 지우면 되는 문법만 허용). 필드를 명시적으로 선언한다.
  readonly code: string
  readonly status: number

  constructor(code: string, status: number, message: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  })
  if (!res.ok) {
    let code = 'INTERNAL_ERROR'
    let detail = `${res.status}`
    try {
      const body = (await res.json()) as { error?: { code?: string; message?: string } }
      if (body.error?.code) code = body.error.code
      if (body.error?.message) detail = `${code}: ${body.error.message}`
    } catch {
      // 에러 바디가 JSON이 아니면 상태코드만 사용
    }
    throw new ApiRequestError(code, res.status, detail)
  }
  return res.json() as Promise<T>
}

/**
 * 거점 목록. **`lang`을 반드시 넘긴다** — 거점명·설명이 요청 언어로 해석되어 오는 값이라
 * 생략하면 백엔드가 기준 언어(ko)로 떨어뜨려, 영문 사용자에게 "안동 / 전통·자연·사찰"이
 * 그대로 나간다. 이 값이 거점 선택 칩·지역 배지·추천 결과 헤더에 직접 렌더링된다.
 */
export function fetchHubs(lang = 'en'): Promise<HubResponse[]> {
  return request(`/api/hubs?lang=${lang}`)
}

/**
 * 셀렉터 옵션(API 명세서 §2.3). 코드와 **요청 언어로 해석된 라벨**이 함께 온다 —
 * 프론트가 자체 라벨 사전을 갖지 않게 해서 언어를 늘릴 때 백엔드 한 곳만 고치면 되게 한다.
 */
export function fetchMetaOptions(lang = 'en'): Promise<MetaOptionsResponse> {
  return request(`/api/meta/options?lang=${lang}`)
}

/** 지원 언어 목록(API 명세서 §2.4). 언어 선택기와 소개 화면 커버리지가 쓴다. */
export function fetchLanguages(): Promise<MetaLanguagesResponse> {
  return request('/api/meta/languages')
}

/**
 * 지역 목록. `hubId`를 주면 그 거점 소속 지역이 상단에 오도록 정렬되고, 생략하면
 * 전체 지역을 기본 순서로 준다(API 명세서 §2.2) — 홈의 지역 카탈로그가 후자를 쓴다.
 */
export function fetchRegions(params: { hubId?: number; lang?: string } = {}): Promise<RegionResponse[]> {
  const query = new URLSearchParams()
  if (params.hubId !== undefined) query.set('hubId', String(params.hubId))
  if (params.lang) query.set('lang', params.lang)
  const qs = query.toString()
  return request(`/api/regions${qs ? `?${qs}` : ''}`)
}

export function fetchRegion(regionId: number, lang = 'en'): Promise<RegionDetailResponse> {
  return request(`/api/regions/${regionId}?lang=${lang}`)
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
    startDate: params.startDate,
    endDate: params.endDate,
    difficulty: params.difficulty,
    lang,
  })
  if (params.regionIds && params.regionIds.length > 0) query.set('regionIds', params.regionIds.join(','))
  if (params.interests.length > 0) query.set('interests', params.interests.join(','))
  return request(`/api/courses?${query.toString()}`)
}

export function searchPlaces(
  // tag·category는 폐기됐다 — 분류축이 placeType 하나로 합쳐졌다(ADR-024).
  params: { regionId?: number; placeType?: string; keyword?: string; page?: number; size?: number },
  lang = 'en',
): Promise<PageResponse<PlaceSummaryResponse>> {
  const query = new URLSearchParams({ lang })
  if (params.regionId !== undefined) query.set('regionId', String(params.regionId))
  if (params.placeType) query.set('placeType', params.placeType)
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  return request(`/api/places?${query.toString()}`)
}

/** 주변 장소(FR-S-04). 좌표 기준이라 캐싱 대상이 아니다 — 키 공간이 사실상 무한하다. */
export function fetchNearby(
  params: { lat: number; lng: number; radius?: number },
  lang = 'en',
): Promise<PlaceNearbyResponse[]> {
  const query = new URLSearchParams({ lat: String(params.lat), lng: String(params.lng), lang })
  if (params.radius !== undefined) query.set('radius', String(params.radius))
  return request(`/api/places/nearby?${query.toString()}`)
}

export function fetchPlaceDetail(placeId: number, lang = 'en'): Promise<PlaceDetailResponse> {
  return request(`/api/places/${placeId}?lang=${lang}`)
}
