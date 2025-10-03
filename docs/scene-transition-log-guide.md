# Scene Transition Log Guide

This guide summarizes the steps used to run the LibGDX hamster game on both the desktop and Android targets in a headless CI container and capture the scene transition logs mentioned in recent investigations.

## Desktop (LWJGL3) run with `xvfb`

1. Install Xvfb once in the container:
   ```bash
   apt-get update
   apt-get install -y xvfb
   ```
2. Launch the desktop target under a virtual display and stop it after a short window (the hamster auto-wins and resets on its own):
   ```bash
   timeout 40 xvfb-run -a ./gradlew :lwjgl3:run --console=plain --no-daemon | tee desktop.log
   ```
3. Review `desktop.log` for lines such as:
   ```
   [HamstersGame] Entering Scene 1 (Gameplay) (reason: initial startup) on Desktop
   [HamstersGame] Transition Scene 1 (Gameplay) -> Scene 2 (Game Over) (reason: hamster victory via auto-win) on Desktop
   ```

## Android emulator run with `xvfb`

1. Download and unpack the Android command-line tools:
   ```bash
   mkdir -p $HOME/android-sdk/cmdline-tools
   wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
   unzip -q cmdline-tools.zip -d cmdline-tools
   mv cmdline-tools/cmdline-tools cmdline-tools/latest
   rm cmdline-tools.zip
   export ANDROID_HOME=$HOME/android-sdk
   export ANDROID_SDK_ROOT=$ANDROID_HOME
   export PATH=$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
   ```
2. Install the required SDK components (platform tools, API 28 platform & system image, and the emulator binary):
   ```bash
   yes | sdkmanager "platform-tools" "platforms;android-28" "emulator" "system-images;android-28;default;x86_64"
   ```
3. Create an Android Virtual Device:
   ```bash
   yes | avdmanager create avd -n hamster_api28 -k "system-images;android-28;default;x86_64" --device "pixel"
   ```
4. Start the emulator inside a virtual display (software rendering is required in this environment):
   ```bash
   xvfb-run -a emulator -avd hamster_api28 -no-snapshot -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off > /tmp/emulator.log 2>&1 &
   adb wait-for-device
   ```
5. Once the device is ready, install and launch the game:
   ```bash
   ./gradlew :android:installDebug --console=plain --no-daemon
   adb logcat -c
   adb shell am start -n tatar.eljah.hamsters/tatar.eljah.hamsters.android.AndroidLauncher
   ```
6. Capture the logs that confirm Scene 2 is presented after the auto-victory:
   ```bash
   adb logcat -d HamstersGame:I *:S > android.log
   ```
   The resulting `android.log` contains entries such as:
   ```
   HamstersGame: Entering Scene 1 (Gameplay) (reason: initial startup) on Android
   HamstersGame: Transition Scene 1 (Gameplay) -> Scene 2 (Game Over) (reason: hamster victory via auto-win) on Android
   ```
7. Shut down the emulator when finished to free resources:
   ```bash
   adb -s emulator-5554 emu kill
   ```

These steps reproduce the behavior expected by the logging tests and provide an auditable record of the transitions on both supported platforms while running entirely in headless CI infrastructure.
