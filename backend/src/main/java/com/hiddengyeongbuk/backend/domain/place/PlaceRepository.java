package com.hiddengyeongbuk.backend.domain.place;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlaceRepository extends JpaRepository<Place, Long>, JpaSpecificationExecutor<Place> {

    // 구 findByRegionIdInWithTags의 JOIN FETCH는 place_tag N+1을 피하려던 것인데, 태그가
    // 폐기되고 분류가 place의 컬럼이 되면서 페치할 연관이 없어졌다(ADR-024).
    List<Place> findByRegionIdIn(List<Long> regionIds);

    // findByContentId·findByHasEnFalse는 제거됐다. place에 자연키(content_id)가 없어졌고
    // (정체성은 Resolution 결과로만 결정된다), 언어 커버리지는 has_en 플래그가 아니라
    // place_translation 행 존재로 판단한다(ADR-017). 번역 백필 대상 조회는 Translator가
    // place_translation을 기준으로 수행한다.
}
