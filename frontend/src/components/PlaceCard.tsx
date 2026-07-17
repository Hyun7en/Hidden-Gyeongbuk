import { Link } from 'react-router-dom'

export interface PlaceCardData {
  placeId: number
  title: string
  addr: string
  tags: string[]
  hasImage: boolean
}

function PlaceCard({ place }: { place: PlaceCardData }) {
  return (
    <Link to={`/places/${place.placeId}`} className="block border border-line bg-bg-elevated">
      <div
        className="h-[140px] md:h-[180px]"
        style={
          place.hasImage
            ? { background: 'linear-gradient(135deg, var(--accent), var(--accent-soft))' }
            : {
                backgroundImage:
                  'repeating-linear-gradient(45deg, var(--line), var(--line) 8px, var(--bg-elevated) 8px, var(--bg-elevated) 16px)',
              }
        }
      />
      <div className="p-3.5 md:p-4">
        <p className="text-[15px] font-bold md:text-base">{place.title}</p>
        <p className="mt-1 text-xs text-text-soft md:text-sm">{place.addr}</p>
        <div className="mt-2.5 flex flex-wrap gap-1.5">
          {place.tags.map((tag) => (
            <span key={tag} className="border border-line px-2 py-[3px] text-[11px] md:text-xs">
              {tag}
            </span>
          ))}
        </div>
      </div>
    </Link>
  )
}

export default PlaceCard
