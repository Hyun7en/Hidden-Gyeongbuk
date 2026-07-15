package com.hiddengyeongbuk.backend.domain.region;

import com.hiddengyeongbuk.backend.domain.hub.Hub;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "region",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_region_ldong_code",
                columnNames = {"ldong_regn_cd", "ldong_signgu_cd"}),
        indexes = @Index(name = "idx_region_hub", columnList = "hub_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_id")
    private Long id;

    @Column(name = "ldong_regn_cd", length = 2, nullable = false)
    private String ldongRegnCd;

    @Column(name = "ldong_signgu_cd", length = 3, nullable = false)
    private String ldongSignguCd;

    @Column(name = "name_ko", length = 50, nullable = false)
    private String nameKo;

    @Column(name = "name_en", length = 50)
    private String nameEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id", nullable = false)
    private Hub hub;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;
}
