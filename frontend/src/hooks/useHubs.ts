import { useEffect, useState } from 'react'
import { fetchHubs } from '../api/client'
import type { HubResponse } from '../api/types'
import { useLanguage } from '../context/LanguageContext'

/**
 * 교통 거점 목록. **언어가 바뀌면 다시 부른다** — 거점명과 성격 설명이 요청 언어로 해석되어
 * 오는 값이라, 한 번만 받아 두면 언어를 바꿔도 처음 받은 언어가 화면에 남는다.
 * (useRegions가 같은 이유로 lang을 의존성에 넣고 있다.)
 */
export function useHubs() {
  const { lang } = useLanguage()
  const [hubs, setHubs] = useState<HubResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    fetchHubs(lang)
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
  }, [lang])

  return { hubs, loading, error }
}
