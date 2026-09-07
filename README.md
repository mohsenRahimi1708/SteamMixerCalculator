# Steam Mixer Calculator v2

An Android application that calculates spray water (attemperator) mixing for superheater outlet temperature control, using the **validated Hummeling IF97 library** for all steam/water thermodynamic properties.

## What's New in v2

- **IF97 Library**: Replaced hand-implemented IAPWS-IF97 equations with the validated [Hummeling IF97 library](https://github.com/hummeling/if97) (`com.hummeling:if97:2.0.0`)
- **Transient Model**: Added platen metal thermal inertia simulation for burner-ignition scenarios
- **Jetpack Compose UI**: Migrated from XML layouts to Material 3 Compose
- **MVVM Architecture**: Clean separation between UI and calculation engine
- **Energy Balance Transparency**: Full assumption set displayed with every result

## Physics

### Steady-State Energy Balance
```
ṁ_steam · h_steam(P, T_steam) + ṁ_spray · h_water(P, T_spray) = (ṁ_steam + ṁ_spray) · h_out
```
Solved for required spray flow:
```
ṁ_w = ṁ_s · (h_s − h_out) / (h_out − h_w)
```

### Transient Thermal Inertia (Burner Ignition)
```
C_metal = M_metal · Cp_metal
dT_metal/dt = Q_net / (M_metal · Cp_metal)
Q_net = η · N_burners · Q_burner − ṁ_w · (h_out − h_spray)
```

## Thermodynamic Properties

All steam and water properties are computed using the **Hummeling IF97 library** — a validated, LGPL-licensed Java implementation of the IAPWS-IF97 industrial formulation.

- **Unit system**: ENGINEERING (bar, °C, kJ/kg)
- **Region 1**: Compressed liquid water (spray water enthalpy)
- **Region 2**: Superheated steam (steam enthalpy before and after mixing)
- **OutOfRangeException** handling: all IF97 domain errors are caught and surfaced

## Screens

1. **Quick Calculator** — Steady-state spray requirement with full assumptions
2. **Transient Simulation** — Burner ignition scenario with metal thermal inertia, temperature and spray flow vs. time charts
3. **Energy Balance** — Detailed breakdown showing the full calculation chain

## Worked Examples

### Example 1: High-Pressure Steam
- Steam: 1000 t/h, 167 bar, 540°C
- Target: 517°C, Spray water: 230°C
- Result: Spray flow ≈ 20 t/h, ΔT ≈ 23°C

### Example 2: Medium-Pressure Steam
- Steam: 300 t/h, 40 bar, 350°C
- Target: 336°C, Spray water: 200°C
- Result: Spray flow ≈ 5 t/h, ΔT ≈ 14°C

### Example 3: Invalid Input
- Steam: 500 t/h, 170 bar, 200°C
- Result: Rejected — steam at 200°C is compressed liquid (T_sat ≈ 352°C)

## Building

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (IF97 verification)
./gradlew test
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Project Structure

```
├── app/src/main/java/com/steamcalc/
│   ├── engine/                          # Pure Kotlin calculation engine
│   │   ├── SteamProperties.kt           # IF97 library wrapper
│   │   ├── SteadyStateSprayCalculator.kt
│   │   ├── PlatenThermalInertiaModel.kt
│   │   ├── TransientSimulationRunner.kt
│   │   ├── EnergyBalanceReport.kt
│   │   └── Models.kt
│   ├── ui/
│   │   ├── theme/                       # Material 3 theme
│   │   ├── screens/                     # Compose screens
│   │   ├── navigation/                  # Navigation graph
│   │   ├── QuickCalculatorViewModel.kt
│   │   └── TransientSimulationViewModel.kt
│   └── MainActivity.kt
├── app/src/test/java/com/steamcalc/engine/
│   ├── SteamPropertiesTest.kt           # IF97 verification points
│   ├── SteadyStateSprayCalculatorTest.kt
│   └── PlatenThermalInertiaModelTest.kt
└── .github/workflows/android-build.yml
```

## Technical Details

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Pattern**: MVVM
- **IF97 Library**: com.hummeling:if97:2.0.0 (LGPL)
- **Offline**: No network calls required

## License

This project uses the Hummeling IF97 library, which is licensed under the GNU Lesser General Public License (LGPL). The library's license notice is included in the application.

## Future Scope

- Instructor/admin panel for training scenarios
- Scoring system for calculated vs. expected results
- Full Region 3 (near-critical) handling
- Transport properties (viscosity, thermal conductivity)
