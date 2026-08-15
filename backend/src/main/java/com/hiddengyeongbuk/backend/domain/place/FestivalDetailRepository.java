package com.hiddengyeongbuk.backend.domain.place;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalDetailRepository extends JpaRepository<FestivalDetail, Long> {

    /**
     * 후보 풀에 있는 장소들의 축제 기간을 한 번에 읽는다(N+1 방지). 추천은 요청마다 도는
     * 경로라 장소별 조회를 돌면 안 된다.
     */
    List<FestivalDetail> findByPlaceIdIn(Collection<Long> placeIds);
}
