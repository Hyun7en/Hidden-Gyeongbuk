function PatternDefs() {
  return (
    <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden="true">
      <defs>
        <pattern id="keyfret" width="12" height="12" patternUnits="userSpaceOnUse">
          <path d="M0 9 V3 H6 V9 H12" fill="none" stroke="var(--line)" strokeWidth="1.2" strokeLinecap="square" />
        </pattern>
        <pattern id="lattice" width="22" height="22" patternUnits="userSpaceOnUse">
          <path d="M0 0 L22 22 M22 0 L0 22" stroke="var(--line)" strokeWidth="1" />
        </pattern>
      </defs>
    </svg>
  )
}

export default PatternDefs
