export type Duration = 'ONE_DAY' | 'ONE_NIGHT' | 'TWO_NIGHT'
export type Interest = 'NATURE' | 'FOOD' | 'TRADITION' | 'TEMPLE' | 'FESTIVAL' | 'REST'
export type Difficulty = 'EASY' | 'NORMAL' | 'LOCAL'

export const DURATION_OPTIONS: { value: Duration; label: string }[] = [
  { value: 'ONE_DAY', label: 'One day' },
  { value: 'ONE_NIGHT', label: 'One night' },
  { value: 'TWO_NIGHT', label: 'Two nights' },
]

export const INTEREST_OPTIONS: { value: Interest; label: string }[] = [
  { value: 'NATURE', label: 'Nature' },
  { value: 'FOOD', label: 'Food' },
  { value: 'TRADITION', label: 'Tradition' },
  { value: 'TEMPLE', label: 'Temple' },
  { value: 'FESTIVAL', label: 'Festival' },
  { value: 'REST', label: 'Rest' },
]

export const DIFFICULTY_OPTIONS: { value: Difficulty; label: string }[] = [
  { value: 'EASY', label: 'Easy' },
  { value: 'NORMAL', label: 'Normal' },
  { value: 'LOCAL', label: 'Local experience' },
]
