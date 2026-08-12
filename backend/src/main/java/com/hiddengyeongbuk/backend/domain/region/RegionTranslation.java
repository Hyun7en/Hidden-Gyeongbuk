package com.hiddengyeongbuk.backend.domain.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * region의 언어별 표시명과 소개 글. 국문도 예외 없이 여기 들어간다(ERD §3.7).
 */
@Entity
@Table(
        name = "region_translation",
        indexes =
                @Index(
                        name = "uk_region_translation_region_lang",
                        columnList = "region_id, lang",
                        unique = true))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RegionTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    /** BCP 47. */
    @Column(name = "lang", length = 10, nullable = false)
    private String lang;

    /** ko=안동, en=Andong. 로마자 표기는 국립국어원 표기법. */
    @Column(name = "name", length = 50, nullable = false)
    private String name;

    /**
     * 지역 소개 글 (S2 화면, GET /api/regions/{id}). TourAPI 수집 대상이 아니라 직접 작성해
     * Flyway seed로 투입한다 — 콘텐츠 작업이 끝나기 전까지는 null이다(ERD §9).
     */
    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
