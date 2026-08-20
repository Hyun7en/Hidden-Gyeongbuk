package com.hiddengyeongbuk.backend.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * place의 언어별 번역/공식 콘텐츠. 언어를 컬럼이 아니라 "행"으로 관리한다 — 언어를 추가해도
 * 스키마 변경 없이 (place_id, lang) 행만 늘어난다. 국문도 예외 없이 여기 들어간다.
 *
 * place와의 연관은 @ManyToOne 대신 place_id 스칼라로 둔다 — 조회 시엔 이미 place를 들고 있어
 * 역방향 탐색이 필요 없고, @MapsId 연관의 detached/낙관적락 함정을 피한다.
 */
@Entity
@Table(
        name = "place_translation",
        indexes = {
            @Index(name = "uk_place_translation_place_lang", columnList = "place_id, lang", unique = true),
            @Index(name = "idx_place_translation_lang_source", columnList = "lang, source")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PlaceTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    /** BCP 47. zh-CN·zh-TW 같은 지역 하위태그를 수용하려면 5자로는 부족하다(ADR-018). */
    @Column(name = "lang", length = 10, nullable = false)
    private String lang;

    /** 번역 행이 있는데 제목이 없는 상태를 막는다 — 조회 폴백이 제목을 항상 찾을 수 있어야 한다. */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Lob
    @Column(name = "overview", columnDefinition = "LONGTEXT")
    private String overview;

    @Column(name = "addr", length = 255)
    private String addr;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private TranslationSource source;

    // 공식(OFFICIAL) 콘텐츠의 원본 서비스 contentId와 modifiedtime — 값이 그대로면 상세 재조회를 생략한다.
    @Column(name = "src_content_id", length = 20)
    private String srcContentId;

    @Column(name = "src_modified_at")
    private LocalDateTime srcModifiedAt;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    /**
     * 제목 LLM localization을 마친 시각(ADR-026). NULL이면 아직 안 했다는 뜻이고, 그것이
     * 재실행 시 건너뛸지를 정하는 유일한 근거다 — {@code source}에는 엔진을 기록하지 않고
     * {@code generated_at}은 모든 행에 이미 채워져 있어 구별에 쓸 수 없다.
     */
    @Column(name = "title_localized_at")
    private LocalDateTime titleLocalizedAt;
}
