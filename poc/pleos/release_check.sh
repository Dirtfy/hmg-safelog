#!/usr/bin/env bash
# 제출용 release APK(apk/*_release.apk, 저장소에 커밋된 그 파일)를 Pleos Connect Emulator에 설치·실행해 확인한다.
# 확인: 서명·debuggable 아님, 설치, LiveActivity 실행, Virtual Console로 R단 저속 가속(브레이크 없음) → 페달 경고·기록, P → 주행 요약.
set -u
O="$OUT"; mkdir -p "$O"
PKG=com.hmg.safelog.poc
VC="python3 poc/pleos/vc.py"
a() { timeout 60 adb shell "$@" 2>&1; }
APK=$(ls apk/*_release.apk | head -1)
{ echo "APK=$APK"; sha256sum "$APK"; ls -l "$APK"; } > "$O/apk.txt"
APKSIGNER=$(ls $ANDROID_HOME/build-tools/*/apksigner | sort -V | tail -1)
$APKSIGNER verify --verbose --print-certs "$APK" > "$O/apksigner.txt" 2>&1
AAPT=$(ls $ANDROID_HOME/build-tools/*/aapt | sort -V | tail -1)
$AAPT dump badging "$APK" | head -3 > "$O/badging.txt"; $AAPT dump badging "$APK" | grep -c "application-debuggable" >> "$O/badging.txt"
a wm size > "$O/wm.txt"; a wm density >> "$O/wm.txt"
a getprop > "$O/getprop.txt"
adb forward tcp:8088 tcp:8088
python3 poc/pleos/vc.py heartbeat &
$VC wait_ready > "$O/vc_ready.txt"
a pm uninstall $PKG > /dev/null
timeout 120 adb install -r -g "$APK" > "$O/install.txt" 2>&1
a dumpsys package $PKG | grep -E "versionName|versionCode|flags=|pkgFlags" > "$O/package.txt"
$VC gear Park; $VC controls 0 0 100
adb logcat -c
timeout 300 adb logcat -v epoch > "$O/logcat.txt" 2>&1 &
a am start -W -n $PKG/com.hmg.safelog.app.LiveActivity --ez route true > "$O/am_start.txt"
sleep 20
a pidof $PKG > "$O/pid.txt"
timeout 30 adb exec-out screencap -p > "$O/r01_start.png"
# 마트 주차장 후진: R단, 브레이크 뗌, 1초 동안 0→15km/h (가속 페달), 브레이크, P
$VC gear Reverse; sleep 5
$VC controls 0 0 0; sleep 2
$VC ramp 0 15 1.0 60 0; sleep 3
timeout 30 adb exec-out screencap -p > "$O/r02_warning.png"
$VC controls 15 0 100; $VC ramp 15 0 3.0 0 100; sleep 2
$VC gear Park; sleep 6
a am start -n $PKG/com.hmg.safelog.app.LiveActivity > /dev/null; sleep 4
timeout 30 adb exec-out screencap -p > "$O/r03_parked.png"
a pidof $PKG >> "$O/pid.txt"
sleep 2
grep -E "SafeLog|AndroidRuntime|FATAL" "$O/logcat.txt" > "$O/logcat_safelog.txt"
{ for k in LIVE_START SIG SPEAK EVENT_SAVED TRIP_SUMMARY FATAL; do echo "$k $(grep -c "$k" "$O/logcat_safelog.txt")"; done; } | tee "$O/summary.txt"
