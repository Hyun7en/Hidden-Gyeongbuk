package com.hiddengyeongbuk.backend.support;

import org.springframework.context.ApplicationEvent;

/**
 * place/place_summary가 배치 수집·백필로 바뀌었을 때 발행한다. `ingest`가 발행하고 `catalog`가
 * 구독해 캐시를 비우는 식으로 두 패키지가 서로 몰라도 되게 한다(직접 의존 대신 이벤트로 결합).
 */
public class PlaceDataChangedEvent extends ApplicationEvent {
    public PlaceDataChangedEvent(Object source) {
        super(source);
    }
}
