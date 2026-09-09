# Platen Superheater Transient Simulation — Unit №4

Android app (Kotlin, Jetpack Compose) that simulates the **transient outlet temperature**
of a platen superheater in a 325 MW natural-circulation drum-type boiler (Unit №4).

## What it does

- **Dynamic, load-dependent thermal model** — not a static calculator.
- Property engine: **IAPWS-IF97 Regions 1/2/4** (vendored `com.hummeling.if97` v2.1.0, LGPL).
- **Uniform platen model** (refactored spec): the coil is one uniform tube — OD 57 /
  wall 8 / ID 41 mm, single material 12Cr2MoWVTiB — with a lumped metal node and steam node.
- **User-set initial metal temperature** (default 450 °C) enters the metal energy balance.
- RK4 solver with timestep-sensitivity verification.
- Full heat-transfer chain every timestep: properties → velocity → Re → Pr → Nu → h_i → U
  (cylindrical resistance chain: internal convection, wall conduction, fouling, external convection).
- Spray mixing with the IF97 Region 2 backward equation T(P,h); four distinct states tracked
  (upstream steam, spray water, mixed, platen outlet).
- **Spray plant rule enforced in the engine:** spray water is always subcooled and within
  100–180 °C — violations raise typed engineering-readable errors.
- **Scenario editor tab:** arbitrary multi-event timelines — any number of events per quantity
  (steam flow, spray flow/temperature, burners, firing fraction, pressure, steam temperature),
  each with its own time, value, enable switch and inline validation; disabled events never fire.
- **Firing-fraction events** scale the burner heat dynamically (Q = n·rating·fraction·F_platen).
- **Every thermodynamic parameter accessible in the UI** after a run — ρ, cp, μ, k, Pr, Re,
  Nu, h_i, U, velocity, enthalpy, heat inputs, all temperatures — each selectable for plotting.
- Validation screen: constant-U vs dynamic-h_i vs uniform model comparison, the
  800 vs ~170 W/m²K discrepancy surfaced explicitly, historian-data calibration
  (F_platen, h_o) with MAE/RMSE/max/bias/R².
- "Show calculation" transparency: every displayed quantity traceable at the end state.

## Build

```bash
# requires JDK 17 and an Android SDK (local.properties points to it)
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Test

```bash
./gradlew :app:testDebugUnitTest   # 29 tests, all green
```

Tests include official IAPWS-IF97 verification points (Region 1 Table 5, Region 2 Table 15,
viscosity/conductivity verification states), flow/heat-transfer consistency, load
monotonicity, burner/spray/combined transients, and timestep convergence (spec §22).

## Architecture

```
UI (Compose) → Screens → PlatenSimulator → SteamProperties (IF97)
                             ↓
              FlowModel / HeatTransfer / OverallU
                             ↓
              BurnerModel / SprayModel / ScenarioState
```

The engine (`com.platen4.superheater.engine`) is pure Kotlin/JVM — no Android dependencies —
and runs in ordinary unit tests. `SteamProperties` is an interface: the IF97 implementation
(`If97SteamProperties`) can be swapped for another property engine (e.g. CoolProp) without
touching the rest of the code.

## IF97 library licensing

`app/src/main/java/com/hummeling/if97/` contains the vendored source of
[IF97 by Hummeling Engineering BV](https://www.if97.software) (v2.1.0), used under the
**GNU LGPL** (see `LICENSE.txt` in that folder). It is kept in its original package with
attribution intact. If you distribute this app, you must comply with LGPL terms
(see lgpl.txt).
