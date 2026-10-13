# 신문형 청첩장 구현 완료

사용자가 제공한 편집 가능한 Figma 복제본 Lu7cJv7S51pIAOZeN3YyE4를 기존 Spring Boot/JavaScript 서비스의 NEWSPAPER 템플릿으로 구현했다. Figma 파일 자체를 수정하지 않았다.

네 A4 지면은 소개·가족, 사진·추억, 인터뷰·예식 준비, 감사 편지로 구성된다. 595 × 842 비율과 장식 자산을 유지하고 이름·날짜·문구와 업로드 사진을 연결한다. 모바일 지면 이동과 크게 읽기를 제공한다. 기존 저장·발행·공유·지도·일정·축의금 계좌와 호환되며 선택 JSON이므로 DB migration은 추가하지 않는다.

원본 작성자 안내에 따라 사용자가 복제한 자료를 사용했다. 사진 자리 표시자는 실제 사진이 아니다. 대표 사진과 갤러리 최대 6장을 지면 슬롯에 반복 배치한다.

page-*-context.txt는 원본 컨텍스트, reference-page-*.png는 Figma 캡처, implementation-page-*.png는 구현, comparison-page-*.png는 나란히 비교 자료다. 최종 결과와 서체 차이는 루트 design-qa.md에 기록했다.
