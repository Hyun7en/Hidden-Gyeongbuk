/**
 * 백엔드 DTO와 필드명을 일치시킨다(API 명세서 v0.9).
 *
 * 언어별 필드(nameKo/nameEn, labelKo/labelEn)는 전부 사라졌다 — 백엔드가 요청 언어로
 * 해석한 단일 값을 주고, 실제 사용된 언어를 resolvedLang으로 알린다. 언어를 늘려도
 * 응답 스키마가 바뀌지 않게 하기 위함이다(ADR-018).
 */

/**
 * 관광공사 신분류체계 대분류 코드(ADR-024). 표시 라벨은 `GET /api/meta/options`가 요청
 * 언어로 해석해 준다(API 명세서 §2.3) — 프론트가 자체 사전을 갖지 않는다.
 */
export type PlaceType =
  | 'AC' // 숙박
  | 'EV' // 축제·공연·행사
  | 'EX' // 체험관광
  | 'FD' // 음식
  | 'HS' // 역사관광
  | 'LS' // 레저스포츠
  | 'NA' // 자연관광
  | 'SH' // 쇼핑
  | 'VE' // 문화관광

/**
 * 관심사는 별도 어휘가 아니라 장소 분류와 같은 값이다(ADR-024). 타입 별칭으로 남긴 것은
 * 호출부에서 "이 자리는 관심사"라는 의도가 읽히게 하기 위함이다.
 */
export type Interest = PlaceType

export type Difficulty = 'EASY' | 'NORMAL' | 'LOCAL'

export type TranslationSource = 'OFFICIAL' | 'TRANSLATE'

export interface HubResponse {
  hubId: number
  /** ANDONG / DONGDAEGU / POHANG / NORTH_INLAND. 공유 URL이 이 값으로 거점을 지목한다. */
  code: string
  name: string
  description: string | null
  lat: number
  lng: number
  resolvedLang: string | null
}

export interface RegionResponse {
  regionId: number
  name: string
  hubId: number
  lat: number | null
  lng: number | null
  firstImage: string | null
  resolvedLang: string | null
}

/** 지역 상세(S2). 목록과 달리 소개 글과 이미지를 준다. */
export interface RegionDetailResponse {
  regionId: number
  name: string
  hubId: number
  lat: number | null
  lng: number | null
  description: string | null
  images: string[]
  resolvedLang: string | null
}

export interface MetaOption {
  code: string
  /** 요청 언어로 해석된 라벨. */
  label: string
}

export interface MetaOptionsResponse {
  /** 장소 분류 선택지. 카드 배지·필터 칩의 라벨이 여기서 온다(API 명세서 §2.3). */
  placeTypes: MetaOption[]
  difficulties: MetaOption[]
  /** placeTypes와 항상 같은 목록이다 — 분류축이 하나로 합쳐졌다(ADR-024). */
  interests: MetaOption[]
  hubs: MetaOption[]
  resolvedLang: string | null
}

export interface LanguageOption {
  code: string
  /** 자기 언어 이름(ko → 한국어). */
  name: string
}

export interface MetaLanguagesResponse {
  languages: LanguageOption[]
  default: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  /** 목록 전체에 대한 요청 언어 해석 결과. 항목별 폴백은 상세의 translationSource로 확인. */
  resolvedLang: string | null
}

export interface PlaceSummaryResponse {
  placeId: number
  placeType: PlaceType
  title: string | null
  addr: string | null
  lat: number | null
  lng: number | null
  firstImage: string | null
}

export interface PlaceDetailResponse {
  placeId: number
  placeType: PlaceType
  regionId: number
  title: string | null
  addr: string | null
  /**
   * 현지 표기 주소(한국어). 요청 언어와 무관하게 항상 내려온다 — 택시 기사에게 보여주는
   * 용도라, 번역된 주소만으로는 실제 이동이 불가능하다(User Persona §4-1).
   */
  addrLocal: string | null
  tel: string | null
  lat: number | null
  lng: number | null
  overview: string | null
  firstImage: string | null
  images: string[]
  /** 실제 표시에 사용된 언어. 요청과 다르면 폴백이 일어난 것이다. */
  resolvedLang: string | null
  translationSource: TranslationSource | null
  festival: { startDate: string; endDate: string } | null
}

export interface PlaceNearbyResponse {
  place: PlaceSummaryResponse
  distanceKm: number
}

/** 체류 적합도(stay)는 점수에서 제외되고 제약으로 전환됐다(ADR-021). */
export interface CourseBreakdown {
  interest: number
  access: number
  dispersion: number
  completeness: number
}

export interface CoursePlaceResponse {
  order: number
  placeId: number
  placeType: PlaceType
  title: string | null
  lat: number | null
  lng: number | null
}

export interface CourseDayResponse {
  day: number
  places: CoursePlaceResponse[]
  lodging: { placeId: number; title: string | null } | null
}

export interface CourseResponse {
  /** 내부 정렬 기준이며 UI에는 노출하지 않는다(화면 설계서 S4). */
  rank: number
  score: number
  breakdown: CourseBreakdown
  hubId: number
  totalDistanceKm: number
  regionCount: number
  days: CourseDayResponse[]
}

/** 적용된 제약. 클라이언트가 "당일 3곳 이내" 같은 안내를 표시하는 근거. */
export interface CourseConstraints {
  placeCount: number
  maxDistanceKm: number
  lodgingCount: number
}

export interface CourseListResponse {
  courses: CourseResponse[]
  constraints: CourseConstraints | null
  resolvedLang: string | null
}

export interface CourseRecommendRequest {
  hubId: number
  /** 0개 이상. 빈 배열·생략 = 거점 주변 전체에서 구성(ADR-019). */
  regionIds?: number[]
  /** ISO 날짜. nights는 서버에서 파생하고, 시작일의 월은 계절 가중에 쓰인다(ADR-020). */
  startDate: string
  endDate: string
  interests: Interest[]
  difficulty: Difficulty
}

export interface ApiErrorResponse {
  error: {
    code: 'INVALID_PARAM' | 'NOT_FOUND' | 'NO_COURSE_FOUND' | 'UPSTREAM_UNAVAILABLE' | 'INTERNAL_ERROR'
    message: string
    hint?: { relaxable?: string[] }
  }
}
