# SafeLog PoC 판정표

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
| 공유 기본 OFF / 운전자 주도 공유 | 구현됨 | core `SharePolicy`, `EventAlertPolicy` | 기본 동의 시 연락처 발송 0건 테스트 |
| 한국어 TTS | 구현됨(빌드 검증) | `TextToSpeech(context, Mode.HYBRID)`, `setLocale(Locale.KOREA)`, `speak(String, Boolean)` | 실기기 음성 출력 미검증 |

## 한계
- 실차/에뮬레이터 실행 없음: 컴파일 + JVM 단위 테스트만 검증. Pleos 권한(`pleos.car.permission.CAR_DRIVE`, `CAR_SAFETY`)과 `android.car.permission.CAR_SPEED`는 매니페스트에 선언, 실제 부여는 미검증.
- SDK 시그니처 특이점: `Vehicle.brake/gear/safety` 프로퍼티 문법은 컴파일 오류(private) -> `getBrake()` 등 명시 호출 필요. `TelltaleInfoListener.onTelltaleInfoUpdated(Telltales?)`는 nullable. 리스너는 모두 `VehicleListener.onFailed(Exception)` 구현 필요. `TelltaleData.color`는 Int. `android.car` 옵션 라이브러리 `useLibrary("android.car")`는 컴파일 통과.
