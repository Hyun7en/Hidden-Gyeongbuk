import { useEffect, useState } from 'react'
import { fetchRegions } from '../api/client'
import type { RegionResponse } from '../api/types'
import { useLanguage } from '../context/LanguageContext'

/**
 * 지역 목록. `hubId`를 주면 그 거점 소속이 상단에 오도록 정렬되고, 생략하면 전체다.
 * 언어가 바뀌면 다시 부른다 — 지역명이 요청 언어로 해석되어 오기 때문이다.
 */
export function useRegions(hubId?: number) {
  const { lang } = useLanguage()
  const [regions, setRegions] = useState<RegionResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setError(null)
    fetchRegions({ hubId, lang })
      .then((data) => {
        if (!cancelled) setRegions(data)
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [hubId, lang])

  return { regions, loading: regions === null && error === null, error }
}
