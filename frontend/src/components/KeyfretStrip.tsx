function KeyfretStrip({ opacity = 1 }: { opacity?: number }) {
  return (
    <svg viewBox="0 0 480 9" preserveAspectRatio="none" className="block h-[9px] w-full">
      <rect width="480" height="9" fill="url(#keyfret)" opacity={opacity} />
    </svg>
  )
}

export default KeyfretStrip
