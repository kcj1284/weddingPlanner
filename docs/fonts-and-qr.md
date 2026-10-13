# 청첩장 글꼴·QR 자산

폰트 원본 출처: https://github.com/google/fonts 의 ofl/nanummyeongjo, ofl/nanumgothic, ofl/nanumpenscript, ofl/nanumbrushscript, ofl/gowundodum, ofl/gowunbatang.

2026-10-12 Regular TTF를 받아 WOFF2 형식으로 변환했다. 글리프 내용은 변경하지 않았다. 브라우저가 선택한 글꼴을 필요할 때만 자체 서버에서 내려받으며 외부 CDN은 사용하지 않는다. 원본 SIL Open Font License는 src/main/resources/static/fonts/*-OFL.txt에 포함한다. 6개 WOFF2의 총 크기는 약 3.1MB다.

QR은 [ZXing](https://github.com/zxing/zxing) core 3.5.3 (Apache License 2.0)을 사용해 서버에서 PNG로 생성한다. URL을 외부 서버로 전송하거나 방문하지 않는다. 사각·둥근·도트 패턴 × 사각·둥근·원형 프레임 9종을 2개 URL에 대해 디코딩 검증했다. 읽기용 finder와 quiet zone은 고정한다.
