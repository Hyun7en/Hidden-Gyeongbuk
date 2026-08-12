package com.hiddengyeongbuk.backend.domain.hub;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubTranslationRepository extends JpaRepository<HubTranslation, Long> {

    /** 거점 목록 응답에서 전 거점의 특정 언어 표시명을 한 번에 조회(N+1 방지). */
    List<HubTranslation> findByLang(String lang);

    Optional<HubTranslation> findByHubIdAndLang(Long hubId, String lang);
}
