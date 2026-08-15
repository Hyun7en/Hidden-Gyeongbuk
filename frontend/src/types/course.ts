// 기간은 enum이 아니라 날짜 범위다(ADR-020) — DURATION_OPTIONS는 폐기됐고, 박 수·일 수 계산은
// lib/tripDates.ts에 있다.
// 관심사 어휘는 장소 분류와 같다(ADR-024). 단일 출처는 API 계층의 PlaceType이다.
export type { Interest } from '../api/types'
import type { Interest } from '../api/types'
export type Difficulty = 'EASY' | 'NORMAL' | 'LOCAL'

// 9종이며 6종이던 구 자체 태그를 대체한다. 칩이 늘었으니 줄바꿈을 전제한 레이아웃이어야
// 한다(화면 설계서 S3). 라벨은 meta/options로 옮기는 중이라 이 표는 과도기다.
export const INTEREST_OPTIONS: { value: Interest; labelKey: string }[] = [
  { value: 'NA', labelKey: 'options.interest.NA' },
  { value: 'HS', labelKey: 'options.interest.HS' },
  { value: 'VE', labelKey: 'options.interest.VE' },
  { value: 'EX', labelKey: 'options.interest.EX' },
  { value: 'LS', labelKey: 'options.interest.LS' },
  { value: 'FD', labelKey: 'options.interest.FD' },
  { value: 'SH', labelKey: 'options.interest.SH' },
  { value: 'EV', labelKey: 'options.interest.EV' },
  { value: 'AC', labelKey: 'options.interest.AC' },
]

// 난이도는 라벨만으로 뜻이 안 통한다("로컬"이 무슨 뜻인지 알 수 없다) — 프로토타입처럼
// 한 줄 설명을 옆에 붙인다.
export const DIFFICULTY_OPTIONS: { value: Difficulty; labelKey: string; descKey: string }[] = [
  { value: 'EASY', labelKey: 'options.difficulty.EASY', descKey: 'options.difficultyDesc.EASY' },
  { value: 'NORMAL', labelKey: 'options.difficulty.NORMAL', descKey: 'options.difficultyDesc.NORMAL' },
  { value: 'LOCAL', labelKey: 'options.difficulty.LOCAL', descKey: 'options.difficultyDesc.LOCAL' },
]
