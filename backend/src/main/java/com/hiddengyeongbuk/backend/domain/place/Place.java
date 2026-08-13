package com.hiddengyeongbuk.backend.domain.place;

import com.hiddengyeongbuk.backend.domain.region.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 장소 — 언어 중립 엔티티(ERD §3.2).
 *
 * <p><b>언어에 의존하는 값은 하나도 두지 않는다.</b> 제목·설명·주소는 전부
 * {@link PlaceTranslation} 행에 있으며 국문도 예외가 아니다. 구 스키마는 국문을 특별대우해
 * (title_ko·overview_ko·addr를 컬럼으로) 국문 서비스에 없는 장소를 표현할 수 없었다.
 *
 * <p><b>자연키가 없다.</b> 정체성은 Identity Resolution 결과로만 결정되며 외부 식별자에
 * 의존하지 않는다. TourAPI contentId는 언어마다 공간이 달라(실측) 언어 중립 계층에 둘 수 없고,
 * {@code staging_place.source_content_id}와 {@code place_translation.src_content_id}에만 존재한다.
 * Merge 시 중복 생성 방지는 {@link StagingPlace#getResolvedPlaceId()}가 담당한다.
 */
@Entity
@Table(
        name = "place",
        indexes = {
            @Index(name = "idx_place_region_type", columnList = "region_id, place_type"),
            @Index(name = "idx_place_coord", columnList = "lat, lng")
        })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    /**
     * 관광공사 신분류체계 대분류 코드(ADR-024). 소스의 {@code lclsSystm1}이 그대로 들어온다 —
     * 언어 무관한 값이라 언어 중립 계층에 두어도 문제가 없다.
     *
     * <p>소스의 contentTypeId는 언어마다 코드가 달라(ko=12, en=76 …) 여기 저장하지 않는다.
     * 원본은 staging에 남고, 그 매핑은 이제 <b>어떤 타입을 수집할지</b>와 <b>lclsSystm1 결측 시
     * 폴백</b>만 담당한다(설정 파일, 아키텍처 §4.3).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", length = 10, nullable = false)
    private PlaceType placeType;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 50)
    private String tel;

    @Column(name = "first_image", length = 500)
    private String firstImage;

    // lcls_systm1/2/3 컬럼은 없다. 대분류는 placeType으로 흡수됐고, 중·소분류는 원본이
    // staging_place에 남아 있다 — Merge는 외부 호출이 없어 자유롭게 재실행 가능하므로,
    // 세분 필터가 필요해지면 그때 컬럼을 추가하고 재Merge하면 된다(측정 전 비정규화 금지).

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
