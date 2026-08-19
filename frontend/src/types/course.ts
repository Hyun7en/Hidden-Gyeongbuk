// 기간은 enum이 아니라 날짜 범위다(ADR-020) — DURATION_OPTIONS는 폐기됐고, 박 수·일 수 계산은
// lib/tripDates.ts에 있다.
// 관심사 어휘는 장소 분류와 같다(ADR-024). 단일 출처는 API 계층의 PlaceType이다.
export type { Interest } from '../api/types'
export type Difficulty = 'EASY' | 'NORMAL' | 'LOCAL'

// INTEREST_OPTIONS·DIFFICULTY_OPTIONS는 폐기됐다.
//
// 선택지 목록과 라벨은 `GET /api/meta/options`가 요청 언어로 해석해 내려준다(API 명세서 §2.3).
// 여기에 목록을 박아 두면 백엔드와 어긋난다 — 실제로 어긋났다. 백엔드는 ADR-025로 음식(FD)을
// 뺐는데 이 표와 i18n 사전에는 남아 있었다. useMetaOptions()를 쓸 것.

/**
 * 난이도 한 줄 설명의 i18n 키.
 *
 * <p><b>이건 사전이 아니라 UI 카피다.</b> meta/options가 주는 것은 `{code, label}`뿐이고,
 * "쉬움"이라는 라벨만으로는 뜻이 안 통해서("로컬경험"이 무슨 뜻인지 알 수 없다) 프로토타입이
 * 옆에 붙여 둔 설명 문구다. enum의 표시명이 아니므로 백엔드가 소유할 이유가 없다.
 *
 * <p>키가 없는 코드가 오면 설명 없이 라벨만 나온다 — 난이도가 늘어도 화면이 깨지지 않는다.
 */
export const DIFFICULTY_DESC_KEY: Record<string, string> = {
  EASY: 'options.difficultyDesc.EASY',
  NORMAL: 'options.difficultyDesc.NORMAL',
  LOCAL: 'options.difficultyDesc.LOCAL',
}
