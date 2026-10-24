#!/usr/bin/env bash
# Records the Play Store demo clips on the emulator: placing, setting up and resizing the widgets, and Clock settings
# (including allowing minute updates). Clips and step pictures go to demo/; marks.txt times each step for captions.
set -uo pipefail
PKG=com.prostellis.awe
D=demo; mkdir -p $D/steps
exec > >(tee -a $D/demo.log) 2>&1
n=0
shot() { n=$((n + 1)); adb exec-out screencap -p > "$D/steps/$(printf %02d $n)-$1.png" 2>/dev/null || true; }
dump() { adb exec-out uiautomator dump /dev/tty 2>/dev/null; }
# where ARGS… → "x y" of the first matching view (waits up to 10 s for it)
where() { local p; for _ in 1 2 3 4 5; do p=$(dump | python3 .github/scripts/ui.py "$@") && { echo "$p"; return 0; }; sleep 2; done
  echo "::warning title=Demo::Not found: $*" >&2; dump > "$D/steps/missing-$n.xml"; return 1; }
tap() { local p; p=$(where "$@") || return 1; adb shell input tap $p; }
# Press, hold, glide to a point and let go (drags a widget or a resize handle)
drag() { local x1=$1 y1=$2 x2=$3 y2=$4 hold=${5:-1.2} i
  adb shell input motionevent DOWN $x1 $y1; sleep "$hold"
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do adb shell input motionevent MOVE $((x1 + (x2 - x1) * i / 12)) $((y1 + (y2 - y1) * i / 12)); done
  sleep 0.6; adb shell input motionevent UP $x2 $y2; }
hold() { adb shell input motionevent DOWN $1 $2; sleep 1.2; adb shell input motionevent UP $1 $2; }
swipe() { adb shell input swipe "$@"; }
T0=0
mark() { echo "$CLIP $(python3 -c "import time;print(round(time.time()-$T0,1))") $*" >> $D/marks.txt; }
rec() { CLIP=$1; adb shell rm -f /sdcard/$1.mp4; adb shell screenrecord --bit-rate 16000000 --time-limit 175 /sdcard/$1.mp4 & RP=$!; T0=$(python3 -c "import time;print(time.time())"); sleep 1; }
stop() { sleep 1.5; adb shell pkill -INT screenrecord || true; wait $RP 2>/dev/null; sleep 2; adb pull /sdcard/$CLIP.mp4 $D/ || true; }

read -r W H < <(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr x ' ')
echo "Screen $W x $H"
# The touchscreen, for drags made of real touch events (a widget only drops if the finger rests over the spot)
TDEV=$(adb shell getevent -pl | awk '/^add device/ {d=$4} /ABS_MT_POSITION_X/ {print d; exit}')
TMX=$(adb shell getevent -pl "$TDEV" | awk '/ABS_MT_POSITION_X/ {for (i=1;i<=NF;i++) if ($i=="max") {gsub(",","",$(i+1)); print $(i+1)}}')
TMY=$(adb shell getevent -pl "$TDEV" | awk '/ABS_MT_POSITION_Y/ {for (i=1;i<=NF;i++) if ($i=="max") {gsub(",","",$(i+1)); print $(i+1)}}')
echo "Touchscreen $TDEV max $TMX x $TMY"
adb push .github/scripts/touch.sh /data/local/tmp/touch.sh > /dev/null
# Writing touch events needs root (allowed on this emulator image)
adb root; sleep 3; adb wait-for-device
adb shell sendevent "$TDEV" 0 0 0 && echo "Touch events OK" || echo "::warning title=Demo::Can't send touch events"
# fdrag x1 y1 x2 y2 [hold] [rest]
fdrag() { adb shell sh /data/local/tmp/touch.sh "$TDEV" "$TMX" "$TMY" "$W" "$H" "$1" "$2" "$3" "$4" "${5:-1.2}" "${6:-1.5}"; }

adb install -r AWE-v*.apk
adb shell cmd alarm set-timezone America/Los_Angeles || true
adb shell cmd uimode night no
adb shell settings put system show_touches 1
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
# Warm-up: Play services settles, and the app downloads the forecast the widgets will show
adb shell am start -n "$PKG/.MainActivity" > /dev/null; sleep 50
adb shell input keyevent KEYCODE_HOME; sleep 4
# Clear the home page's three app icons (dragged to Remove) so the widgets have room
for app in Gmail Photos YouTube; do
  p=$(where --text "$app") && { read -r x y <<< "$p"; fdrag $x $((y - 70)) $((W / 2)) $((H * 7 / 100)); sleep 2; }
done
shot home

# Opens the widget list, finds AWE Weather and drags the widget named $1 to ($2, $3)
place() {
  hold $((W / 2)) $((H * 45 / 100)); sleep 1.5; shot menu
  tap --text Widgets || { adb shell input keyevent KEYCODE_HOME; return 1; }; sleep 2.5; shot picker
  tap --text "AWE Weather" || true; sleep 2; shot list
  # The widget's preview sits above its name: find the name, then the preview just above it
  local p="" i lb
  for i in 1 2 3; do
    if lb=$(dump | python3 .github/scripts/ui.py --text "$1" --bounds); then
      read -r lx1 ly1 lx2 ly2 <<< "$lb"
      p=$(dump | python3 .github/scripts/ui.py --longclick --near $(((lx1 + lx2) / 2)) $((ly1 - 150))) && break
    fi
    swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 50 / 100)) 600; sleep 1.5; shot scrolled
  done
  [ -n "$p" ] || { echo "::warning title=Demo::$1 not in the widget list" >&2; dump > "$D/steps/missing-$n.xml"; adb shell input keyevent KEYCODE_HOME; sleep 2; return 1; }
  read -r x y <<< "$p"
  # Android 15's widget list adds a widget with a tap and its Add button; otherwise drag it
  adb shell input tap $x $y; sleep 1.8; shot tapped
  if tap --text "Add" || tap --text "Add to home screen"; then sleep 4; shot placed; return 0; fi
  fdrag $x $y "$2" "$3" 1.2 3 & local fp=$!
  sleep 4.5; shot dragging; wait $fp; sleep 4; shot placed
}

# ---- Clip 1: the widget list and the Forecast widget's settings
rec widgets
mark "Pick a widget"
if place "AWE Forecast" $((W / 2)) $((H * 33 / 100)); then
mark "Make it yours"
sleep 2
tap --id style_fine; sleep 1.2; tap --id style_basic; sleep 1.2; tap --id style_3d; sleep 1.2
p=$(where --id glow) && { read -r x y <<< "$p"; swipe $((x - 200)) $y $((x + 250)) $y 900; sleep 1; }
tap --id row_temp_color && { sleep 1.5; mark "Any color"; tap --text Metallic; sleep 1.2; tap --desc Gold || true; sleep 1.5; shot picker-gold; tap --text OK; sleep 1.5; }
swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 30 / 100)) 900; sleep 1.5
swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 30 / 100)) 900; sleep 1.5
shot wsettings-bottom
tap --id done; sleep 3; shot forecast-home
fi
stop

# ---- Clip 2: more widgets, and stretching the Daily widget wider
rec resize
mark "From 1×1 to 5×2"
place "AWE 1×1" $((W * 80 / 100)) $((H * 53 / 100)) && { tap --id done; sleep 3; }
place "AWE Daily" $((W * 30 / 100)) $((H * 53 / 100)) && { tap --id done; sleep 3; }
mark "Stretch for more days"
# The Daily widget is the last one placed; long-press it for the resize frame, then pull its right edge across
if b=$(where --cls AppWidgetHostView --near $((W * 30 / 100)) $((H * 53 / 100)) --bounds); then
  read -r x1 y1 x2 y2 <<< "$b"; hold $(((x1 + x2) / 2)) $(((y1 + y2) / 2)); sleep 1.5; shot resize-frame
  swipe $((x2 - 6)) $(((y1 + y2) / 2)) $((W - 40)) $(((y1 + y2) / 2)) 1500; sleep 2; shot resized
  adb shell input keyevent KEYCODE_BACK; sleep 2
fi
shot widgets-home
stop

# ---- Clip 3: AWE Clock and its settings, including minute updates
rec clock
mark "AWE Clock"
if place "AWE Clock" $((W / 2)) $((H * 66 / 100)); then
sleep 1.5
mark "Right on the minute"
tap --id minute_allow; sleep 3; shot alarms-page
tap --text "*Allow setting alarms*" || tap --id "android:id/switch_widget" || tap --id switch_widget; sleep 2; shot alarms-on
adb shell input keyevent KEYCODE_BACK; sleep 3; shot back-in-settings
mark "10 clock styles"
for f in Heavy Tall Rounded Clean; do tap --desc "$f"; sleep 1.3; done
mark "Any color"
tap --id row_time_color && { sleep 1.5; tap --text Gradient; sleep 1.2; tap --desc Sunset || true; sleep 1.5; tap --text OK; sleep 1.5; }
swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 40 / 100)) 900; sleep 1.2
mark "Date and weather line"
tap --id weather; sleep 1.2; tap --id weather; sleep 1.2
tap --id date; sleep 1.2
swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 40 / 100)) 900; sleep 1.2
tap --id wxline; sleep 1.5
tap --id date_below; sleep 1.2
shot clock-settings
swipe $((W / 2)) $((H * 75 / 100)) $((W / 2)) $((H * 30 / 100)) 900; sleep 1.2
tap --id done; sleep 4; shot clock-home
mark "On your home screen"
sleep 3
fi
stop

adb shell settings put system show_touches 0
ls -la $D
