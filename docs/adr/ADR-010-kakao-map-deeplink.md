# ADR-010 · 지도 = Kakao 표시 + 외부 딥링크

**상태** Accepted  
**작성일자** 2026-05-29

## 컨텍스트

Google 지도는 한국에서 저정밀 데이터·길찾기 한계(고정밀 지도 반출 보류 지속)가 있고, Kakao/Naver는 한국 내 길찾기·POI 데이터가 완비돼 있다. 주 사용자는 외국인이다.

## 결정

지도 표시는 Kakao Maps API(무료·한국 POI 정확)를 쓴다. 길찾기(FR-C-05)는 MVP에서 인앱 라우팅 대신 외부 앱 딥링크(Google·Kakao)로 연결한다.

## 근거

Kakao는 한국 렌더링·POI가 정확하고 무료다. 외국인 친숙도는 Google Maps 딥링크 병행으로 보완한다.

## 결과

Kakao JS SDK를 연동하고 좌표는 TourAPI 좌표를 사용한다. 길찾기 버튼은 외부 딥링크로 처리한다.

## 대안

Google Maps(표시·길찾기) — 한국 길찾기·POI 한계; Naver Maps — 가능하나 Kakao와 유사, JS SDK 편의로 Kakao 우선 → Kakao 표시 + 외부 딥링크.
