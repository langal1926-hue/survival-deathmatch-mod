# Zombie Apocalypse SMP — TaCZ 총기 14종 및 부착물 화이트리스트

Minecraft **1.21.1** / NeoForge **21.1.264** / TaCZ 비공식 포트 **1.1.8-hotfix-r7**.

전체 TaCZ 모드 자체는 수정하지 않습니다. 대신 우리 모드가 **서버에서** TaCZ 아이템의 실제 총기 ID와 장착된 부착물 ID를 검사합니다. 게임 내 레지스트리 ID `tacz:modern_kinetic_gun`만 확인하면 모든 총기가 같은 아이템으로 취급되므로, 반드시 **`IGun.getGunId(ItemStack)`와 `IAttachment.getAttachmentId(ItemStack)`**로 구분해야 합니다.

## 최종 허용 목록

| 계열 | 게임상 총기 (실제 TaCZ ID) | 허용 조준경 | 허용 소음기 |
| --- | --- | --- | --- |
| 권총 | Glock 17 — `tacz:glock_17` | `tacz:sight_acro_pistol` | `tacz:muzzle_silencer_mirage` |
| 권총 | M1911 — `tacz:m1911` | 기본 가늠쇠 | `tacz:muzzle_silencer_mirage` |
| 권총 | Desert Eagle — `tacz:deagle` | `tacz:sight_t1` | `tacz:muzzle_silencer_wraith` |
| SMG | MP5A5 — `tacz:hk_mp5a5` | `tacz:sight_t2` | `tacz:muzzle_silencer_ursus` |
| SMG | UMP45 — `tacz:ump45` | `tacz:sight_t2` | `tacz:muzzle_silencer_phantom_s1` |
| AR | AK47 — `tacz:ak47` | `tacz:sight_okp7` | `tacz:muzzle_silencer_ursus` |
| AR | M4A1 — `tacz:m4a1` | `tacz:sight_552` | `tacz:muzzle_silencer_knight_qd` |
| AR | AUG — `tacz:aug` | **기본 내장** `tacz:scope_aug_default` | `tacz:muzzle_silencer_knight_qd` |
| AR | SCAR-H — `tacz:scar_h` | `tacz:scope_acog_ta31` | `tacz:muzzle_silencer_phantom_s1` |
| 산탄총 | DB-2 Durin — `tacz:db_short` | 기본 가늠쇠 | 장착 불가 |
| 산탄총 | M870 — `tacz:m870` | 기본 가늠쇠 | `tacz:muzzle_silencer_sg` |
| 산탄총 | M1014 — `tacz:m1014` | `tacz:sight_coyote` | `tacz:muzzle_silencer_sg` |
| 저격총 | M700 — `tacz:m700` | `tacz:scope_standard_8x` | `tacz:muzzle_silencer_ursus` |
| 저격총 | AWM 게임상 AWP — `tacz:ai_awp` | `tacz:scope_mk5hd` | `tacz:muzzle_silencer_ursus` |

이전 제안 중 **TaCZ 원본에서 장착할 수 없었던 조합**은 정확한 장착 태그에 따라 조정했습니다.

- Desert Eagle: RMR → T1, Vulture → Wraith
- MP5A5: T1 → T2
- AK47: Wraith → Ursus
- AUG: Ptilopsis → Knight QD, 조준경은 별도 루팅이 아닌 **기본 내장형**
- SCAR-H: Vulture → Phantom S1
- AWP: Wraith → Ursus

이 변경은 **총기 종류 14종을 변경하지 않고** 호환되지 않는 부착물을 호환되는 모델로 바꾸는 것입니다.

## 서버 제한 규칙

- **허용한 14종 이외 TaCZ 총기**: 실제 서버 발사 이벤트를 취소하고, 플레이어 인벤토리에서 제거합니다.
- **허용되지 않은 단독 TaCZ 부착물**: 바닥 아이템 줍기를 차단하고, 이미 인벤토리에 있으면 제거합니다.
- **허용한 부착물도 잘못된 총에 설치하면**: 다음 서버 검사 시 총은 보존하고 금지 부착물만 제거합니다. 발사 직전에도 장착 조합을 확인해 취소합니다.
- **나머지 부착물**(레이저, 손잡이, 확장탄창 등): 현재 서버 규칙에서는 전부 금지합니다.
- 5틱마다 서버에서 플레이어 **인벤토리 + UI 커서 슬롯**을 검사하여 크리에이티브·명령어·별도 모드 지급도 처리합니다.
- TaCZ 본체의 총기 제작대 4종 상호작용 금지는 기존대로 유지합니다.
- **총성 감지 거리**: 기본 64블록 / 선택한 소음기 장착시 **28블록**. TaCZ의 실제 총기 사운드·사격 효과는 그대로 사용합니다.
- TaCZ 기본 탄약 및 탄약 상자는 이번 총기/부착물 화이트리스트 범위에서 제한하지 않았습니다. **탄약 획득/제작 차단, 채굴·제작 금지, 상자 60분 재보급, 상인**은 이후 구현 단계입니다.
- 금지 장비가 **월드 상자 내부에 남아 있는 경우** 서버는 상자 내용까지 자동 삭제하지 않습니다. 플레이어가 인벤토리로 옮기거나 주우면 검사를 적용합니다. 서버 관리자 보관함·기타 모드 전용 인벤토리에는 별도 처리 작업이 필요합니다.
- 크리에이티브 인벤토리에 TaCZ 원본의 다른 총이 **표시되는 현상은 정상**입니다. UI 숨김이 아닌 **서버 보안 검사**를 우선 구현했습니다.

## 편집 방법

`src/main/resources/data/zombieapocalypse/tacz_weapon_allowlist.json`을 변경하면 서버 허용 목록을 고칠 수 있습니다. 이 파일은 빌드될 때 모드 JAR에 포함되며 **서버 재시작** 후 적용됩니다. 수정한 뒤에는 다음 검사를 실행해야 합니다.

```powershell
python scripts/check_tacz_weapon_catalog.py
.\\gradlew.bat build
```

검증 스크립트는 **Modrinth에서 지정된 TaCZ 1.21.1 r7 JAR를 다운로드하고 SHA-512를 검증한 후**, 원본 총기 데이터 ID, 부착물 ID, 유형, 태그의 중첩 참조를 직접 비교합니다. 아카이브는 검사 후 삭제되며 GitHub에 올리지 않습니다.

## 인게임 검증 권장

1. 기존대로 GitHub Desktop Pull → `.\\gradlew.bat runClient` 실행.
2. 허용된 Glock 17, MP5, AK47, AWM 등을 지급해 조준·발사·재장전 확인.
3. 허용되지 않은 M107을 들면 몇 틱 내에 서버에서 제거되는지 확인.
4. 허용된 Glock 17에 ACRO와 Mirage를 장착하고 정상 발사되는지 확인.
5. 다른 소음기/그립/레이저를 장착하려 시도해 제거 및 발사 차단 확인.
6. 64블록 부근 감염자와 28블록 부근 감염자로 소음기 장착 전후 청각 반응 확인.
7. 멀티플레이 환경에서 **서버/클라이언트 동일 모드팩**으로 재시험.

**주의:** 빌드·정적 규칙 검증은 인게임 또는 20~30인 동시접속 테스트를 대신하지 않습니다.
