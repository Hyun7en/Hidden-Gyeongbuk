package com.hiddengyeongbuk.backend.ingest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DetailCommonItem(
        String contentid,
        String contenttypeid,
        String title,
        String addr1,
        String overview,
        String mapx,
        String mapy,
        String tel,
        String firstimage) {}
