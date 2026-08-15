package com.hiddengyeongbuk.backend.domain.place;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlaceTranslationRepository extends JpaRepository<PlaceTranslation, Long> {

    /**
     * 여러 장소의 번역 행을 언어 구분 없이 한 번에 조회(N+1 방지).
     *
     * <p>언어로 걸러내지 않는 이유는 폴백 체인 때문이다 — 요청 언어와 ko가 모두 없으면
     * "존재하는 아무 행"을 써야 하고, addrLocal은 요청 언어와 무관하게 ko 행이 필요하다.
     * 언어별로 조건을 나누면 언어를 늘릴 때 쿼리가 늘어난다.
     */
    List<PlaceTranslation> findByPlaceIdIn(Collection<Long> placeIds);

    List<PlaceTranslation> findByPlaceId(Long placeId);

    Optional<PlaceTranslation> findByPlaceIdAndLang(Long placeId, String lang);

    boolean existsByPlaceIdAndLang(Long placeId, String lang);

    /** 번역 백필 대상 — 해당 언어 행이 아직 없는 장소를 찾기 위해 존재하는 조합을 읽는다. */
    List<PlaceTranslation> findByLang(String lang);

    /** 언어별 번역 행 수 — place 수와의 차이가 곧 번역 백필 잔량이다(GET /internal/sync/status). */
    @Query("select t.lang, count(t) from PlaceTranslation t group by t.lang")
    List<Object[]> countGroupedByLang();
}
