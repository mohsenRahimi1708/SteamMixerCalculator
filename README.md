# Steam Mixer Calculator

An Android application that calculates the outlet steam temperature after spray water (attemperator) injection at the inlet of a superheater. This is a mixing-point energy balance calculation using **IAPWS-IF97** thermodynamic properties.

## Physics

Steady-state energy and mass balance at the spray mixing point (no pressure drop):

```
ṁ_steam · h_steam(P, T_steam) + ṁ_spray · h_water(P, T_spray) = (ṁ_steam + ṁ_spray) · h_out
```

The outlet temperature T_out is found by solving `h_steam(P, T_out) = h_out` using bisection over T ∈ [T_sat(P), 600°C].

## Thermodynamic Properties

All steam and water properties are computed from **IAPWS-IF97** (Industrial Formulation 1997):

- **Region 1** (compressed/subcooled liquid water) — spray water enthalpy
- **Region 2** (superheated steam) — steam enthalpy before and after mixing

The implementation includes the full 34-term Region 1 polynomial and 43-term Region 2 residual equation with 9-term ideal gas contribution, exactly as published in the IAPWS-IF97 standard.

## Worked Examples

### Example 1: High-Pressure Steam
- **Steam:** 1000 t/h, 167 bar, 540°C
- **Spray:** 20 t/h, 167 bar, 230°C
- **Result:** T_out ≈ 517°C, ΔT ≈ 23°C
- **Combined flow:** 1020 t/h

### Example 2: Medium-Pressure Steam
- **Steam:** 300 t/h, 40 bar, 350°C
- **Spray:** 5 t/h, 40 bar, 200°C
- **Result:** T_out ≈ 336°C, ΔT ≈ 14°C
- **Combined flow:** 305 t/h

### Example 3: Invalid Input
- **Steam:** 500 t/h, 170 bar, 200°C
- **Result:** Rejected — "Invalid input: steam at 200°C, 170 bar is outside superheated steam region (T_sat ≈ 352°C)"
- At 170 bar, the saturation temperature is approximately 352°C. Steam at 200°C is compressed liquid, not superheated steam, so this input is correctly rejected.

## Building

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (IF97 verification)
./gradlew test
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## CI/CD

GitHub Actions workflow (`.github/workflows/android-build.yml`) runs on every push/PR:
1. Sets up JDK 17
2. Runs `./gradlew test` (includes IF97 verification unit tests)
3. Runs `./gradlew assembleDebug`
4. Uploads the APK as a build artifact

## Project Structure

```
├── app/src/main/java/com/steamcalc/
│   ├── if97/
│   │   ├── IF97Region1.kt      # Region 1 (liquid water) properties
│   │   ├── IF97Region2.kt      # Region 2 (superheated steam) properties
│   │   └── IF97Common.kt       # Saturation temperature/pressure
│   ├── calculator/
│   │   └── SteamMixerCalculator.kt  # Mixing calculation + root-finding
│   └── MainActivity.kt         # Android UI
├── app/src/test/java/com/steamcalc/
│   ├── if97/
│   │   ├── IF97Region1Test.kt
│   │   ├── IF97Region2Test.kt
│   │   └── IF97SaturationTest.kt
│   └── calculator/
│       └── SteamMixerCalculatorTest.kt
└── .github/workflows/android-build.yml
```

## Technical Details

- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34
- **Language:** Kotlin
- **UI:** XML Layout with standard Android SDK widgets
- **Offline:** No network calls required
- **Root-finding:** Bisection method with 0.001°C temperature tolerance

## License

This project implements the IAPWS-IF97 industrial standard for water and steam thermodynamic properties.
