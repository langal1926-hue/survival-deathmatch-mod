# TaCZ 전체 모드 연동 — Zombie Apocalypse SMP

## 현재 구조 (Minecraft 1.21.1 / NeoForge 21.1.264)

**무기 엔진: TaCZ 1.21.1 NeoForge 비공식 이식판 `1.1.8-hotfix-r7`**

- TaCZ는 **수정하지 않은 JAR 자체로**, 클라이언트와 서버 모두에 별도로 설치합니다.
- Zombie Apocalypse SMP는 우리 고유의 좀비 AI, 감염, 루팅, 상인, PvPvE 등만 담당합니다.
- 모델, 조준, 1·3인칭 손 파지, 재장전, 총성과 탄환, 총기 HUD는 **TaCZ가 전부 직접 처리**합니다.
- TaCZ 쪽에서 **서버의 실제 발사 이벤트 `GunFireEvent`**가 발생하면, 우리 모드가 **64블록 반경의 감염자에게 소리 위치**를 알립니다.
- TaCZ의 4개 총기 제작대는 지금 단계에서 **상호작용 차단**합니다. 일반 생존은 무기 제작이 아니라 **상자 루팅/감염자 잔해/상인** 중심으로 만들 계획입니다.
- 이전 실험용 `zombieapocalypse:g17_pistol`과 `pistol_ammo`는 **세이브 파일을 깨뜨리지 않도록 아이템 ID 등록만 유지**합니다. 이전 자체 G17의 키바인딩/R키/렌더링/HUD/총기 네트워크/로컬 에셋 오버레이는 모두 비활성화되어, 실제 총기로 사용하지 않습니다.
- 이전 `local/licensed_tacz_g17` 개인용 에셋 팩은 **이 구성에서 필요하지 않습니다**. 해당 ZIP/JAR의 파일을 Git에 올리지 마세요.

### 처음 실행 (Windows / IntelliJ)

1. Minecraft/개발용 클라이언트 종료.
2. GitHub Desktop **Fetch origin → Pull origin**.
3. 프로젝트 최상위 위치에서 **`.\gradlew.bat --refresh-dependencies runClient`**. Gradle의 `localRuntime`이 TaCZ 1.21.1 NeoForge JAR를 Modrinth Maven에서 **개발 실행용으로 자동 다운로드/로드**하도록 설정되어 있습니다. 처음에는 인터넷 연결이 필요합니다.
4. 게임 메인 메뉴 Mods 화면에서 **TaCZ (tacz)** 와 **Zombie Apocalypse SMP (zombieapocalypse)** 두 개가 모두 로딩되었는지 확인합니다.
5. 새 **테스트 월드**를 만드세요. TaCZ 1.20.1의 예전 월드 아이템 데이터는 1.21.1 이식판과 호환되지 않을 수 있습니다.
6. 크리에이티브 모드의 TaCZ 총기 탭에서 실제 TaCZ 총/탄약을 시험 지급합니다. 기존 명령어 `/give @s zombieapocalypse:g17_pistol` 은 이제 사용하지 않습니다.
7. 총을 들고 **우클릭 ADS, 발사, R 재장전, F5 3인칭, HUD**를 시험합니다. 총기 입력/애니메이션/발사 소리는 모두 TaCZ 원본 시스템을 써야 합니다.
8. `/summon zombieapocalypse:infected ~12 ~ ~`으로 감염자를 소환하고, **실제 TaCZ 총성**을 들려 반응하는지 확인합니다. 시각 감지와 혼동하지 않도록 감염자 시야 밖에서 실험하면 좋습니다.
9. TaCZ 총기 제작대 4종을 우클릭했을 때 제작 UI가 열리지 않는지 확인합니다.

### 20–30명 멀티플레이 서버

- **서버와 접속자 전원**의 `mods` 폴더에 동일한 TaCZ 1.21.1 NeoForge 이식판 JAR와 우리 Zombie Apocalypse SMP 모드 JAR가 있어야 합니다.
- 개발용 `localRuntime` 설정은 IntelliJ `runClient` / `runServer`를 위한 설정이지, **배포용 좀비 모드 JAR 안에 TaCZ를 포함하는 것이 아닙니다.**
- TaCZ/추가 총기팩 버전을 서버와 클라이언트가 다르게 쓰지 마세요.
- 추가 총기팩은 TaCZ 1.21.1 이식판과의 호환성을 별도로 확인하세요. 일부 1.20.1 총기팩은 **TaCZ Pack Upgrader**를 필요로 합니다.
- **서버에서 총기 14종 및 총기별 허용 부착물 검사**는 [TACZ_WHITELIST.md](TACZ_WHITELIST.md)에서 다룹니다. 일반 제작대·채굴 금지, 루팅 상자 **60분 재보급**, 플레이어 설치 상자 제외, 두 종류 상인은 **추가 구현 단계**입니다.

### 의존성

- Modrinth: https://modrinth.com/mod/tacz-1.21.1/version/1.1.8-hotfix-r7
- 프로젝트 ID: `OypNE65K`, 버전 ID: `62q6mB5Q`
- `build.gradle`에서 `compileOnly` + `localRuntime`으로 다운로드합니다.
- `neoforge.mods.toml`에서 `tacz`를 **BOTH/required**로 선언합니다.
- 원본 코드는 TaCZ GPL-3.0 등의 라이선스 조건을 따릅니다. 이 저장소는 TaCZ 바이너리나 에셋을 재포장하지 않습니다.

## 이후 개발 순서

1. **완료:** 지정 총기 14종 및 조준경·소음기 정확한 ResourceLocation ID를 TaCZ 1.21.1 기본팩과 대조하고 서버 허용 목록 구축.
2. 총기/탄약은 지정된 도시 건물 상자와 감염자 잔해·암상인에서만 등장.
3. 광질·제작·기본 몹 드롭·상인 우회 등 불허 경로를 막는 완전한 서버 규칙.
4. 플레이어 소유 상자와 구분되는 **맵 관리자 파밍 상자**의 60분 재보급 및 서버 저장 데이터.
5. 소음기 사용시 청각 AI 반경과 NPC 경제·탄약 공급 밸런스 튜닝.

## 문제 해결

- **TaCZ를 찾을 수 없음**: Modrinth Maven에 접근 가능한지, Gradle `--refresh-dependencies`, JDK 21인지 확인.
- **모드 불일치**: 사용자 서버/클라이언트가 정확히 동일한 1.21.1 NeoForge TaCZ JAR를 쓰는지 확인.
- **예전 개인용 G17 팩만 설치한 경우**: 별도 TaCZ 모드가 아니므로 이 구성에선 작동하지 않습니다.
- **TaCZ 렌더/총기팩 오류**: TaCZ 이식판은 비공식이므로 프로젝트 저장소의 이슈와 이식판 공식 안내를 확인. 원본 1.20.1 개발팀에 해당 이식판 버그를 제보하지 마세요.
