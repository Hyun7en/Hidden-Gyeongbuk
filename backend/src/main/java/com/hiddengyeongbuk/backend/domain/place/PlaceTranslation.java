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
 * 스키마 변경 없이 (place_id, lang) 행만 늘어난다. place의 언어별 컬럼과 place_summary를 대체.
 *
 * place와의 연관은 @ManyToOne 대신 place_id 스칼라로 둔다 — 조회 시엔 이미 place를 들고 있어
 * 역방향 탐색이 필요 없고, @MapsId 연관의 detached/낙관적락 함정(PlaceSummary에서 겪음)을 피한다.
 */
@Entity
@Table(
        name = "place_translation",
        indexes = {
            @Index(name = "uk_place_translation_place_lang", columnList = "place_id, lang", unique = true),
            @Index(name = "idx_place_translation_lang", columnList = "lang")
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

    @Column(name = "lang", length = 5, nullable = false)
    private String lang;

    @Column(name = "title", length = 200)
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
}
