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

/**
 * 인구감소지역. 표시명은 {@link RegionTranslation} 행으로 간다(ERD §3.6).
 */
@Entity
@Table(
        name = "region",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_region_ldong_code",
                        columnNames = {"ldong_regn_cd", "ldong_signgu_cd"}),
        indexes = @Index(name = "idx_region_hub_active", columnList = "hub_id, active"))
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

    /** 법정동 시도코드 (경북 = 47). */
    @Column(name = "ldong_regn_cd", length = 2, nullable = false)
    private String ldongRegnCd;

    /** 법정동 시군구코드. staging의 같은 코드로 Merge 시 지역을 결정한다. */
    @Column(name = "ldong_signgu_cd", length = 3, nullable = false)
    private String ldongSignguCd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id", nullable = false)
    private Hub hub;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    /**
     * 인구감소지역 지정 여부. 지정이 해제돼도 데이터를 지우지 않고 false로 전환한다 —
     * Collector가 active=true 행만 순회하므로, 지정 변경에 행 추가·수정으로 대응하고
     * 코드를 고치지 않는다(NFR-06).
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
