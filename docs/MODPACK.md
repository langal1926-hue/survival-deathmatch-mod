# Zombie Apocalypse SMP 모드팩 — 배포·설치 가이드

## 🎮 참가자에게는 단 하나의 `.mrpack` 파일만 전달

Minecraft 1.21.1 / NeoForge 21.1.264, TaCZ 1.1.8-hotfix-r7, Zombie Apocalypse SMP 0.2.0 개발용 스냅샷.

**두 모드를 하나의 JAR로 합치는 것이 아닙니다.** 런처가 둘을 함께 설치하므로 손 파지·발사·재장전·사운드 등은 TaCZ 원본 렌더링 엔진을 그대로 이용하고, 우리 모드는 감염자 AI·루팅·상인/경제 등을 담당합니다.

- **우리 자체 모드 JAR:** 자동 빌드 결과가 `.mrpack`의 `overrides/mods/`에 포함됩니다.
- **TaCZ 모드 JAR:** `modrinth.index.json`에 정확한 Modrinth 버전 `62q6mB5Q`, 다운로드 URL, SHA-1/SHA-512를 저장합니다. 사용자가 팩을 가져오면 **런처가 자동 다운로드/검증**합니다. TaCZ를 따로 찾아서 설치할 필요가 없습니다.
- **Minecraft / NeoForge:** `modrinth.index.json`의 `minecraft=1.21.1`, `neoforge=21.1.264`를 기준으로 런처가 설치합니다.
- TaCZ 파일은 공식 Modrinth CDN에서 변형하지 않고 다운로드하며 **우리 GitHub 저장소나 배포용 JAR에 TaCZ 에셋을 재업로드하지 않습니다**.

## GitHub에서 새 모드팩 파일 받기

1. [GitHub 저장소 Actions](https://github.com/langal1926-hue/survival-deathmatch-mod/actions/workflows/build.yml) 열기.
2. **main 브랜치에서 성공한 최신 Build** 실행 선택.
3. 페이지 아래쪽 **Artifacts → `Zombie-Apocalypse-SMP-Modpack`** 다운로드.
4. GitHub가 제공한 ZIP 압축을 풀고 **`Zombie-Apocalypse-SMP.mrpack` 파일 하나**를 꺼냅니다.
5. **Modrinth App** 또는 **Prism Launcher**에서 `.mrpack`을 **파일에서 가져오기/Import**로 불러옵니다. Minecraft, NeoForge, 두 모드가 자동 설치됩니다.
6. 게임 시작 → Mods에서 TaCZ 및 Zombie Apocalypse SMP가 모두 있는지 확인 → 테스트 서버 접속.

**참고:** GitHub Actions 아티팩트는 보관 기간이 30일입니다. 공개 배포용으로 확정한 버전은 나중에 GitHub Releases나 Modrinth에 정식 파일로 올리는 것이 좋습니다. Modrinth 플랫폼 업로드 시에는 해당 플랫폼의 제3자 모드 포함 정책과 별도 라이선스 계약을 확인해야 합니다.

## 개발자용

일반 개발자는 기존처럼 GitHub Desktop으로 소스 갱신 후 `.\\gradlew.bat runClient`를 실행할 수 있습니다. TaCZ는 이미 개발용 Gradle 의존성으로 자동 내려받게 설정되어 있습니다.

빌드 서버에서 한 파일 모드팩을 만들려면:

```powershell
.\\gradlew.bat build
python scripts/build_modpack.py --jar-dir build/libs --output dist/Zombie-Apocalypse-SMP.mrpack --commit 0123456789abcdef
```

모드팩 스크립트는 **검증된 TaCZ Modrinth 메타데이터**를 온라인에서 조회하고, 컴파일된 우리 모드 JAR를 삽입합니다. URL/해시/버전/파일 경로를 검증하므로 외부 메타데이터가 올바르지 않으면 안전하게 실패합니다.

## 전용 서버

`.mrpack`은 기본적으로 **플레이어용 런처 설치 파일**입니다. 전용 서버는 동일한 NeoForge 버전과 두 모드의 **정확히 일치하는 JAR**를 설치해야 합니다. 운영 서버 구성·맵 데이터·서버 설정·플레이어 개인 파일/월드 데이터는 이 팩에 포함하지 않았습니다.

## 아직 개발 중인 사항

- TaCZ 총기 14종 제한과 [채굴·제작 금지](SURVIVAL_RULES.md)는 구현했습니다. 건축자재 상인 판매는 다음 단계입니다.
- 맵 지정 상자만 **60분 간격으로 재보급**, 플레이어 설치 상자와 개인 창고는 영구 제외
- 감염자 잔해 파밍, 암상인, 특수 보급품 및 경제 시스템

모드팩 제공이 이 기능들이 완료되었다는 의미는 아닙니다. 현재 게임에서 정상 작동하는 것으로 확인된 건 TaCZ와 우리 모드의 동시 실행이며, 모드팩 설치 흐름 자체는 각 플레이어의 런처에서 최종 테스트해야 합니다.
