#!/usr/bin/env bash
# Pleos Connect Emulator 2.1.0 AVD 만들기. avdmanager가 실패하면 config.ini를 직접 쓴다. 출력은 $OUT/setup.log
set -x
exec >> "$OUT/setup.log" 2>&1
SDK=$ANDROID_HOME
D=$SDK/system-images/android-34/pleos_car_emulator/x86_64
ls -la "$D/" ; cat "$D/source.properties"; ls "$D/data" 2>/dev/null | head
find -L "$D" -maxdepth 1 -name "*.ini" -o -maxdepth 1 -name "*.xml" | while read f; do echo "== $f"; head -60 "$f"; done
yes | $SDK/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1
$SDK/cmdline-tools/latest/bin/sdkmanager --install emulator platform-tools > /dev/null 2>&1
$SDK/emulator/emulator -version | head -1
echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm
ls -la /dev/kvm
export ANDROID_AVD_HOME=$HOME/.android/avd; mkdir -p $ANDROID_AVD_HOME
echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd -n pleos -k "system-images;android-34;pleos_car_emulator;x86_64" --force
if [ ! -f $ANDROID_AVD_HOME/pleos.avd/config.ini ]; then
  echo "avdmanager failed -> manual AVD"
  mkdir -p $ANDROID_AVD_HOME/pleos.avd
  printf 'avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/pleos.avd\ntarget=android-34\n' "$ANDROID_AVD_HOME/pleos.avd" > $ANDROID_AVD_HOME/pleos.ini
  cat > $ANDROID_AVD_HOME/pleos.avd/config.ini <<CFG
AvdId=pleos
avd.ini.displayname=Pleos Connect v2.1.0
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
image.sysdir.1=system-images/android-34/pleos_car_emulator/x86_64/
tag.id=android-automotive
tag.display=Pleos Connect v2.1.0 APIs
PlayStore.enabled=false
hw.keyboard=yes
hw.mainKeys=no
disk.dataPartition.size=8G
CFG
fi
C=$ANDROID_AVD_HOME/pleos.avd/config.ini
sed -i '/^hw.ramSize/d;/^fastboot.forceColdBoot/d;/^hw.gpu/d;/^hw.lcd/d;/^vm.heapSize/d' $C
printf 'hw.ramSize=4096\nvm.heapSize=512\nfastboot.forceColdBoot=yes\nhw.gpu.enabled=yes\nhw.gpu.mode=swiftshader_indirect\nhw.lcd.width=%s\nhw.lcd.height=%s\nhw.lcd.density=%s\n' "${LCD_W:-1920}" "${LCD_H:-1080}" "${LCD_DPI:-120}" >> $C
cat $C
cp $C "$OUT/avd_config.ini"
