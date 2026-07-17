import type { ReactNode } from 'react'

function Container({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`mx-auto w-full max-w-5xl px-6 md:px-10 lg:px-16 ${className}`}>{children}</div>
}

export default Container
