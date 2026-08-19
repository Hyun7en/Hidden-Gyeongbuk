import { useEffect, useState } from 'react'
import { fetchMetaOptions } from '../api/client'
import { useOptionLabels, type OptionLabels } from '../api/optionLabels'
import type { MetaOptionsResponse } from '../api/types'
import { useLanguage } from '../context/LanguageContext'

/**
 * 셀렉터 옵션과 enum 라벨의 단일 출처(API 명세서 §2.3, ADR-024).
 *
 * <p><b>프론트는 enum 라벨 사전을 갖지 않는다.</b> 사전을 양쪽에 두면 언어를 늘릴 때 두 곳이
 * 어긋나고, 실제로 어긋났다 — 백엔드는 `PlaceType.served()`로 FD를 뺐는데 프론트 i18n에는
 * `options.interest.FD`("음식"/"Food")가 남아 있었다. 서비스하지 않는 분류의 라벨을 프론트가
 * 계속 들고 있던 셈이다.
 *
 * <p><b>선택지 목록도 여기서 온다.</b> 라벨만 받고 목록은 프론트가 박아 두면 분류가 늘거나
 * 줄 때 다시 두 곳을 고쳐야 한다 — 그게 사전을 없애는 이유와 같은 이유다.
 *
 * <p>언어가 바뀌면 다시 부른다. 라벨이 요청 언어로 해석되어 오는 값이기 때문이다.
 */
export function useMetaOptions(): {
  meta: MetaOptionsResponse | undefined
  labels: OptionLabels
  loading: boolean
  error: string | null
} {
  const { lang } = useLanguage()
  const [meta, setMeta] = useState<MetaOptionsResponse | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setError(null)
    fetchMetaOptions(lang)
      .then((data) => {
        if (!cancelled) setMeta(data)
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [lang])

  const labels = useOptionLabels(meta)
  return { meta, labels, loading: meta === undefined && error === null, error }
}
