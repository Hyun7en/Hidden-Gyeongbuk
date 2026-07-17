/** /api/places 응답의 tags는 place_tag.tag 컬럼(한글) 그대로 온다 — 영문 우선 UI 표기를 위한 역매핑. */
export const KO_TO_EN_INTEREST: Record<string, string> = {
  자연: 'Nature',
  음식: 'Food',
  전통: 'Tradition',
  사찰: 'Temple',
  축제: 'Festival',
  휴식: 'Rest',
}

export function translateTag(tagKo: string): string {
  return KO_TO_EN_INTEREST[tagKo] ?? tagKo
}

/** PlaceCategory 자바 enum이 관광지/음식점/숙박/축제/쇼핑처럼 한글 상수명으로 정의돼 있어, name()이 그대로 한글로 온다. */
export const KO_TO_EN_CATEGORY: Record<string, string> = {
  관광지: 'Tourist Spot',
  음식점: 'Restaurant',
  숙박: 'Lodging',
  축제: 'Festival',
  쇼핑: 'Shopping',
}

export function translateCategory(categoryKo: string): string {
  return KO_TO_EN_CATEGORY[categoryKo] ?? categoryKo
}

/**
 * 백엔드 LangFallback.overview()는 overviewEn이 없으면 overviewKo를 그대로 돌려준다 —
 * 필드 하나에 언어가 섞여 나오므로, 프론트에서 한글 포함 여부로 폴백 여부를 판별한다.
 */
export function containsHangul(text: string): boolean {
  return /[가-힣]/.test(text)
}
