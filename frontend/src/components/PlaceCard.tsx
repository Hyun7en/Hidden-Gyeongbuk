import { Link } from 'react-router-dom'

export interface PlaceCardData {
  placeId: number
  title: string
  addr: string
  /** 요청 언어로 해석된 분류 라벨. 장소당 정확히 하나다(ADR-024). */
  placeTypeLabel: string
  imageUrl: string | null
}

function PlaceCard({ place }: { place: PlaceCardData }) {
  return (
    <Link to={`/places/${place.placeId}`} className="block border border-line bg-bg-elevated">
      <div
        className="h-[140px] md:h-[180px]"
        style={
          place.imageUrl
            ? { backgroundImage: `url(${place.imageUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' }
            : {
                backgroundImage:
                  'repeating-linear-gradient(45deg, var(--line), var(--line) 8px, var(--bg-elevated) 8px, var(--bg-elevated) 16px)',
              }
        }
      />
      <div className="p-3.5 md:p-4">
        <p className="text-[15px] font-bold md:text-base">{place.title}</p>
        <p className="mt-1 text-xs text-text-soft md:text-sm">{place.addr}</p>
        {place.placeTypeLabel && (
          <div className="mt-2.5">
            <span className="border border-line px-2 py-[3px] text-[11px] md:text-xs">{place.placeTypeLabel}</span>
          </div>
        )}
      </div>
    </Link>
  )
}

export default PlaceCard
