#!/usr/bin/env bash
# Emulator smoke test: installs AmpyDeck, makes it the active keyboard, taps real keys
# (letters, comma, unknown word, suggestion slots, emoji, flow) and fails on any crash.
PKG=it.ampy.ampydeck
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p ci-out
adb shell settings put global hide_error_dialogs 1
adb shell settings put global anr_show_background 0
adb install -r "$APK"
for t in $(seq 1 20); do
  adb shell ime list -a -s | grep -q "$PKG" && break; sleep 2
done
for t in $(seq 1 10); do
  adb shell ime enable $PKG/.AmpyDeckIME; adb shell ime set $PKG/.AmpyDeckIME
  adb shell settings get secure default_input_method | grep -q "$PKG" && break; sleep 2
done
echo "default IME: $(adb shell settings get secure default_input_method)"
adb shell settings put secure spell_checker_enabled 1
adb shell settings put secure enabled_accessibility_services $PKG/.AmpyMouseService
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure selected_spell_checker $PKG/.AmpySpellChecker
adb shell settings put secure selected_spell_checker_subtype 0
# dismiss "X isn't responding" system dialogs (slow CI emulators)
anr(){ adb shell uiautomator dump /sdcard/a.xml >/dev/null 2>&1
  w=$(adb shell cat /sdcard/a.xml | grep -o 'text="Wait"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  if [ -n "$w" ]; then set -- $w; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); echo "dismissed ANR dialog"; sleep 1; fi
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1; }
sleep 20; anr
echo '{"entry_fx":"tiles","entry_ms":1600}' > /tmp/p.json; adb push /tmp/p.json /data/local/tmp/p.json >/dev/null; adb shell chmod 644 /data/local/tmp/p.json
adb shell run-as $PKG mkdir -p files; adb shell run-as $PKG cp /data/local/tmp/p.json files/ampydeck_prefs.json; echo "prefs: $(adb shell run-as $PKG cat files/ampydeck_prefs.json)"
adb shell am start -n $PKG/.SettingsActivity --ez smoke true
sleep 3; adb shell settings put secure selected_spell_checker $PKG/.AmpySpellChecker; echo "spell=$(adb shell settings get secure selected_spell_checker)"
adb shell dumpsys textservices | head -40 > ci-out/textservices.txt
sleep 6
# tap the test field (first EditText) to be sure the keyboard opens
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
b=$(adb shell cat /sdcard/ui.xml | grep -o 'class="android.widget.EditText"[^>]*bounds="[^"]*"' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
set -- $b
for t in 1 2 3 4; do
  anr
  [ $# -ge 4 ] && adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
  for f in 1 2 3 4; do sleep 0.4; adb exec-out screencap -p > ci-out/00-entry-$t-$f.png; done
  sleep 3
  adb logcat -d -s AmpyDeckKeys:I | grep -q "K SPACE" && break
  echo "keyboard not up yet, retry $t"
done

keys(){ adb logcat -d -s AmpyDeckKeys:I | sed -n 's/.*AmpyDeckKeys: //p' > ci-out/keys.txt; }
pos(){ grep -F "K $1 " ci-out/keys.txt | tail -1 | awk '{print $3" "$4}'; }
tap(){ local p; p=$(pos "$1"); if [ -z "$p" ]; then echo "::warning::key '$1' not found"; return; fi
       adb shell input tap $p; sleep 0.35; }
type(){ for ch in $(echo "$1" | sed 's/./& /g'); do tap "$ch"; done; }
shot(){ adb exec-out screencap -p > "ci-out/$1.png"; }
alive(){ adb shell pidof $PKG:ime >/dev/null || return 1
  adb shell dumpsys input_method | grep -q "mIsInputViewShown=true" && return 0
  adb shell dumpsys input_method | grep -iE "shown|visible" | head -6 | while IFS= read -r l; do echo "::warning::IMS $l"; done; return 1; }

sleep 4; keys; sleep 1; keys
grep -q "K SPACE" ci-out/keys.txt || { echo "::error::keyboard never opened"; adb exec-out screencap -p > ci-out/00-noopen.png
  adb logcat -d > ci-out/logcat.txt; adb shell dumpsys input_method > ci-out/ims.txt
  pid=$(adb shell pidof $PKG:ime); echo "::warning::ime pid=$pid"
  [ -n "$pid" ] && adb shell kill -3 $pid && sleep 2 && adb logcat -d > ci-out/logcat-anr.txt
  grep -a " $pid " ci-out/logcat.txt | tail -30 | while IFS= read -r l; do echo "::warning::IME $l"; done
  grep -aE "AmpyDeck|FATAL|AndroidRuntime|ampydeck" ci-out/logcat.txt | grep -v AmpyDeckKeys | tail -25 | while IFS= read -r l; do echo "::warning::$l"; done
  exit 1; }
grep -a FIT ci-out/keys.txt | while IFS= read -r l; do echo "::warning::$l"; done
grep GEOM ci-out/keys.txt | tail -2 | while IFS= read -r l; do echo "::warning::$l"; done
adb shell dumpsys window windows | grep -A3 "InputMethod" | head -20 | while IFS= read -r l; do echo "::warning::WIN $l"; done
shot 01-open
type ciao; tap ","; shot 02-comma
tap SPACE; type kzqwx; shot 03-unknown
tap B1; sleep 0.6; shot 04-pick-unknown
alive || echo "::error::keyboard gone after tapping the unknown word"
type grazei; tap SPACE; tap DEL; tap SPACE
type com; tap B1; tap B1
# long-press comma / period, then flow swipe c-i-a-o
p=$(pos ","); adb shell input swipe $p $p 700; sleep 0.4
p=$(pos "."); adb shell input swipe $p $p 700; sleep 0.4
c=$(pos c); i=$(pos i); a=$(pos a); o=$(pos o)
adb shell input motionevent DOWN $c; for q in "$i" "$a" "$o"; do adb shell input motionevent MOVE $q; done; adb shell input motionevent UP $o
sleep 1; shot 05-flow
# hold space = trackpad while the finger is down; drag around (also near the screen edge), release
p=$(pos SPACE); set -- $p; x=$1; y=$2
adb shell input motionevent DOWN $x $y; sleep 1.0
for d in 80 200 320 -200 -420; do adb shell input motionevent MOVE $(( x+d )) $(( y-60 )); done
shot 05b-spacepad
adb shell input motionevent MOVE 8 $(( y-120 )); adb shell input motionevent UP 8 $(( y-120 )); sleep 0.6
alive || echo "::error::keyboard closed during space-trackpad gesture"
# tap inside an already written word: suggestions for it, keyboard must stay
set -- $b; [ $# -ge 4 ] && adb shell input tap $(( $1+90 )) $(( ($2+$4)/2 )); sleep 1.2; shot 05c-word-tap
alive || echo "::error::keyboard closed after tapping a written word"
keys; tap B1; sleep 0.5
tap SYM; sleep 0.6; keys; tap "@"; tap "ABC"; sleep 0.6; keys
tap EMOJI; sleep 0.8; keys; tap PANEL0; p=$(pos PANELABC); set -- $p; adb shell input tap 50 $2; sleep 0.6; keys
tap TOOLBAR; sleep 0.4; shot 06-toolbar; tap TOOLBAR
type ok; tap "."; tap ","; shot 07-final
# trackpad: hold ?123, drag around, select mode, back
p=$(pos SYM); adb shell input swipe $p $p 800; sleep 0.8; keys; shot 08-trackpad
p=$(pos PANEL0); set -- $p; adb shell input swipe $1 $(( $2+60 )) $(( $1+300 )) $(( $2+60 )) 500; sleep 0.4
# drag starting at the very left edge (Android back gesture zone) must not close the keyboard
adb shell input swipe 3 $(( $2+120 )) 500 $(( $2+120 )) 400; sleep 0.6
alive || echo "::error::keyboard closed by an edge drag on the trackpad"
adb shell input swipe $(( $1+300 )) $(( $2+60 )) $(( $1+300 )) $(( $2-40 )) 400; sleep 0.4
p=$(pos PANELABC); set -- $p; adb shell input tap 50 $2; sleep 0.6; keys

echo "--- text in field"; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml | grep -o 'class="android.widget.EditText"[^>]*' | grep -o 'text="[^"]*"' | head -2
crash=$(adb shell run-as $PKG cat files/last_crash.txt 2>/dev/null)
adb shell pidof $PKG:ime >/dev/null || echo "::warning::ime process not running"
adb logcat -d > ci-out/logcat.txt
fatal=$(grep -A25 "FATAL EXCEPTION" ci-out/logcat.txt | grep -B2 -A25 "$PKG" | head -40)
fail=0
if [ -n "$crash" ]; then echo "$crash" | head -30 | while IFS= read -r l; do echo "::error::CRASHLOG $l"; done; fail=1; fi
if [ -n "$fatal" ]; then echo "$fatal" | while IFS= read -r l; do echo "::error::FATAL $l"; done; fail=1; fi
grep -E "AmpyDeckCrash|ANR in $PKG" ci-out/logcat.txt | head -20 | while IFS= read -r l; do echo "::error::$l"; done
if ! alive; then echo "::error::keyboard not shown / process gone at end"; fail=1; fi
# ---- mouse: open from the toolbar (➶ = 4th tool), move the pointer, click, exit
p=$(pos TOOLBAR); set -- $p; bx=$1; by=$2; W=1080
adb shell input tap $bx $by; sleep 0.5
tw=$(( (W-2*bx)/9 )); adb shell input tap $(( 2*bx + tw*3 + tw/2 )) $by; sleep 1.2
p=$(pos PANEL0); set -- $p; mx=$(( $1+300 )); my=$(( $2+150 ))
adb shell "input motionevent DOWN $mx $my; input motionevent MOVE $(( mx-40 )) $(( my-30 )); input motionevent MOVE $(( mx-80 )) $(( my-60 )); input motionevent MOVE $(( mx-120 )) $(( my-90 )); input motionevent UP $(( mx-120 )) $(( my-90 ))"
sleep 0.6; shot 10-mouse
adb shell dumpsys window | grep -q "AmpyDeck pointer" && echo "::notice::pointer overlay on screen" || echo "::error::pointer overlay missing"
p=$(pos PANELABC); set -- $p; adb shell input tap $(( W*15/16 )) $2; sleep 0.6
alive || echo "::error::keyboard closed after mouse mode"
# ---- emoji inertial scroll: fast flick, compare frames
tap EMOJI; sleep 0.8; keys; p=$(pos PANEL0); set -- $p
adb shell input swipe 540 $(( $2+330 )) 540 $(( $2+60 )) 120; shot 11-fling-a; sleep 0.6; shot 11-fling-b
p=$(pos PANELABC); set -- $p; adb shell input tap 50 $2; sleep 0.5
# swipe up from the space bar must hide the keyboard
p=$(pos SPACE); set -- $p; x=$1; y=$2
adb shell "input motionevent DOWN $x $y; input motionevent MOVE $x $(( y-60 )); input motionevent MOVE $x $(( y-250 )); input motionevent MOVE $x $(( y-450 )); input motionevent UP $x $(( y-450 ))"; sleep 1.2; shot 09-after-swipe-up
adb logcat -d -s AmpyDeckSpell:* | tail -10 | while IFS= read -r l; do echo "::warning::$l"; done
adb logcat -d > ci-out/logcat2.txt
adb logcat -d -s AmpyDeckKeys:I | grep -aE "swipe|SWIPE|requestHide|TOUCH" | tail -8 | while IFS= read -r l; do echo "::warning::$l"; done
adb shell dumpsys window | grep -A30 "Window{.*InputMethod}" | grep -m1 -E "isVisible|mViewVisibility" | while IFS= read -r l; do echo "::notice::after swipe-up: $l"; done
exit $fail
