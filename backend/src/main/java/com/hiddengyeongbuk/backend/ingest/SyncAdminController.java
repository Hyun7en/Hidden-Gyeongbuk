package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.domain.region.RegionRepository;
import java.util.List;
import java.util.stream.Collectors;
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
 * 인증이 없으므로 로컬 개발 중에만 사용한다 — 배포 전 반드시 제거하거나 접근 제한을
 * 추가할 것(P4, "운영 DB 자격증명/네트워크 보안 설정"와 함께 처리).
 */
@RestController
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
