package com.hiddengyeongbuk.backend.ingest.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AreaBasedSyncItem(
        String contentid,
        String contenttypeid,
        String title,
        String addr1,
        String mapx,
        String mapy,
        String tel,
        String firstimage,
        String showflag,
        String modifiedtime,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3) {}
