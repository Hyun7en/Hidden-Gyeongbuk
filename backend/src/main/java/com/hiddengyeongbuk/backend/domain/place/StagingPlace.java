package com.hiddengyeongbuk.backend.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TourAPI 수집 원본. 언어별 서비스 응답을 <b>변환 없이</b> 적재한다(ADR-016, ERD §3.1).
 * 언어별 테이블을 만들지 않고 {@code lang} 컬럼으로 구분하므로, 언어를 늘려도 행만 늘어난다.
 *
 * <p><b>임시 테이블이 아니라 재매칭의 근거다.</b> 오퍼레이션별 일 1,000건 한도에서 3,000건
 * 재수집은 며칠이 걸린다. staging이 없으면 매칭 로직을 고칠 때마다 그 비용을 치러야 하므로,
 * 최신 수집분을 상시 보존한다(언어당 1세트, 이력은 누적하지 않음 — 아키텍처 §7).
 *
 * <p>이 단계에서는 매칭·판단을 하지 않는다. 동일 장소 판정은 Resolver가, place/place_translation
 * 병합은 Merger가 맡는다.
 */
@Entity
@Table(
        name = "staging_place",
        indexes = {
            @Index(
                    name = "uk_staging_place_lang_src",
                    columnList = "lang, source_content_id",
                    unique = true),
            @Index(name = "idx_staging_place_status_lang", columnList = "resolution_status, lang"),
            @Index(name = "idx_staging_place_coord", columnList = "lat, lng")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class StagingPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staging_id")
    private Long id;

    /** 수집 언어. BCP 47 (ko, en, ja, zh-CN…). */
    @Column(name = "lang", length = 10, nullable = false)
    private String lang;

    /**
     * 해당 언어 서비스의 contentid. 언어마다 ID 공간이 달라(실측: 영문 "Andong Dam"의 contentId를
     * KorService2로 조회하면 NO_DATA) 언어를 빼고는 의미가 없다 — 그래서 유니크도
     * (lang, source_content_id) 복합이다.
     */
    @Column(name = "source_content_id", length = 20, nullable = false)
    private String sourceContentId;

    /** 해당 언어 서비스의 contenttypeid를 원본 그대로(ko=12, en=76 …). 내부 타입 변환은 Merge에서. */
    @Column(name = "source_type_id", nullable = false)
    private Integer sourceTypeId;

    @Column(name = "ldong_regn_cd", length = 2)
    private String ldongRegnCd;

    /** Merge 시 region 매칭에 사용한다. */
    @Column(name = "ldong_signgu_cd", length = 3)
    private String ldongSignguCd;

    @Column(name = "title", length = 200)
    private String title;

    @Lob
    @Column(name = "overview", columnDefinition = "LONGTEXT")
    private String overview;

    @Column(name = "addr", length = 255)
    private String addr;

    /** mapy. 좌표는 언어 무관이라 Resolution의 1순위 키다. */
    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    /** mapx. */
    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    /**
     * 원본 그대로. <b>전화번호가 아닌 값이 오기도 한다</b> — 영문 축제는 이 칸에
     * "• 1330 Travel Hotline: …<br>(Korean, English, …)" 같은 안내문을 넣는다(실측 111~113자).
     * 그래서 폭을 전화번호 길이로 잡을 수 없다(V6에서 50 → 255).
     */
    @Column(length = 255)
    private String tel;

    @Column(name = "first_image", length = 500)
    private String firstImage;

    /**
     * detailImage2가 준 이미지 URL 목록을 줄바꿈으로 이어 원본 순서 그대로 담는다.
     * 정규화는 Merge가 {@code place_image}로 한다 — staging은 해석하지 않는 계층이다.
     */
    @Lob
    @Column(name = "detail_images", columnDefinition = "LONGTEXT")
    private String detailImages;

    /**
     * detailImage2에 <b>물어봤다</b>는 표시. 받은 개수와 무관하게 응답이 오면 찍는다.
     *
     * <p>개수로 판단하면 이미지가 없는 장소를 매 수집마다 영원히 다시 묻는다 — overview에서
     * 이미 겪은 함정과 같다("응답은 왔는데 비어 있다"와 "응답을 못 받았다"는 다르다).
     * 원본이 바뀌면 {@code StagingWriter}가 null로 되돌려 다시 묻게 한다.
     */
    @Column(name = "image_fetched_at")
    private LocalDateTime imageFetchedAt;

    /** detailIntro2의 eventstartdate. 언어 무관한 값이라 어느 언어 행에서 와도 같아야 한다. */
    @Column(name = "event_start_date")
    private LocalDate eventStartDate;

    /** detailIntro2의 eventenddate. */
    @Column(name = "event_end_date")
    private LocalDate eventEndDate;

    /**
     * detailIntro2에 물어봤다는 표시. {@code imageFetchedAt}과 따로 두는 이유는 두 오퍼레이션이
     * <b>서로 다른 호출 예산</b>을 쓰고 독립적으로 실패하기 때문이다.
     */
    @Column(name = "intro_fetched_at")
    private LocalDateTime introFetchedAt;

    /**
     * TourAPI areaBasedSyncList2의 showflag. 1=표출, 0=미표출(소스가 내림), null=아직 안 받음.
     *
     * <p>동기화 목록이 일반 목록과 따로 있는 이유가 이 값이다 — 소스에서 내려간 항목도 표시를
     * 달아 계속 내려보내야, 데이터를 복사해 둔 쪽이 "삭제됨"과 "이번 페이지에 없음"을 구별할 수
     * 있다. 2026-08-18 실측으로 showflag=0인 항목은 일반 목록(areaBasedList2)에 하나도 없음을
     * 확인했다(울릉 0/6, 영양 0/9).
     *
     * <p><b>null은 "미표출"이 아니라 "모름"이다.</b> 이 컬럼이 생기기 전에 수집한 행이 그 상태이며,
     * null을 미표출로 읽으면 확인도 안 한 장소를 지우게 된다.
     *
     * <p>담는 값이 0/1뿐이라 컬럼은 {@code TINYINT}인데, Hibernate는 {@code Integer}를 기본으로
     * {@code INTEGER}에 대응시켜 {@code ddl-auto: validate}가 기동을 막는다. 필드 타입을
     * {@code Byte}로 낮추면 호출부가 전부 어색해지므로 <b>매핑 쪽을 컬럼에 맞춘다.</b>
     */
    @Column(name = "show_flag")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer showFlag;

    @Column(name = "lcls_systm1", length = 10)
    private String lclsSystm1;

    @Column(name = "lcls_systm2", length = 10)
    private String lclsSystm2;

    @Column(name = "lcls_systm3", length = 20)
    private String lclsSystm3;

    /**
     * TourAPI modifiedtime. 언어별 증분 수집 워터마크이며, <b>행별로</b> 비교해 값이 그대로면
     * 상세조회(detailCommon2)를 생략한다 — 전역 워터마크가 아니다. 호출 한도 절약의 핵심.
     */
    @Column(name = "source_modified_at")
    private LocalDateTime sourceModifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", length = 20, nullable = false)
    @Builder.Default
    private ResolutionStatus resolutionStatus = ResolutionStatus.PENDING;

    /**
     * 판정 결과 연결된 place. MATCHED일 때만 존재한다.
     *
     * <p>@ManyToOne이 아니라 스칼라로 둔다 — place_translation과 같은 이유(@MapsId 연관의
     * detached/낙관적락 함정 회피)에 더해, Merger가 배치로 도는 경로라 지연 로딩된 연관을
     * 들고 다닐 이유가 없다. DB에는 FK 제약이 걸려 있으므로 참조 무결성은 보장된다.
     */
    @Column(name = "resolved_place_id")
    private Long resolvedPlaceId;

    /** 보류 사유(복수 후보 / 이름 추출 불가 / 좌표 없음 등). 수동 검토의 근거. */
    @Column(name = "quarantine_reason", length = 200)
    private String quarantineReason;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
