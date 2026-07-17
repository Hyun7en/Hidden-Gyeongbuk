package com.hiddengyeongbuk.backend.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "place_summary")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PlaceSummary {

    @Id
    @Column(name = "place_id")
    private Long placeId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "place_id")
    private Place place;

    @Lob
    @Column(name = "summary_en", nullable = false, columnDefinition = "LONGTEXT")
    private String summaryEn;

    @Column(name = "title_en")
    private String titleEn;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private SummarySource source;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;
}
