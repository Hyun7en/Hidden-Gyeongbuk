export type Duration = 'ONE_DAY' | 'ONE_NIGHT' | 'TWO_NIGHT'
export type Interest = 'NATURE' | 'FOOD' | 'TRADITION' | 'TEMPLE' | 'FESTIVAL' | 'REST'
export type Difficulty = 'EASY' | 'NORMAL' | 'LOCAL'

export const DURATION_OPTIONS: { value: Duration; labelKey: string }[] = [
  { value: 'ONE_DAY', labelKey: 'options.duration.ONE_DAY' },
  { value: 'ONE_NIGHT', labelKey: 'options.duration.ONE_NIGHT' },
  { value: 'TWO_NIGHT', labelKey: 'options.duration.TWO_NIGHT' },
]

export const INTEREST_OPTIONS: { value: Interest; labelKey: string }[] = [
  { value: 'NATURE', labelKey: 'options.interest.NATURE' },
  { value: 'FOOD', labelKey: 'options.interest.FOOD' },
  { value: 'TRADITION', labelKey: 'options.interest.TRADITION' },
  { value: 'TEMPLE', labelKey: 'options.interest.TEMPLE' },
  { value: 'FESTIVAL', labelKey: 'options.interest.FESTIVAL' },
  { value: 'REST', labelKey: 'options.interest.REST' },
]

export const DIFFICULTY_OPTIONS: { value: Difficulty; labelKey: string }[] = [
  { value: 'EASY', labelKey: 'options.difficulty.EASY' },
  { value: 'NORMAL', labelKey: 'options.difficulty.NORMAL' },
  { value: 'LOCAL', labelKey: 'options.difficulty.LOCAL' },
]
