import { useMemo } from 'react'
import type { MetaOptionsResponse } from './types'

/**
 * enum 코드 → 표시 라벨 조회.
 *
 * <p>프론트는 자체 라벨 사전을 갖지 않는다. `GET /api/meta/options`가 요청 언어로 해석한
 * `{ code, label }`을 주므로(API 명세서 §2.3), 카드 배지·필터 칩·선택지가 전부 그 응답
 * 하나를 공유한다. 사전을 프론트에도 두면 언어를 늘릴 때 두 곳이 어긋난다 —
 * 명세서가 `labelKo`/`labelEn` 방식을 폐기한 이유가 그것이다.
 *
 * <p>라벨이 아직 안 왔으면(로딩·실패) 코드를 그대로 보여준다. 빈 문자열보다 낫고,
 * 무엇이 안 왔는지 화면에서 바로 드러난다.
 */
export type LabelLookup = (code: string | null | undefined) => string

function toMap(options: { code: string; label: string }[] | undefined): Map<string, string> {
  return new Map((options ?? []).map((o) => [o.code, o.label]))
}

export interface OptionLabels {
  placeType: LabelLookup
  interest: LabelLookup
  difficulty: LabelLookup
  hub: LabelLookup
}

export function useOptionLabels(meta: MetaOptionsResponse | undefined): OptionLabels {
  return useMemo(() => {
    const lookup = (map: Map<string, string>): LabelLookup =>
      (code) => (code ? (map.get(code) ?? code) : '')
    return {
      placeType: lookup(toMap(meta?.placeTypes)),
      interest: lookup(toMap(meta?.interests)),
      difficulty: lookup(toMap(meta?.difficulties)),
      hub: lookup(toMap(meta?.hubs)),
    }
  }, [meta])
}
