export interface HubResponse {
  hubId: number
  nameKo: string
  nameEn: string
  lat: number
  lng: number
  description: string
  descriptionEn: string | null
}

export interface RegionResponse {
  regionId: number
  nameKo: string
  nameEn: string
  hubId: number
  lat: number
  lng: number
}

export interface MetaOption {
  code: string
  labelKo: string
  labelEn: string
}

export interface MetaOptionsResponse {
  durations: MetaOption[]
  difficulties: MetaOption[]
  interests: MetaOption[]
  hubs: MetaOption[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

export interface PlaceSummaryResponse {
  placeId: number
  category: string
  title: string
  addr: string
  lat: number | null
  lng: number | null
  firstImage: string | null
  tags: string[]
}

export interface PlaceDetailResponse {
  placeId: number
  contentId: string
  category: string
  title: string
  titleKo: string
  addr: string
  tel: string | null
  lat: number | null
  lng: number | null
  overview: string | null
  firstImage: string | null
  images: string[]
  tags: string[]
  // title/addr/overview는 백엔드에서 이미 요청 언어로 해석돼 내려온다(place_translation → 국문 폴백).
  // translationSource는 그 영문 출처 — 'OFFICIAL'(공식) / 'TRANSLATE'(기계 번역) / null(영문 없음=국문 표시).
  translationSource: 'OFFICIAL' | 'TRANSLATE' | null
  festival: { startDate: string; endDate: string } | null
}

export interface CourseBreakdown {
  interest: number
  access: number
  stay: number
  dispersion: number
  completeness: number
}

export interface CoursePlaceResponse {
  order: number
  placeId: number
  category: string
  title: string
  lat: number
  lng: number
}

export interface CourseDayResponse {
  day: number
  places: CoursePlaceResponse[]
  lodging: { placeId: number; title: string } | null
}

export interface CourseResponse {
  rank: number
  score: number
  breakdown: CourseBreakdown
  hubId: number
  totalDistanceKm: number
  days: CourseDayResponse[]
}

export interface CourseListResponse {
  courses: CourseResponse[]
}

export interface CourseRecommendRequest {
  hubId: number
  duration: string
  interests: string[]
  difficulty: string
}
