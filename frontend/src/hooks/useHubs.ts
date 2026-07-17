import { useEffect, useState } from 'react'
import { fetchHubs } from '../api/client'
import type { HubResponse } from '../api/types'

export function useHubs() {
  const [hubs, setHubs] = useState<HubResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    fetchHubs()
      .then((data) => {
        if (!cancelled) setHubs(data)
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { hubs, loading, error }
}
