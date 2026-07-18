package com.hiddengyeongbuk.backend.ingest.admin;

import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import com.hiddengyeongbuk.backend.ingest.PlaceCollectionService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 15곳 초기 대량 적재·수동 재처리용 내부 전용 엔드포인트.
 * 인증이 없으므로 local 프로파일에서만 빈이 뜬다 — 운영 배포(SPRING_PROFILES_ACTIVE=prod)에서는
 * 이 엔드포인트 자체가 존재하지 않는다(404). "배포 전에 제거하자"는 계획 대신 코드로 강제한다.
 */
@RestController
@Profile("local")
@RequestMapping("/internal/sync")
public class SyncAdminController {

    private final RegionRepository regionRepository;
    private final PlaceCollectionService placeCollectionService;

    public SyncAdminController(RegionRepository regionRepository, PlaceCollectionService placeCollectionService) {
        this.regionRepository = regionRepository;
        this.placeCollectionService = placeCollectionService;
    }

    @GetMapping("/regions")
    public List<RegionSummary> listRegions() {
        return regionRepository.findAll().stream()
                .map(r -> new RegionSummary(r.getId(), r.getNameKo(), r.getLdongRegnCd(), r.getLdongSignguCd()))
                .collect(Collectors.toList());
    }

    /**
     * 해당 지역 전체를 areaBasedSyncList2 페이지네이션으로 수집한다(collectRegionIncremental).
     * 지역 규모에 따라 응답까지 수 분 걸릴 수 있다 — TourAPI 개발계정 일 1,000건 한도를
     * 감안해 하루 1~2개 지역만 호출할 것.
     */
    @PostMapping("/regions/{regionId}")
    public ResponseEntity<PlaceCollectionService.CollectionResult> collectRegion(@PathVariable Long regionId) {
        Region region = regionRepository
                .findById(regionId)
                .orElseThrow(() -> new RegionNotFoundException(regionId));
        return ResponseEntity.ok(placeCollectionService.collectRegionIncremental(region));
    }

    @ExceptionHandler(RegionNotFoundException.class)
    public ResponseEntity<String> handleRegionNotFound(RegionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    public record RegionSummary(Long id, String nameKo, String ldongRegnCd, String ldongSignguCd) {}

    static class RegionNotFoundException extends RuntimeException {
        RegionNotFoundException(Long regionId) {
            super("존재하지 않는 region_id: " + regionId);
        }
    }
}
