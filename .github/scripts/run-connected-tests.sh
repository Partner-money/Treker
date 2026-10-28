#!/usr/bin/env bash
# Runs the instrumentation suite on the emulator started by reactivecircus/android-emulator-runner.
# Kept in a file (not inline in the workflow) so quoting stays readable and the script can be run locally.
set -uo pipefail

LOG="gradle-android.log"

log() { echo "[connected-tests] $*"; }

# Package verifiers and leftover installs are the usual reasons for "Failed to install split APK(s)"
# on the lightweight aosp_atd images.
adb wait-for-device
adb shell settings put global verifier_verify_adb_installs 0 || true
adb shell settings put global package_verifier_enable 0 || true
adb uninstall de.treker.habits >/dev/null 2>&1 || true
adb uninstall de.treker.habits.test >/dev/null 2>&1 || true

# Free the Gradle daemon memory before the emulator has to share the runner RAM with it.
./gradlew --stop >/dev/null 2>&1 || true

status=1
for attempt in 1 2; do
  log "attempt ${attempt}: connectedDebugAndroidTest"
  ./gradlew --no-daemon --max-workers=1 \
    -Dorg.gradle.jvmargs="-Xmx1280m -XX:MaxMetaspaceSize=384m" \
    connectedDebugAndroidTest 2>&1 | tee -a "$LOG"
  status=${PIPESTATUS[0]}
  if [ "$status" -eq 0 ]; then
    break
  fi
  if grep -q "Failed to install split APK" "$LOG"; then
    log "install failed, cleaning device state before retry"
    adb uninstall de.treker.habits >/dev/null 2>&1 || true
    adb uninstall de.treker.habits.test >/dev/null 2>&1 || true
    adb shell pm trim-caches 4G >/dev/null 2>&1 || true
    sleep 15
    continue
  fi
  # A real test failure must not be retried: it would only hide the signal.
  break
done

adb logcat -d > logcat.txt 2>&1 || true
adb shell pm list packages > installed-packages.txt 2>&1 || true
exit "$status"
