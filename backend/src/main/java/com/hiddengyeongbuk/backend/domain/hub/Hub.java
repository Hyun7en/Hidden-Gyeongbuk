package com.hiddengyeongbuk.backend.domain.hub;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * 교통 거점. 언어 중립 엔티티라 표시명·성격 설명을 갖지 않는다 — 그것들은 {@link HubTranslation}
 * 행으로 간다(ERD §3.4). 여기 남는 건 접근성 점수의 기준점인 좌표와, 설정·URL 파라미터가
 * 참조하는 내부 코드뿐이다.
 */
@Entity
@Table(
        name = "hub",
        uniqueConstraints = @UniqueConstraint(name = "uk_hub_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hub_id")
    private Long id;

    /** ANDONG / DONGDAEGU / POHANG / NORTH_INLAND. 설정과 공유 URL이 이 값으로 거점을 지목한다. */
    @Column(name = "code", length = 30, nullable = false)
    private String code;

    // 좌표는 접근성 점수(Haversine, ADR-004)의 기준점이라 결측을 허용하지 않는다.
    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7, nullable = false)
    private BigDecimal lng;
}
