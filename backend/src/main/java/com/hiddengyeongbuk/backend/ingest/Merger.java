package com.hiddengyeongbuk.backend.ingest;

import com.hiddengyeongbuk.backend.domain.place.ResolutionStatus;
import com.hiddengyeongbuk.backend.domain.place.StagingPlace;
import com.hiddengyeongbuk.backend.domain.place.StagingPlaceRepository;
import com.hiddengyeongbuk.backend.domain.sync.SyncPhase;
import com.hiddengyeongbuk.backend.support.PlaceDataChangedEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 수집 3단계 — MATCHED 판정된 staging 그룹을 언어 중립 {@code place} 1건 +
 * 언어별 {@code place_translation} 행으로 병합한다(ADR-016, 외부 API 연동 설계서 §2.6.4).
 *
 * <p><b>외부 API를 호출하지 않는다</b> — 자유롭게 재실행할 수 있다.
 *
 * <p>쓰기는 {@link MergeWriter}(별도 빈)가 그룹 단위로 독립 커밋한다. 전체를 트랜잭션 하나로
 * 묶으면 중간의 DB 오류 하나가 rollback-only를 걸어 전량을 날린다.
 */
@Service
public class Merger {

    private static final Logger log = LoggerFactory.getLogger(Merger.class);

    private final StagingPlaceRepository stagingPlaceRepository;
    private final IdentityGrouper grouper;
    private final MergeWriter mergeWriter;
    private final StalePlaceSweeper stalePlaceSweeper;
    private final SyncLogger syncLogger;
    private final ApplicationEventPublisher eventPublisher;

    public Merger(
            StagingPlaceRepository stagingPlaceRepository,
            IdentityGrouper grouper,
            MergeWriter mergeWriter,
            StalePlaceSweeper stalePlaceSweeper,
            SyncLogger syncLogger,
            ApplicationEventPublisher eventPublisher) {
        this.stagingPlaceRepository = stagingPlaceRepository;
        this.grouper = grouper;
        this.mergeWriter = mergeWriter;
        this.stalePlaceSweeper = stalePlaceSweeper;
        this.syncLogger = syncLogger;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param places 새로 만든 place 수. 이미 있던 그룹은 place를 재사용하므로 여기 세지 않는다
     * @param stalePlacesRemoved 근거를 잃어 지운 place 수. 재판정으로 그룹 구성이 바뀌면
     *     승자 아닌 place가 남는데, 그게 목록에 중복으로 뜨므로 정리한다(StalePlaceSweeper)
     */
    public record Result(
            int places, int translations, int failed, int quarantined, int stalePlacesRemoved) {

        String breakdown() {
            return "places=%d, translations=%d, quarantined=%d, failed=%d, stalePlacesRemoved=%d"
                    .formatted(places, translations, quarantined, failed, stalePlacesRemoved);
        }
    }

    public Result merge() {
        List<StagingPlace> matched = stagingPlaceRepository.findByResolutionStatus(ResolutionStatus.MATCHED);
        if (matched.isEmpty()) {
            return new Result(0, 0, 0, 0, 0);
        }

        // Resolver와 같은 결정적 판정을 다시 돌려 그룹을 복원한다 — 판정 결과를 담을 컬럼이
        // resolved_place_id 하나뿐이고 그건 place가 생긴 뒤에야 채울 수 있기 때문이다.
        IdentityGrouper.Grouping grouping = grouper.group(matched);

        int places = 0;
        int translations = 0;
        int failed = 0;
        int quarantined = 0;
        for (List<StagingPlace> group : grouping.groups()) {
            // 그룹 하나의 실패가 나머지를 막지 않도록 격리한다. 실패한 그룹은 resolved_place_id가
            // 안 채워지므로 다음 Merge에서 자동 재시도된다.
            try {
                MergeWriter.Written written = mergeWriter.write(group);
                places += written.placeCreated() ? 1 : 0;
                translations += written.translations();
                quarantined += written.quarantined() ? 1 : 0;
            } catch (RuntimeException e) {
                failed++;
                log.warn("그룹 병합 실패 (앵커 staging id={})", group.get(0).getId(), e);
            }
        }

        // 병합이 끝난 뒤에 쓸어낸다 — 승자 place가 확정되기 전에 지우면 살아 있어야 할 것을 지운다.
        StalePlaceSweeper.Result swept = stalePlaceSweeper.sweep();

        Result result = new Result(places, translations, failed, quarantined, swept.deletedPlaces());
        log.info(
                "Merge 완료: places={}, translations={}, failed={}, quarantined={}, stalePlacesRemoved={}",
                places, translations, failed, quarantined, swept.deletedPlaces());
        // quarantined는 실패가 아니라 정상 판정 결과다(lclsSystm1 결측 보류, ADR-024 결과 ⑦).
        // 그래서 내역에만 넣고 failMessage로는 올리지 않는다 — 올리면 정상 실행이 PARTIAL로 기록된다.
        syncLogger.record(
                SyncPhase.MERGE,
                null,
                "MATCHED 전체",
                grouping.groups().size(),
                result.breakdown(),
                failed > 0 ? failed + "건 병합 실패" : null);

        // 조회 캐시가 stale로 남지 않도록 알린다. ingest는 catalog를 직접 참조하지 않으므로
        // 이벤트로만 통신한다.
        if (places > 0 || translations > 0 || swept.deletedPlaces() > 0) {
            eventPublisher.publishEvent(new PlaceDataChangedEvent(this));
        }
        return result;
    }
}
