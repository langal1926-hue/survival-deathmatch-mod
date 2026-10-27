# Zombie Apocalypse SMP

Minecraft 1.21.1 + NeoForge 기반의 멀티플레이 좀비 아포칼립스 모드 프로젝트입니다.

## 개발 환경
- Java 21 (JDK)
- Minecraft 1.21.1
- NeoForge 21.1.264
- Gradle / ModDevGradle

## 프로젝트 목표
- 유튜버 20~30명 규모의 시즌제 PvPvE 생존 서버
- 감염, 좀비 AI, 거점 약탈, 총기, 차량 및 경제 시스템
- 8,000 x 8,000 블록 월드와 좀비 대이동 이벤트

## 진행 상태
- [x] NeoForge 개발 템플릿 설치
- [x] 개발용 Minecraft 클라이언트 실행 확인
- [ ] 모드 ID 및 Java 패키지를 프로젝트용으로 변경
- [ ] 첫 커스텀 좀비 구현
- [ ] 소리에 반응하는 좀비 AI 개발

## 개발용 Minecraft 실행 (Windows)

```powershell
.\gradlew.bat runClient
```

> 현재 게임 코드는 NeoForge 기본 예제 단계입니다. 위에 적힌 게임 기능은 앞으로 차례대로 개발할 계획입니다.
