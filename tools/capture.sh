#!/bin/bash
# Screenshots of the -Pdemo -Pshots build (app.keyling.shots) in one language, opened straight on each screen by intent extras.
set -e
export ANDROID_SERIAL=d7a925e6 JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
cd "$(dirname "$0")/.."
L=$1; P=app.keyling.shots; OUT=docs/screenshots/$L; mkdir -p $OUT
./gradlew assembleRelease -Pdemo -Pshots -Pdemolang=$L -q 2>&1 | grep -E "^e: |FAILED" && exit 1
adb install -r app/build/outputs/apk/release/app-release.apk >/dev/null
adb shell pm clear $P >/dev/null
adb shell am start -n $P/app.keyling.MainActivity >/dev/null; sleep 10
shot() { adb shell am start -S -n $P/app.keyling.MainActivity $2 >/dev/null; sleep ${3:-5}
  f=$(adb shell dumpsys window | grep -m1 mCurrentFocus); echo "$f" | grep -q "$P" || { echo "focus lost: $f"; exit 1; }
  adb exec-out screencap -p > $OUT/$1.png; }
shot 1-desk "--ei tab 0" 6
shot 2-residents "--ei tab 1 --es res enter"
shot 3-year "--ei tab 2 --ei sub 0"
shot 4-awards "--ei tab 2 --ei sub 3"
shot 5-workshop "--ei tab 3 --ei shop 0"
shot 6-records "--ei tab 2 --ei sub 2"
adb shell am force-stop $P
echo done $L
