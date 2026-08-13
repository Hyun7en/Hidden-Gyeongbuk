import { useState } from 'react'

/** 디자인 시스템 `SearchInput`(`hg-field`). 정의는 `styles/ds.css`. */
function SearchInput({
  value,
  placeholder,
  size = 'md',
  shape = 'pill',
  disabled,
  onChange,
  onSubmit,
  className,
}: {
  value: string
  placeholder?: string
  size?: 'sm' | 'md' | 'lg'
  shape?: 'pill' | 'box'
  disabled?: boolean
  onChange: (value: string) => void
  onSubmit?: (value: string) => void
  className?: string
}) {
  const [focused, setFocused] = useState(false)
  const cls = [
    'hg-field',
    `hg-field-${shape}`,
    `hg-field-${size}`,
    focused ? 'hg-field-focused' : '',
    disabled ? 'hg-field-disabled' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={cls}>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6">
        <circle cx="7" cy="7" r="5" />
        <path d="M11 11l4 4" strokeLinecap="round" />
      </svg>
      <input
        type="search"
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && onSubmit) onSubmit(value)
        }}
      />
      {value && !disabled && (
        <button type="button" className="hg-field-clear" aria-label="지우기" onClick={() => onChange('')}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
            <path d="M3 3l8 8M11 3l-8 8" />
          </svg>
        </button>
      )}
    </div>
  )
}

export default SearchInput
