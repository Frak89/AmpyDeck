#!/usr/bin/env bash
# Emulator smoke test: installs AmpyDeck, makes it the active keyboard, taps real keys
# (letters, comma, unknown word, suggestion slots, emoji, flow) and fails on any crash.
PKG=it.ampy.ampydeck
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p ci-out
adb install -r "$APK"
for t in $(seq 1 20); do
  adb shell ime list -a -s | grep -q "$PKG" && break; sleep 2
done
for t in $(seq 1 10); do
  adb shell ime enable $PKG/.AmpyDeckIME; adb shell ime set $PKG/.AmpyDeckIME
  adb shell settings get secure default_input_method | grep -q "$PKG" && break; sleep 2
done
echo "default IME: $(adb shell settings get secure default_input_method)"
# dismiss "X isn't responding" system dialogs (slow CI emulators)
anr(){ adb shell uiautomator dump /sdcard/a.xml >/dev/null 2>&1
  w=$(adb shell cat /sdcard/a.xml | grep -o 'text="Wait"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
  if [ -n "$w" ]; then set -- $w; adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); echo "dismissed ANR dialog"; sleep 1; fi; }
sleep 20; anr
adb logcat -c
adb shell am start -n $PKG/.SettingsActivity --ez smoke true
sleep 6
# tap the test field (first EditText) to be sure the keyboard opens
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
b=$(adb shell cat /sdcard/ui.xml | grep -o 'class="android.widget.EditText"[^>]*bounds="[^"]*"' | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]*' | tr '\n' ' ')
set -- $b
for t in 1 2 3 4; do
  anr
  [ $# -ge 4 ] && adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
  sleep 5
  adb logcat -d -s AmpyDeckKeys:I | grep -q "K SPACE" && break
  echo "keyboard not up yet, retry $t"
done

keys(){ adb logcat -d -s AmpyDeckKeys:I | sed -n 's/.*AmpyDeckKeys: //p' > ci-out/keys.txt; }
pos(){ grep -F "K $1 " ci-out/keys.txt | tail -1 | awk '{print $3" "$4}'; }
tap(){ local p; p=$(pos "$1"); if [ -z "$p" ]; then echo "::warning::key '$1' not found"; return; fi
       adb shell input tap $p; sleep 0.35; }
type(){ for ch in $(echo "$1" | sed 's/./& /g'); do tap "$ch"; done; }
shot(){ adb exec-out screencap -p > "ci-out/$1.png"; }
alive(){ adb shell pidof $PKG:ime >/dev/null && adb shell dumpsys input_method | grep -q "mInputShown=true"; }

sleep 4; keys; sleep 1; keys
grep -q "K SPACE" ci-out/keys.txt || { echo "::error::keyboard never opened"; adb exec-out screencap -p > ci-out/00-noopen.png; exit 1; }
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
tap SYM; sleep 0.6; keys; tap "@"; tap "ABC"; sleep 0.6; keys
tap EMOJI; sleep 0.8; keys; tap PANEL0; tap PANELABC; sleep 0.6; keys
tap TOOLBAR; sleep 0.4; shot 06-toolbar; tap TOOLBAR
type ok; tap "."; tap ","; shot 07-final
# trackpad: hold ?123, drag around, select mode, back
p=$(pos SYM); adb shell input swipe $p $p 800; sleep 0.8; keys; shot 08-trackpad
p=$(pos PANEL0); set -- $p; adb shell input swipe $1 $(( $2+60 )) $(( $1+300 )) $(( $2+60 )) 500; sleep 0.4
adb shell input swipe $(( $1+300 )) $(( $2+60 )) $(( $1+300 )) $(( $2-40 )) 400; sleep 0.4
tap PANELABC; sleep 0.6; keys

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
# swipe up from the space bar must hide the keyboard
p=$(pos SPACE); set -- $p; adb shell input swipe $1 $2 $1 $(( $2-450 )) 250; sleep 1.2; shot 09-after-swipe-up
adb logcat -d -s AmpyDeckKeys:I | grep -aE "swipe|SWIPE|requestHide" | while IFS= read -r l; do echo "::warning::$l"; done
if adb shell dumpsys input_method | grep -q "mInputShown=true"; then echo "::error::swipe up did not hide the keyboard"; fail=1; fi
exit $fail
