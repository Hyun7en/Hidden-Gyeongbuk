package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.Place;
import com.hiddengyeongbuk.backend.domain.place.PlaceCategory;
import com.hiddengyeongbuk.backend.domain.place.PlaceRepository;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslation;
import com.hiddengyeongbuk.backend.domain.place.PlaceTranslationRepository;
import com.hiddengyeongbuk.backend.domain.region.Region;
import com.hiddengyeongbuk.backend.ingest.client.TourApiClient;
import com.hiddengyeongbuk.backend.ingest.client.dto.AreaBasedSyncItem;
import com.hiddengyeongbuk.backend.ingest.client.dto.DetailCommonItem;
import com.hiddengyeongbuk.backend.domain.place.TranslationSource;
import com.hiddengyeongbuk.backend.support.GeoUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * EngService2의 자체 areaBasedSyncList2로 지역별 영문 장소를 독자 수집해, 이미 저장된 국문 place에
 * 이름+좌표로 매칭하고 공식 영문 제목·설명을 채운다.
 *
 * 배경: TourAPI는 국문(KorService2)·영문(EngService2)이 서로 다른 contentId 공간을 쓴다(실측
 * 확인). 그래서 국문 목록의 contentId를 그대로 영문 detailCommon2에 넣던 기존 방식은 거의 항상
 * 빈손이었다. 대신 영문 목록을 독자 조회하고, 영문 title에 괄호로 실려오는 국문명({@link
 * KoreanNameExtractor})과 좌표 근접으로 국문 place와 짝을 맞춘다.
 *
 * 매칭은 "정밀 우선": 오라벨(엉뚱한 장소에 영문이 붙는 것)을 피하려고, 이름이 일치하지 않으면
 * 좌표만 가깝다고 매칭하지 않고, 애매하면 스킵한다(스킵된 장소는 번역 폴백이 담당).
 *
 * DB 쓰기는 항목 단위로 {@link PlaceWriter}에 위임해 독립 커밋시킨다(지역 루프에 @Transactional
 * 금지 — PlaceWriter 주석의 롱 트랜잭션·rollback-only 함정 참고).
 */
@Service
public class EngPlaceMatchingService {

    private static final Logger log = LoggerFactory.getLogger(EngPlaceMatchingService.class);
    private static final int PAGE_SIZE = 100;
    // 이름이 일치해도 좌표가 이 거리 이상 떨어져 있으면 동명이소(같은 이름 다른 장소)로 보고 매칭하지
    // 않는다. 국문/영문 좌표 데이터의 사소한 차이는 흡수하되 명백한 오매칭만 걸러내는 넉넉한 가드.
    private static final double COORD_GUARD_KM = 1.0;
    private static final String LANG_EN = "en";

    private final TourApiClient tourApiClient;
    private final ContentTypeMapper contentTypeMapper;
    private final PlaceRepository placeRepository;
    private final PlaceTranslationRepository placeTranslationRepository;
    private final PlaceWriter placeWriter;

    public EngPlaceMatchingService(
            TourApiClient tourApiClient,
            ContentTypeMapper contentTypeMapper,
            PlaceRepository placeRepository,
            PlaceTranslationRepository placeTranslationRepository,
            PlaceWriter placeWriter) {
        this.tourApiClient = tourApiClient;
        this.contentTypeMapper = contentTypeMapper;
        this.placeRepository = placeRepository;
        this.placeTranslationRepository = placeTranslationRepository;
        this.placeWriter = placeWriter;
    }

    public EngMatchResult matchRegion(Region region) {
        List<Place> places = placeRepository.findByRegionIdInWithTags(List.of(region.getId()));
        Map<String, List<Place>> byName = new HashMap<>();
        for (Place place : places) {
            String key = KoreanNameExtractor.normalize(place.getTitleKo());
            if (key != null) {
                byName.computeIfAbsent(key, k -> new ArrayList<>()).add(place);
            }
        }
        // 기존 en 번역 행을 미리 로드해 공식 캐싱(src_content_id·src_modified_at)을 확인한다.
        Map<Long, PlaceTranslation> enByPlaceId = placeTranslationRepository
                .findByLangAndPlaceIdIn(LANG_EN, places.stream().map(Place::getId).toList())
                .stream()
                .collect(Collectors.toMap(PlaceTranslation::getPlaceId, Function.identity()));

        Set<Long> matchedIds = new HashSet<>();
        Counters counters = new Counters();
        int pageNo = 1;
        List<AreaBasedSyncItem> page;
        do {
            page = tourApiClient.fetchAreaBasedSyncListEng(
                    region.getLdongRegnCd(), region.getLdongSignguCd(), pageNo, PAGE_SIZE);
            for (AreaBasedSyncItem item : page) {
                matchItem(item, byName, matchedIds, enByPlaceId, counters);
            }
            pageNo++;
        } while (page.size() == PAGE_SIZE);

        return new EngMatchResult(counters.matched, counters.unchanged, counters.failed);
    }

    private void matchItem(
            AreaBasedSyncItem item,
            Map<String, List<Place>> byName,
            Set<Long> matchedIds,
            Map<Long, PlaceTranslation> enByPlaceId,
            Counters counters) {
        try {
            Optional<PlaceCategory> engCategory = contentTypeMapper.categoryOfEng(item.contenttypeid());
            if (engCategory.isEmpty()) {
                // 여행코스(75) 등 우리가 수집하지 않는 타입 — 매칭 대상 아님.
                return;
            }
            String key = KoreanNameExtractor.normalize(KoreanNameExtractor.extract(item.title()));
            if (key == null) {
                return;
            }
            List<Place> sameName = byName.get(key);
            if (sameName == null) {
                return;
            }
            List<Place> available = sameName.stream()
                    .filter(p -> !matchedIds.contains(p.getId()))
                    .toList();
            if (available.isEmpty()) {
                return;
            }

            BigDecimal itemLat = PlaceCollectionService.parseCoordinate(item.mapy());
            BigDecimal itemLng = PlaceCollectionService.parseCoordinate(item.mapx());
            Optional<Place> selected = selectCandidate(available, itemLat, itemLng, engCategory);
            if (selected.isEmpty()) {
                return;
            }

            Place place = selected.get();
            // 이 국문 place는 이 run에서 소진한다(1:1 보장) — 상세 조회 성공 여부와 무관하게,
            // 같은 이름의 다른 영문 항목이 같은 place를 다시 집지 않도록.
            matchedIds.add(place.getId());
            enrich(place, item, enByPlaceId.get(place.getId()), counters);
        } catch (RuntimeException e) {
            counters.failed++;
            log.warn("영문 매칭 항목 {} 처리 중 오류 — 건너뜀: {}", item.contentid(), e.getMessage());
        }
    }

    private void enrich(Place place, AreaBasedSyncItem item, PlaceTranslation existingEn, Counters counters) {
        LocalDateTime engModifiedAt = PlaceCollectionService.parseModifiedTime(item.modifiedtime());
        // 영문 캐싱: 같은 영문 contentId로 이미 공식 콘텐츠를 붙였고 modifiedtime이 안 바뀌었으면 상세 재조회 생략.
        if (existingEn != null
                && existingEn.getSource() == TranslationSource.OFFICIAL
                && item.contentid().equals(existingEn.getSrcContentId())
                && existingEn.getSrcModifiedAt() != null
                && engModifiedAt != null
                && !engModifiedAt.isAfter(existingEn.getSrcModifiedAt())) {
            counters.unchanged++;
            return;
        }
        Optional<DetailCommonItem> engDetail = tourApiClient.fetchDetailCommonEng(item.contentid());
        if (engDetail.isEmpty()) {
            // sourceModifiedAtEn을 저장하지 않으므로 다음 run에서 자동 재시도된다.
            counters.failed++;
            return;
        }
        placeWriter.applyEnglishOfficial(place, engDetail.get(), item.contentid(), engModifiedAt);
        counters.matched++;
    }

    /**
     * 이름이 일치하는 후보 중 하나를 고른다(정밀 우선). 없으면 스킵(Optional.empty).
     * 단일 후보면 좌표 가드만 확인하고, 복수 후보면 카테고리 일치 → 좌표 최근접 순으로 좁힌다.
     */
    static Optional<Place> selectCandidate(
            List<Place> sameNameCandidates,
            BigDecimal itemLat,
            BigDecimal itemLng,
            Optional<PlaceCategory> engCategory) {
        if (sameNameCandidates.isEmpty()) {
            return Optional.empty();
        }
        List<Place> pool = sameNameCandidates;
        if (pool.size() > 1 && engCategory.isPresent()) {
            List<Place> byCategory = pool.stream()
                    .filter(p -> p.getCategory() == engCategory.get())
                    .toList();
            if (!byCategory.isEmpty()) {
                pool = byCategory;
            }
        }
        if (pool.size() == 1) {
            return withinCoordGuard(pool.get(0), itemLat, itemLng)
                    ? Optional.of(pool.get(0))
                    : Optional.empty();
        }
        // 복수 후보가 남으면 좌표로만 가른다. 좌표가 없으면 애매하므로 스킵(오매칭 방지).
        if (itemLat == null || itemLng == null) {
            return Optional.empty();
        }
        Place best = null;
        double bestKm = Double.MAX_VALUE;
        for (Place p : pool) {
            if (p.getLat() == null || p.getLng() == null) {
                continue;
            }
            double km = GeoUtils.haversineKm(
                    itemLat.doubleValue(), itemLng.doubleValue(), p.getLat().doubleValue(), p.getLng().doubleValue());
            if (km < bestKm) {
                bestKm = km;
                best = p;
            }
        }
        return (best != null && bestKm <= COORD_GUARD_KM) ? Optional.of(best) : Optional.empty();
    }

    // 좌표가 양쪽 다 있으면 가드 거리 이내여야 매칭을 허용한다. 한쪽이라도 결측이면 이름만으로 인정.
    private static boolean withinCoordGuard(Place place, BigDecimal itemLat, BigDecimal itemLng) {
        if (itemLat == null || itemLng == null || place.getLat() == null || place.getLng() == null) {
            return true;
        }
        double km = GeoUtils.haversineKm(
                itemLat.doubleValue(),
                itemLng.doubleValue(),
                place.getLat().doubleValue(),
                place.getLng().doubleValue());
        return km <= COORD_GUARD_KM;
    }

    private static final class Counters {
        int matched;
        int unchanged;
        int failed;
    }

    public record EngMatchResult(int matched, int unchanged, int failed) {}
}
