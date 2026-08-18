package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 2단계 — staging 행들 사이에서 동일 장소를 판정해 상태를 기록한다(ADR-016).
 *
 * <p><b>외부 API를 전혀 호출하지 않는다.</b> 그래서 자유롭게 재실행할 수 있고, 이것이 staging을
 * 두는 가장 큰 이유다 — 매칭 로직을 고쳐도 오퍼레이션별 일 1,000건 한도를 다시 쓰지 않는다.
 *
 * <p>판정 자체는 {@link IdentityGrouper}가 한다. 여기서는 그 결과를 MATCHED/QUARANTINED로
 * 기록하는 것까지만 하고, place 생성은 Merger가 맡는다.
 */
@Service
public class Resolver {

    private static final Logger log = LoggerFactory.getLogger(Resolver.class);

    private final StagingPlaceRepository stagingPlaceRepository;
    private final IdentityGrouper grouper;
    private final SyncLogger syncLogger;
    private final ContentTypeMapping contentTypes;

    public Resolver(
            StagingPlaceRepository stagingPlaceRepository,
            IdentityGrouper grouper,
            SyncLogger syncLogger,
            ContentTypeMapping contentTypes) {
        this.stagingPlaceRepository = stagingPlaceRepository;
        this.grouper = grouper;
        this.syncLogger = syncLogger;
        this.contentTypes = contentTypes;
    }

    /**
     * showflag=0. null은 "모름"이라 대상이 아니다 — 이 컬럼이 생기기 전에 수집한 행이 그
     * 상태이고, 그걸 미표출로 읽으면 확인도 안 한 장소를 지우게 된다.
     */
    private static boolean hiddenAtSource(StagingPlace staging) {
        return Integer.valueOf(0).equals(staging.getShowFlag());
    }

    /**
     * 더는 수집 대상이 아닌 타입인가. 설정({@code app.content-type.mappings})이 유일한 근거라
     * 대상을 줄이면 <b>이미 쌓인 staging 행도 자동으로</b> 판정에서 빠진다 — 음식점 제외
     * (ADR-025)처럼 설정만 고치고 재판정하면 끝나게 하기 위함이다.
     *
     * <p>staging 행 자체는 지우지 않는다. 원본 보존이 재매칭의 근거이고(ADR-016), 결정이
     * 바뀌면 설정을 되돌려 재판정하는 것만으로 복구된다 — 외부 API를 다시 부르지 않는다.
     */
    private boolean notCollectedAnymore(StagingPlace staging) {
        return !contentTypes.isCollectable(staging.getLang(), staging.getSourceTypeId());
    }

    public record Result(int matched, int quarantined, int excluded, int groups) {

        /** quarantined는 실패가 아니라 정상 판정 결과라 내역에 넣는다 — failMessage가 아니다. */
        String breakdown() {
            return "matched=%d, quarantined=%d, excluded=%d, groups=%d"
                    .formatted(matched, quarantined, excluded, groups);
        }
    }

    /**
     * PENDING 행을 전부 판정한다.
     *
     * <p>PENDING만 보는 것은 의도적이다. 원본이 바뀐 행은 Collector가 PENDING으로 되돌리므로
     * 여기 들어오고, 안 바뀐 행은 이전 판정을 유지해 이미 병합된 장소를 다시 건드리지 않는다.
     * 매칭 로직을 고쳐서 전면 재판정을 하려면 상태를 일괄 PENDING으로 되돌리면 된다.
     */
    @Transactional
    public Result resolve() {
        List<StagingPlace> pending = stagingPlaceRepository.findByResolutionStatus(ResolutionStatus.PENDING);
        if (pending.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }

        // 소스가 내린 행(showflag=0)과 더는 수집하지 않는 타입은 판정 대상에서 뺀다. 여기서
        // 빼면 MATCHED가 되지 않고, MATCHED가 아니면 StalePlaceSweeper가 그 place를 근거 없는
        // 것으로 보고 지운다 — 별도 정리 로직이 필요 없는 이유다.
        Predicate<StagingPlace> excludable =
                staging -> hiddenAtSource(staging) || notCollectedAnymore(staging);
        List<StagingPlace> excluded = pending.stream().filter(excludable).toList();
        excluded.forEach(staging -> {
            staging.setResolutionStatus(ResolutionStatus.EXCLUDED);
            staging.setQuarantineReason(null);
        });
        List<StagingPlace> resolvable = pending.stream().filter(excludable.negate()).toList();

        IdentityGrouper.Grouping grouping = grouper.group(resolvable);

        int matched = 0;
        for (List<StagingPlace> group : grouping.groups()) {
            for (StagingPlace member : group) {
                member.setResolutionStatus(ResolutionStatus.MATCHED);
                member.setQuarantineReason(null);
                matched++;
            }
        }
        grouping.quarantined().forEach((staging, reason) -> {
            staging.setResolutionStatus(ResolutionStatus.QUARANTINED);
            staging.setQuarantineReason(reason);
        });

        stagingPlaceRepository.saveAll(pending);

        Result result =
                new Result(matched, grouping.quarantined().size(), excluded.size(), grouping.groups().size());
        log.info(
                "Resolve 완료: matched={}, quarantined={}, excluded={}, groups={}",
                result.matched(),
                result.quarantined(),
                result.excluded(),
                result.groups());
        syncLogger.record(SyncPhase.RESOLVE, null, "PENDING 전체", pending.size(), result.breakdown(), null);
        return result;
    }

    /** 매칭 로직을 고친 뒤 전면 재판정할 때 쓴다 — 외부 API 재호출 없이 staging만으로 돈다. */
    @Transactional
    public int resetAllToPending() {
        List<StagingPlace> all = stagingPlaceRepository.findAll();
        all.forEach(staging -> {
            staging.setResolutionStatus(ResolutionStatus.PENDING);
            staging.setQuarantineReason(null);
        });
        stagingPlaceRepository.saveAll(all);
        return all.size();
    }
}
