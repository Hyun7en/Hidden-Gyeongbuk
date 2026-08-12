package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import java.util.List;
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

    public Resolver(
            StagingPlaceRepository stagingPlaceRepository, IdentityGrouper grouper, SyncLogger syncLogger) {
        this.stagingPlaceRepository = stagingPlaceRepository;
        this.grouper = grouper;
        this.syncLogger = syncLogger;
    }

    public record Result(int matched, int quarantined, int groups) {}

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
            return new Result(0, 0, 0);
        }

        IdentityGrouper.Grouping grouping = grouper.group(pending);

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

        Result result = new Result(matched, grouping.quarantined().size(), grouping.groups().size());
        log.info(
                "Resolve 완료: matched={}, quarantined={}, groups={}",
                result.matched(),
                result.quarantined(),
                result.groups());
        syncLogger.record(SyncPhase.RESOLVE, null, "PENDING 전체", pending.size(), null);
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
