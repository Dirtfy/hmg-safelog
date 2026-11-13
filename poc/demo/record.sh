#!/usr/bin/env bash
# Runs inside android-emulator-runner (emulator already booted). Records the DemoActivity.
# Output dir: $OUT (default demo-out)
set -u
OUT="${OUT:-demo-out}"
SCENARIO="${SCENARIO:-demo_mart_reverse}"
PKG=com.hmg.safelog.poc
ACT=$PKG/com.hmg.safelog.app.DemoActivity
mkdir -p "$OUT"
APK=$(ls poc/app/build/outputs/apk/debug/*.apk | head -1)
echo "APK=$APK"

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell getprop ro.build.version.sdk | tee "$OUT/sdk.txt"

adb install -r -t "$APK" || { echo "install failed"; exit 1; }

# 16:9 landscape car-display-like screen, Korean locale for text
adb shell wm size 1920x1080
adb shell wm density 240
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# suppress the one-time "Viewing full screen" immersive-mode hint so it does not cover the recording
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell wm size | tee "$OUT/wm_size.txt"
adb shell fc-list 2>/dev/null | head -0
adb shell ls /system/fonts | grep -i -E 'cjk|korean|nanum|droidsans' | tee "$OUT/fonts.txt"

adb logcat -c

rm -f "$OUT/pull.log"
adb shell "screenrecord --bit-rate 8000000 --size 1920x1080 --time-limit 170 /sdcard/demo.mp4" > "$OUT/screenrecord.log" 2>&1 &
SR=$!
sleep 4
START=$(date +%s)
adb shell am start -W -n "$ACT" --es scenario "$SCENARIO" | tee "$OUT/am_start.txt"

has() { adb logcat -d -s SafeLog | grep -q -- "$1"; }
wait_for() { # pattern timeout_s
  local i=0
  while [ $i -lt "$2" ]; do has "$1" && return 0; sleep 1; i=$((i+1)); done
  echo "TIMEOUT waiting for: $1"; return 1
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1 at +$(( $(date +%s) - START ))s"; }

wait_for "SCREEN driving" 30
sleep 1;  shot 01_driving_idle
wait_for "SPEAK 오른발" 20 ; sleep 1; shot 02_parking_prompt
wait_for "SPEAK 다른 페달" 40; sleep 1; shot 03_pedal_warning
sleep 1; shot 03b_pedal_warning
wait_for "SCENARIO_END" 40
wait_for "SCREEN record" 20; sleep 4.5; shot 04_record
wait_for "SCREEN review" 30; sleep 2; shot 05_review
wait_for "DEMO_DONE" 30
sleep 2

pkill_sr() { adb shell pkill -INT screenrecord || adb shell killall -2 screenrecord; }
pkill_sr
sleep 5
wait $SR 2>/dev/null
adb pull /sdcard/demo.mp4 "$OUT/demo.mp4"
adb logcat -d -v time -s SafeLog > "$OUT/safelog_logcat.txt"
adb logcat -d -v time '*:E' > "$OUT/errors_logcat.txt"
ls -la "$OUT"
cat "$OUT/safelog_logcat.txt"

# Pass 2: frame capture (same app, same engine, virtual clock) -> smooth 10 fps PNG sequence
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am start -W -n "$ACT" --es scenario "$SCENARIO" --ez frames true
wait_for "FRAMES_DONE" 900
adb logcat -d -v time -s SafeLog > "$OUT/frames_logcat.txt"
cat "$OUT/frames_logcat.txt"
adb exec-out run-as "$PKG" tar cf - files/frames > "$OUT/frames.tar"
ls -la "$OUT/frames.tar"
tar tf "$OUT/frames.tar" | wc -l
[ -s "$OUT/demo.mp4" ] && [ -s "$OUT/frames.tar" ]
