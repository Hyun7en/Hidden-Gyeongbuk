package com.hiddengyeongbuk.backend.ingest.translate.title;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 제목 localization 작업 파일의 위치와 안전장치(ADR-026).
 *
 * <p><b>여기 있는 것은 사람이 읽고 쓰는 산출물뿐이다.</b> 검수 결과와 교정이 그렇다 —
 * 교정을 DB에 두면 한 건 추가할 때마다 마이그레이션이 붙고(contentTypeId 매핑을 설정에 둔 것과
 * 같은 이유, 아키텍처 §4.3), 사람이 눈으로 검토하기도 어렵다.
 *
 * <p>반대로 <b>"이 제목을 처리했는가"는 파일이 아니라 {@code place_translation.title_localized_at}
 * 컬럼이다</b>. 그건 사람이 읽을 것이 아니라 그 행의 상태이고, 언어가 늘어도 스키마가 그대로다.
 *
 * <p>기본값 {@code ../data}는 bootRun의 작업 디렉터리가 {@code backend/}라는 전제다 —
 * 저장소 루트의 {@code data/}를 가리킨다.
 *
 * <p><b>{@code workDir}를 {@link Path}로 바인딩하지 말 것.</b> Spring이 String → Path 변환을
 * 리소스 경로 변환기로 처리하는데, 앞의 {@code ..}를 정규화하다 null로 만들고 기동이 실패한다
 * (2026-08-20 실측: {@code The resource path [/../data] has been normalized to [null]}).
 * 문자열로 받아 {@link Path#of}로 우리가 바꾼다.
 *
 * @param workDir 대장·검수 결과·교정 파일을 두는 디렉터리
 * @param maxLength 생성 제목 길이 상한. {@code place_translation.title}이 VARCHAR(200)이라
 *     넘으면 적재가 실패한다 — 넘어오면 버리고 기존 제목을 남긴다
 * @param batchSize 한 호출에 묶어 보낼 장소 수. <b>이 값이 작업 시간을 지배한다</b> — 1건씩
 *     보내면 1,105건에 3.7시간이지만 20건씩이면 56회 호출로 12분이다
 * @param pacingMillis 호출 간 간격(ms). <b>무료 티어가 분당 5회</b>라 12초가 하한이고,
 *     기본값은 여유를 둔 13초다(2026-08-20 실측: 0.4초로 두었다가 8건째에 429로 막혔다)
 */
@ConfigurationProperties(prefix = "app.title")
public record TitleProperties(
        @DefaultValue("../data") String workDir,
        @DefaultValue("200") int maxLength,
        @DefaultValue("20") int batchSize,
        @DefaultValue("13000") long pacingMillis) {

    Path dir() {
        return Path.of(workDir);
    }

    /** 전수 검수 결과. 사람이 SUSPECT만 골라 보는 입력이다. */
    Path reviewFile(String lang) {
        return dir().resolve("title-review-" + lang + ".json");
    }

    /** 사람이 확정한 교정. 유일하게 손으로 쓰는 파일이다. */
    Path overridesFile(String lang) {
        return dir().resolve("title-overrides-" + lang + ".yml");
    }
}
