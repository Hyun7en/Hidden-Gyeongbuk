export type Duration = 'ONE_DAY' | 'ONE_NIGHT' | 'TWO_NIGHT'
// 관심사 어휘는 장소 분류와 같다(ADR-024). 단일 출처는 API 계층의 PlaceType이다.
export type { Interest } from '../api/types'
import type { Interest } from '../api/types'
export type Difficulty = 'EASY' | 'NORMAL' | 'LOCAL'

export const DURATION_OPTIONS: { value: Duration; labelKey: string }[] = [
  { value: 'ONE_DAY', labelKey: 'options.duration.ONE_DAY' },
  { value: 'ONE_NIGHT', labelKey: 'options.duration.ONE_NIGHT' },
  { value: 'TWO_NIGHT', labelKey: 'options.duration.TWO_NIGHT' },
]

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

export const DIFFICULTY_OPTIONS: { value: Difficulty; labelKey: string }[] = [
  { value: 'EASY', labelKey: 'options.difficulty.EASY' },
  { value: 'NORMAL', labelKey: 'options.difficulty.NORMAL' },
  { value: 'LOCAL', labelKey: 'options.difficulty.LOCAL' },
]
