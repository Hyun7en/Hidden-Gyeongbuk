export interface HubResponse {
  hubId: number
  nameKo: string
  nameEn: string
  lat: number
  lng: number
  description: string
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
  addr: string
  tel: string | null
  lat: number | null
  lng: number | null
  overview: string | null
  firstImage: string | null
  images: string[]
  tags: string[]
  summary: { titleEn: string | null; text: string; source: string } | null
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
  shareUrl: string
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
