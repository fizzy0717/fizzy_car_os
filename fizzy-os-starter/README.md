# FIZZY OS

A lightweight, customizable Android head-unit launcher inspired by modern automotive interfaces.

> **Project status:** Early scaffold (v0.1.0). This is not a production-ready launcher yet. Maps, media integration, live connectivity labels, weather, prayer times, sidebar editing, debloating and recovery are planned phases.

## Design goals

- Native Android Java launcher for Android 10 (API 29) and later.
- Landscape-first layout with a large map area and right-side widgets.
- Translucent glass-inspired UI, tuned conservatively for lower-powered head units.
- Customizable left sidebar and All Apps drawer.
- Preserve the factory launcher and provide a safe route back to it.
- Never remove system packages automatically.

## Requirements

- Android Studio with Android SDK Platform 35.
- JDK 17.
- Android head unit running Android 10 or later for device testing.

Compatibility is not guaranteed across all head units. Hardware keys, CAN bus, radio, steering-wheel controls, Bluetooth audio and reverse-camera integrations vary by manufacturer.

## Build

1. Open this repository in Android Studio.
2. Allow Gradle sync to complete.
3. Select the `app` run configuration.
4. Build with **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Test on an emulator or spare head unit before setting it as the default Home app.

## Install safely

1. Keep the factory launcher installed.
2. Install the APK.
3. Launch FIZZY OS normally first.
4. Review requested permissions; grant only those needed for enabled features.
5. If Android asks which Home app to use, choose FIZZY OS only after confirming the factory launcher remains available.
6. Test Maps, audio, Bluetooth, radio, steering-wheel buttons and reverse camera while parked.

## Return to the factory launcher

Use Android **Settings > Apps > Default apps > Home app** (wording varies by device) and select the factory launcher. If FIZZY OS cannot open, use the head unit's system Settings or the manufacturer's recovery procedure.

## Uninstall

1. Switch the default Home app back to the factory launcher.
2. Open Android Settings > Apps > FIZZY OS > Uninstall.
3. Confirm removal.
4. Verify the factory launcher starts correctly.

FIZZY OS must not delete the factory launcher, firmware, CAN bus services, radio services, Bluetooth services, audio services or camera services.

## Safe debloating policy

No bulk debloating is included in this starter. Package removal/disable actions must be device-specific, explained, reversible where possible, and confirmed by the user. Do not disable unknown vendor packages.

## Development roadmap

- [x] Initial native Android project scaffold
- [ ] Liquid-glass home screen refinement
- [ ] Dynamic time/date and connection status
- [ ] Sidebar customization and All Apps drawer
- [ ] Maps integration
- [ ] Media controls
- [ ] Weather and prayer-time modules
- [ ] Settings and recovery UX
- [ ] Device-specific performance testing
- [ ] Safe debloating profiles
- [ ] Installation screenshots and troubleshooting guide

## License

License is not yet selected. Do not redistribute code from third-party projects until their license and attribution requirements have been reviewed.
