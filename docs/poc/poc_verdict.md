# SafeLog PoC 판정표

**2026-11-14 갱신 — Pleos Connect Emulator 2.1.0 실제 실행 결과** (GitHub Actions `pleos-probe`, `poc/pleos/record_live.sh` → v14부터 `record_vc.sh`: Virtual Console 웹 화면 조작)

| 항목 | Pleos 에뮬레이터 결과 | 근거 / 조치 |
|---|---|---|
| 브레이크·기어 수신 | 확인 | Virtual Console → `Brake.registerBrakePedalStatus`, `Gear.registerGearSelection` 콜백 (지연: 기어 중앙값 0.8초) |
| 차속 수신 | 확인 | Virtual Console 속도 → `CarPropertyManager` `PERF_VEHICLE_SPEED` (중앙값 0.35초, 90% 1.8초). 콜백은 별도 스레드로 받도록 수정 |
| 페달 오조작 경고 | 확인 | R단 0→15km/h(1초, 브레이크 없음)에서 "다른 페달, 브레이크를 밟으세요" + 기록 저장. **빠른 정상 출발(5초에 40km/h, D단)에서도 경고** → 기준 재조정 필요 |
| R/D 출발 안내 | 확인 | "오른발이 브레이크 위에 있는지 확인하세요" (10분 1회) |
| 주행 요약 | 확인 | 신호 몰림으로 급제동 과다 집계(23회) 발견 → 1초 이상 구간 가속도로 수정(테스트 추가). 앱 시작 때 기어 미수신(UNKNOWN)을 주행으로 세던 문제, 기어·속도 콜백 스레드 경쟁으로 한 주행을 두 번 세던 문제도 수정 |
| Pleos TTS 발화 | 확인 | `TTS_VIA pleos` 로그. `pleos.car.permission.TTS_SERVICE` 미선언 시 앱 종료 → 추가 |
| 경고등 수신 | **불가(권한)** | `registerTelltales`·`getTelltalesInfo` 모두 `PRIVATE_CAR_SAFETY` 요구 → 서드파티 앱 SecurityException. 앱은 격리 처리, 시연은 값을 브로드캐스트로 넣음 |
| 경고등 쉬운 말 해설 + 정비소 경유지 | 구현됨(로직·NaviHelper 호출 확인) | core `TelltaleGuide`/`TelltaleExplainer`, `VoiceReply`; `NaviHelper.requestRoute`/`addWaypoint` 호출. 에뮬레이터 지도 데이터가 없어 경유지 목록은 비어 있음 |
| 오늘 돌아보기 + 가족 자동 전송 | 확인 | 주행 종료(P)마다 `SharePolicy.onTripEnd` → `TRIP_SUMMARY`에 동의한 가족에게만 `DayReview.shareCard()`(횟수만, 위치·경로 없음). 에뮬레이터에서 `SHARE_SENT auto` 로그 확인. 실제 메시지 발송 채널은 없음 |
| Virtual Console 웹 화면 조작 | 확인(주의) | 기어는 브레이크를 밟은 채 바꿔야 유지됨. 페이지가 속도를 계산해 보냄. GPU 없는 러너에서는 3D 뷰가 CPU를 다 써서 막고 2D로. 앱 쪽 기어·브레이크 해제 콜백이 가끔 안 와서 재확인 필요 |
| 주행 중 화면 | 제한됨 | DO 미인정(사이드로드) 앱은 주행·R단에서 화면 제한, 액티비티 재생성 → 상태를 `LiveSession`으로. DO 선언 추가(마켓 심사 후 인정 예상) |
| 음성 인식(STT) | 미검증 | 에뮬레이터에 마이크 없음 → 인식된 문장을 브로드캐스트로 넣어 시연 |

---
이하는 v7 판정표(컴파일·단위 테스트 기준)입니다.

CI: https://github.com/Dirtfy/hmg-safelog/actions/runs/41285531757 (core-test 성공: 20 tests, 0 실패 / app-build 성공: assembleDebug APK)
(이전 실행 41285189888: core 1건 실패(시나리오 설계 오류), app 컴파일 오류 -> 수정 후 통과)

| 기능 | 판정 | 근거(클래스·메서드) | 비고 |
|---|---|---|---|
| 페달 안심 (오조작 의심 감지 + "다른 페달, 브레이크를 밟으세요") | 대체 구현 | 속도: AAOS `CarPropertyManager` + `VehiclePropertyIds.PERF_VEHICLE_SPEED`(Pleos Vehicle SDK에 속도 API 없음); 브레이크: `Vehicle.getBrake().registerBrakePedalStatus`; 기어: `getGear().registerGearSelection` | **가속페달 API 없음** -> 속도 급상승 + 브레이크 미작동으로 추정. 임계값은 PedalGuardConfig. 실차 속도 갱신율 미검증 |
| 이벤트 기록(전 10초/후 5초, JSON lines) | 구현됨 | core `PedalGuard`/`EventStore`, app `FileEventStore` | 테스트로 검증 |
| 주차 출발 확인 (P->R/D) | 구현됨 | `GearSelectionListener.onSelected`, core `ParkingStartCheck` (10분 1회) | |
| 본인 피드백(주행 요약) | 구현됨 | core `TripTracker`/`TripSummary.toKorean()` | 속도 적분 거리; 운전자 본인에게만 표시 |
| 동의 기반 알림(에어백) | 구현됨(빌드 검증) | `Safety.registerSeatAirbagDeployed(SeatArea(SeatValue.ROW_1_LEFT), SeatAirbagDeployedListener)` | 운전석만 등록; 다른 좌석은 확장 필요. 실제 연락처 전송 채널(SMS 등) 미구현 - 인터페이스만 |
| 경고등 N일 지속 리마인드 | 구현됨(빌드 검증) | `Safety.registerTelltales(TelltaleInfoListener)`, `TelltaleType` ABS/ESC/AIRBAG/ENGINE/ENGINE_OIL_PRESSURE/TPMS/MASTER_WARNING | 상태 영속화(앱 재시작 후 지속일 계산) 미구현 |
| 가족 자동 전송(동의한 가족만) | 구현됨 | core `SharePolicy.onTripEnd`, `EventAlertPolicy` | 동의 없는 연락처 발송 0건, `TRIP_SUMMARY` 동의 가족만 수신 테스트 |
| 한국어 TTS | 구현됨(빌드 검증) | `TextToSpeech(context, Mode.HYBRID)`, `setLocale(Locale.KOREA)`, `speak(String, Boolean)` | 실기기 음성 출력 미검증 |

## 한계
- 실차/에뮬레이터 실행 없음: 컴파일 + JVM 단위 테스트만 검증. Pleos 권한(`pleos.car.permission.CAR_DRIVE`, `CAR_SAFETY`)과 `android.car.permission.CAR_SPEED`는 매니페스트에 선언, 실제 부여는 미검증.
- SDK 시그니처 특이점: `Vehicle.brake/gear/safety` 프로퍼티 문법은 컴파일 오류(private) -> `getBrake()` 등 명시 호출 필요. `TelltaleInfoListener.onTelltaleInfoUpdated(Telltales?)`는 nullable. 리스너는 모두 `VehicleListener.onFailed(Exception)` 구현 필요. `TelltaleData.color`는 Int. `android.car` 옵션 라이브러리 `useLibrary("android.car")`는 컴파일 통과.
