# AutomotiveCarPropApp — Simple Vehicle Property Dashboard (AAOS)

Capstone Project 1 — a basic Android Automotive OS (AAOS) application that reads real-time vehicle signals through the Car API and displays them on an automotive-optimized dashboard.

Built in **Java** with classic Android Views. Runs on the **Android Automotive emulator** (API 33, "Automotive 1408p landscape" profile, Google APIs image).

## What it does

The app connects to the vehicle service, reads static vehicle information once, subscribes to dynamic properties, and updates the UI in real time as signals change in the emulator.

| Card | Property (`VehiclePropertyIds`) | Behavior |
|---|---|---|
| Speed (km/h) | `PERF_VEHICLE_SPEED` | Live, UI rate; HAL reports m/s, converted to km/h |
| Gear | `GEAR_SELECTION` | Live, on change; decoded to P / R / N / D |
| Fuel | `FUEL_LEVEL` + `INFO_FUEL_CAPACITY` | Live; shown as % of tank capacity; **turns red with a warning below 20%** |
| Engine RPM (est.) | `ENGINE_RPM` | Privileged — refused on the emulator; falls back to an estimate derived from speed + gear (6-speed automatic model), explicitly labeled "(est.)" |
| Doors | `DOOR_POS` | Privileged — refused; degrades gracefully to "N/A" |
| Manufacturer / Car Model / Model Year | `INFO_MAKE`, `INFO_MODEL`, `INFO_MODEL_YEAR` | Read once at startup |
| Speed history | — | Custom Canvas view; last 60 s of speed, sampled at 1 Hz |

Additional features:

- **Day/Night theme** — DayNight theme + `values-night` resources; switches live when the system UI mode changes (toggle `NIGHT_MODE` in the emulator's VHAL panel).
- **Runtime permissions** — `CAR_SPEED`, `CAR_POWERTRAIN`, `CAR_ENERGY` are requested at launch; the app connects after the grant.
- **Driving UX restrictions** — the activity is declared `distractionOptimized`, so the dashboard stays usable while the vehicle is in Drive.

## Architecture

```
MainActivity (Java, Views)
   └── Car.createCar()                        connects to the vehicle service
         └── CarPropertyManager
               ├── getProperty()              one-shot reads (static info, initial values)
               └── registerCallback()         real-time subscriptions (speed / fuel / gear)
                     └── Vehicle HAL          simulated by the emulator (VHAL properties panel)
```

Key files:

- `app/src/main/java/.../MainActivity.java` — connection, permissions, reads, subscriptions, UI updates
- `app/src/main/java/.../SpeedGraphView.java` — custom View drawing the speed history with Canvas
- `app/src/main/res/layout/activity_main.xml` — dashboard layout (background photo + 2 rows of cards + graph row)
- `app/src/main/res/values/colors.xml` and `values-night/colors.xml` — day/night palettes
- `app/src/main/AndroidManifest.xml` — automotive feature, `android.car` library, permissions, `distractionOptimized`

## How to run

1. **Android Studio** → SDK Manager → install an **Android Automotive** system image (tested with API 33, Google APIs, x86_64).
2. Device Manager → Create Device → category **Automotive** → "Automotive (1408p landscape)" → select the Automotive image → Finish.
3. Open the project, let Gradle sync (`useLibrary("android.car")` is set in `app/build.gradle.kts`).
4. Run ▶ on the Automotive AVD.
5. On first launch, **grant the requested car permissions**.

## How to test real-time updates

In the emulator window: **⋮ Extended Controls → Car data → VHAL properties** tab, then:

| Property | Panel input | Effect in app |
|---|---|---|
| `Speed of the vehicle` (ID 291504647) | value in **m/s** (e.g. `20` → 72 km/h) | Speed card + graph + estimated RPM |
| `FUEL_LEVEL` (ID 291504903) | value in **liters** on this panel (HAL stores milliliters) — e.g. `18` on a 120 L tank → 15% → red alert | Fuel card |
| `GEAR_SELECTION` | `4` = Park, `8` = Drive, `2` = Reverse, `1` = Neutral | Gear card |
| `NIGHT_MODE` | toggle true/false | Live day/night theme switch |

Note: events fire only when the value actually **changes**.

## Known limitations (by design)

- `ENGINE_RPM` and `DOOR_POS` require **privileged permissions** (`CAR_ENGINE_DETAILED`, `CONTROL_CAR_DOORS`) reserved for OEM/system apps. Granting via `adb shell pm grant` fails with *"not a changeable permission type"*. The app requests the real properties first and only falls back (labeled estimate for RPM, "N/A" for doors) after a `SecurityException` — this mirrors how a well-behaved third-party app must behave on production AAOS.
- The RPM estimation is a simple plausibility model (idle 800, per-gear ratio bands, capped at 6500) — clearly labeled "(est.)" in the UI.

## Deliverables checklist

- [x] Working AAOS application on the emulator
- [x] Real-time data updates (callbacks)
- [x] Automotive UI/UX (dashboard cards, distraction-optimized, day/night)
- [x] Permission handling (runtime + graceful degradation for privileged)
- [x] Optional: low-fuel alert
- [x] Optional: theme customization (day/night)
- [x] Optional: historical trends (speed graph)
- [x] Presentation slides + screenshots
