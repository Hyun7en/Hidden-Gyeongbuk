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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
     * 언어 중립 내부 타입. 소스의 contentTypeId는 언어마다 코드가 달라(ko=12, en=76 …) 여기
     * 저장하지 않는다 — 원본은 staging에 남고, (placeType, lang) → sourceTypeId 매핑은
     * DB가 아니라 설정 파일에 둔다(아키텍처 §4.3). 언어 추가가 마이그레이션을 유발하지 않게 하기 위함.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "place_type", length = 20, nullable = false)
    private PlaceType placeType;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 50)
    private String tel;

    @Column(name = "first_image", length = 500)
    private String firstImage;

    // 관광공사 신분류체계 대/중/소분류. 현재는 저장만 하며, 이 코드로 place_tag를 파생하는
    // 규칙은 미정이다 — 추천 가중치 35%가 걸린 항목이라 매핑 품질이 추천 품질을 좌우한다(ERD §9).
    @Column(name = "lcls_systm1", length = 10)
    private String lclsSystm1;

    @Column(name = "lcls_systm2", length = 10)
    private String lclsSystm2;

    @Column(name = "lcls_systm3", length = 20)
    private String lclsSystm3;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "place", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlaceTag> tags = new ArrayList<>();
}
