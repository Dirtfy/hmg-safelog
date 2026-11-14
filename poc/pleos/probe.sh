#!/usr/bin/env bash
# Pleos Connect Emulator 탐색: 패키지, VHAL 속성, Virtual Console(8088) 웹 자원, 화면.
set -u
O="$OUT"; mkdir -p "$O/vc"
a() { timeout 60 adb shell "$@" 2>&1; }
a getprop > "$O/getprop.txt"
a wm size > "$O/wm.txt"; a wm density >> "$O/wm.txt"
a pm list packages -f > "$O/packages.txt"
timeout 30 adb root; sleep 5; timeout 60 adb wait-for-device
a id > "$O/root.txt"
sleep 30
timeout 30 adb exec-out screencap -p > "$O/s01_home.png"
a dumpsys activity activities | head -80 > "$O/activities.txt"
a "netstat -tlnp 2>/dev/null || ss -tlnp" > "$O/listen.txt"
a cmd car_service -h > "$O/car_service_help.txt"
a cmd car_service get-carpropertyconfig > "$O/carprops.txt"
a dumpsys android.hardware.automotive.vehicle.IVehicle/default --list > "$O/vhal_list.txt"
a dumpsys android.hardware.automotive.vehicle.IVehicle/default --help > "$O/vhal_help.txt"
a service list > "$O/services.txt"
adb forward tcp:8088 tcp:8088
sleep 2
curl -s -m 20 -D "$O/vc/headers.txt" http://127.0.0.1:8088/ -o "$O/vc/index.html"
( cd "$O/vc" && wget -q -r -l 3 -p -np -nH -e robots=off http://127.0.0.1:8088/ ; ls -R . > ls.txt )
# 앱 설치·실행
APK=$(ls poc/app/build/outputs/apk/debug/*.apk | head -1)
timeout 120 adb install -r -t -g "$APK" > "$O/install.txt" 2>&1
a dumpsys package com.hmg.safelog.poc | grep -A40 "runtime permissions" > "$O/app_perms.txt"
adb logcat -c
a am start -W -n com.hmg.safelog.poc/com.hmg.safelog.app.MainActivity > "$O/am_main.txt"
sleep 15
timeout 30 adb exec-out screencap -p > "$O/s02_app.png"
timeout 30 adb logcat -d > "$O/logcat_app.txt"
# speed property set test (PERF_VEHICLE_SPEED 0x11600207)
a cmd car_service inject-vhal-event 0x11600207 0 5.5 > "$O/inject_speed.txt"
a dumpsys android.hardware.automotive.vehicle.IVehicle/default --set 0x11600207 -f 5.5 >> "$O/inject_speed.txt"
a dumpsys android.hardware.automotive.vehicle.IVehicle/default --get 0x11600207 >> "$O/inject_speed.txt"
a cmd car_service get-property-value 0x11600207 >> "$O/inject_speed.txt"
sleep 5
timeout 30 adb exec-out screencap -p > "$O/s03_after_speed.png"
timeout 30 adb logcat -d -s SafeLog > "$O/logcat_safelog.txt"
# screen recording speed check
START=$(date +%s.%N); for i in $(seq 1 20); do timeout 30 adb exec-out screencap -p > /tmp/f.png; done; END=$(date +%s.%N)
echo "20 screencaps: $(echo "$END - $START" | bc)s" > "$O/screencap_rate.txt"
timeout 30 adb emu screenrecord start --time-limit 15 "$O/emu_rec.webm" > "$O/emu_rec.txt" 2>&1; sleep 17
ls -la "$O" >> "$O/emu_rec.txt"
