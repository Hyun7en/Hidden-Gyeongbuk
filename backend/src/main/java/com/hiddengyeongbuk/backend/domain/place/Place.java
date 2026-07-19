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
import jakarta.persistence.Lob;
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

@Entity
@Table(
        name = "place",
        indexes = {
            @Index(name = "idx_place_region_content_type", columnList = "region_id, content_type_id"),
            @Index(name = "idx_place_category", columnList = "category"),
            @Index(name = "uk_place_content_id", columnList = "content_id", unique = true)
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

    @Column(name = "content_id", length = 20, nullable = false)
    private String contentId;

    @Column(name = "content_type_id", nullable = false)
    private Integer contentTypeId;

    // 관광공사 신분류체계 대/중/소분류 코드(목록 응답에 포함). 현재는 저장만 하고, 분류 기반
    // 태그 개편은 후속 단계에서 이 컬럼들을 활용한다.
    @Column(name = "lcls_systm1", length = 10)
    private String lclsSystm1;

    @Column(name = "lcls_systm2", length = 10)
    private String lclsSystm2;

    @Column(name = "lcls_systm3", length = 20)
    private String lclsSystm3;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PlaceCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "title_ko", length = 200, nullable = false)
    private String titleKo;

    @Column(length = 255)
    private String addr;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 50)
    private String tel;

    @Lob
    @Column(name = "overview_ko", columnDefinition = "LONGTEXT")
    private String overviewKo;

    @Column(name = "first_image", length = 500)
    private String firstImage;

    // "en 번역(공식/기계) 존재 여부" 캐시 플래그 — 실제 영문 텍스트는 place_translation에 있다.
    // 추천 완성도 점수가 조인 없이 참조하고, 번역 백필의 재처리 방지(findByHasEnFalse)에도 쓴다.
    @Column(name = "has_en", nullable = false)
    private boolean hasEn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "source_modified_at")
    private LocalDateTime sourceModifiedAt;

    @OneToMany(mappedBy = "place", fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlaceTag> tags = new ArrayList<>();
}
