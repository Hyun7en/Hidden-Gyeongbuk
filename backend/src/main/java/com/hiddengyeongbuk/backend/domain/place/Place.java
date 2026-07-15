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

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PlaceCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "title_ko", length = 200, nullable = false)
    private String titleKo;

    @Column(name = "title_en", length = 200)
    private String titleEn;

    @Column(length = 255)
    private String addr;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(length = 50)
    private String tel;

    @Lob
    @Column(name = "overview_ko")
    private String overviewKo;

    @Lob
    @Column(name = "overview_en")
    private String overviewEn;

    @Column(name = "first_image", length = 500)
    private String firstImage;

    @Column(name = "has_en", nullable = false)
    private boolean hasEn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
