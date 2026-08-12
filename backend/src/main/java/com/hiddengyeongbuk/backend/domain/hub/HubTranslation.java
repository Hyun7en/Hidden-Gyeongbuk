package com.hiddengyeongbuk.backend.domain.hub;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * hub의 언어별 표시명·성격 설명. 국문도 예외 없이 여기 들어간다(ERD §1.2 — 언어 중립/의존 분리).
 *
 * <p>place_translation과 같은 이유로 hub 연관을 @ManyToOne이 아니라 hub_id 스칼라로 둔다 —
 * @MapsId 연관의 detached/낙관적락 함정을 피하고, 조회 시엔 이미 hub를 들고 있어 역방향 탐색이 없다.
 */
@Entity
@Table(
        name = "hub_translation",
        indexes =
                @Index(
                        name = "uk_hub_translation_hub_lang",
                        columnList = "hub_id, lang",
                        unique = true))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class HubTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    /** BCP 47 (ko, en, ja, zh-CN…). 언어를 늘려도 스키마가 아니라 행이 늘어난다(ADR-018). */
    @Column(name = "lang", length = 10, nullable = false)
    private String lang;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    /** 거점 성격 (전통·자연·사찰 등). */
    @Column(name = "description", length = 255)
    private String description;
}
