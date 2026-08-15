/**
 * 카드 로딩 자리표시자. {@link Card}와 같은 골격(이미지 180px + 본문 두 줄)이라 실제 데이터가
 * 도착해도 레이아웃이 튀지 않는다.
 *
 * <p>여러 화면이 같은 그리드를 쓰므로 컴포넌트로 뺐다 — 화면마다 베껴 두면 카드 높이를 바꿀 때
 * 한쪽만 고치게 된다.
 */
function CardSkeleton() {
  return (
    <div className="border border-line bg-bg-elevated">
      <div className="skeleton h-[140px] md:h-[180px]" />
      <div className="flex flex-col gap-2 p-3.5">
        <div className="skeleton h-3 w-[70%]" />
        <div className="skeleton h-2.5 w-[45%]" />
      </div>
    </div>
  )
}

export default CardSkeleton
