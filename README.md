# 세이프로그 (SafeLog) — HMG 차량용 앱 개발 공모전 (Pleos Connect)

브레이크·기어·차속 신호로 주차장·후진 같은 저속 순간의 페달 실수를 음성으로 먼저 알리고, 그 순간을 **운전자 본인의 기록**으로 남기는 Pleos 앱.
타깃은 Pleos 차량의 모든 운전자이고, 핵심 페르소나는 만 65세 이상 운전자입니다. 차량 제어는 하지 않으며, 공유는 운전자가 직접 누를 때만(기본 꺼짐) 합니다.

## 구조
| 경로 | 내용 |
|---|---|
| `docs/plan/기획서_세이프로그_v7.docx` | 제출용 기획서 — 공모전 공식 접수양식(form.docx) 원본에 02 항목 1~8 기입 + 별지 1~4 |
| `docs/plan/기획서_세이프로그_v7.pdf` | 같은 내용의 PDF |
| `docs/research/` | 근거 자료(조사 노트 md), Pleos SDK 공개 API 시그니처 추출본 |
| `docs/poc/poc_verdict.md` | PoC 구현 가능성 판정표 (구현됨 / 대체 구현 / 불가) |
| `poc/core` | 순수 Kotlin 로직: `PedalGuard`, `ParkingStartCheck`, `TripFeedback`, `AlertPolicy`(공유·동의 알림) + JUnit5 테스트 |
| `poc/app` | Android 어댑터: Pleos Vehicle SDK(Brake·Gear·Safety), TextToSpeech, AAOS `CarPropertyManager`(PERF_VEHICLE_SPEED) |
| `poc/sim/scenarios` | 가짜 신호 시나리오 CSV (오조작·정상 주차·고속 가속·브레이크 밟은 가속·에어백·경고등 3일 등) |
| `.github/workflows/ci.yml` | `:core:test` + `:app:assembleDebug` |

## PoC 결과 요약
- CI: https://github.com/Dirtfy/hmg-safelog/actions/runs/41285531757. core 단위 테스트는 20건 모두 통과했고, 실제 Pleos SDK(Vehicle 2.3.2, TextToSpeech 3.3.1)로 APK 컴파일에 성공했습니다.
- **대체 구현:** Vehicle SDK에는 가속 페달 API가 없습니다. 그래서 "R/D단 + 15km/h 이하에서 1초 안에 +8km/h + 브레이크 미작동" 조건으로 오조작을 추정합니다. 차속은 AAOS 경로로 받습니다.
- **미검증:** 실차·에뮬레이터 실행, 권한 승인, 콜백 주기, 실제 음성 출력. 연락처 전송 채널은 인터페이스만 만들었습니다.
- 빌드 정책: 서버에서는 빌드하지 않고, 컴파일·테스트는 GitHub Actions에서만 실행합니다.
