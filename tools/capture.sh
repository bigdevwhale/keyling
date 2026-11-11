#!/bin/bash
# Screenshots of the demo on a phone, in English and Russian: docs/screenshots/<lang>/*.jpg and, when the
# site sits next to this repo, ../stillcraft/assets/kl-*.png (half size).
#   ANDROID_SERIAL=<phone> bash tools/capture.sh
# Each screen opens straight from the launch intent (demo builds read tab/sub/res/shop), so there are no taps;
# before every shot it checks the demo is in front and no call is going on, and stops otherwise.
set -e
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
: "${JAVA_HOME:=/c/Program Files/Android/Android Studio/jbr}"; export JAVA_HOME
PKG=app.keyling.shots
RAW=app/build/shots

guard() {
  f=$(adb shell dumpsys window | grep mCurrentFocus)
  c=$(adb shell dumpsys telephony.registry | grep -m1 mCallState | tr -d ' \r')
  case "$f" in *$PKG*) ;; *) echo "ABORT focus $f"; exit 1;; esac
  [ "$c" = "mCallState=0" ] || { echo "ABORT call $c"; exit 1; }
}
open() { adb shell am force-stop $PKG; adb shell am start -n $PKG/app.keyling.MainActivity "$@" >/dev/null; sleep "${WAIT:-6}"; }
shot() { guard; adb exec-out screencap -p > "$RAW/$LANG_/$1.png"; }

# Light theme and a tidy status bar while shooting; the phone gets its own back afterwards.
NIGHT=$(adb shell cmd uimode night | tr -d '' | awk '{print $3}')
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
restore() { adb shell cmd uimode night "$NIGHT" >/dev/null; demo exit; }
trap restore EXIT
adb shell cmd uimode night no >/dev/null
adb shell settings put global sysui_demo_allowed 1
demo enter; demo clock -e hhmm 0900; demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide; demo notifications -e visible false

for LANG_ in ${LANGS:-en ru}; do
  mkdir -p "$RAW/$LANG_"
  ./gradlew :app:assembleDebug -Pdemo -Pshots -Pdemolang=$LANG_ -q
  adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
  adb shell pm clear $PKG >/dev/null
  WAIT=25 open                                   # the first launch seeds a year of writing
  open --ei tab 0;                         shot 1-desk
  open --ei tab 1 --es res enter;          shot 2-residents
  open --ei tab 2 --ei sub 0;              shot 3-year
  open --ei tab 2 --ei sub 3;              shot 4-awards
  open --ei tab 3 --ei shop 0;             shot 5-workshop
  open --ei tab 2 --ei sub 2;              shot 6-records
  adb shell am force-stop $PKG
done
python - <<'PY'
import glob, os
from PIL import Image
SITE = {"1-desk": "desk", "2-residents": "residents", "3-year": "year", "4-awards": "awards", "5-workshop": "workshop", "6-records": "records"}
for p in glob.glob("app/build/shots/*/*.png"):
    lang, name = p.replace("\\", "/").split("/")[-2], os.path.basename(p)[:-4]
    im = Image.open(p).convert("RGB")
    # The site and README show 1080 x 2340: a taller phone loses a little top and bottom.
    extra = im.height - round(im.width * 2340 / 1080)
    if extra > 0:
        im = im.crop((0, extra // 2, im.width, im.height - (extra - extra // 2)))
    os.makedirs(f"docs/screenshots/{lang}", exist_ok=True)
    im.save(f"docs/screenshots/{lang}/{name}.jpg", quality=88)
    if os.path.isdir("../stillcraft/assets"):
        im.resize((im.width // 2, im.height // 2), Image.LANCZOS).save(
            f"../stillcraft/assets/kl-{SITE[name]}{'' if lang == 'en' else '.ru'}.png", optimize=True)
PY
echo done
