# Platen Superheater Transient Simulation — Complete User Guide

**Application:** Unit №4 Platen Superheater Transient Simulator (Android)
**Platform:** Android 8.0+ (API 26+)
**Property engine:** IAPWS-IF97 (Regions 1, 2, 4) — vendored `com.hummeling.if97` v2.1.0
**Version:** 1.0.0

---

## Table of Contents

1. [What the application does](#1-what-the-application-does)
2. [Installing the app](#2-installing-the-app)
3. [How the simulation works — the big picture](#3-how-the-simulation-works--the-big-picture)
4. [The Simulate tab — step by step](#4-the-simulate-tab--step-by-step)
5. [Spray water rules (important)](#5-spray-water-rules-important)
6. [Scenario events](#6-scenario-events)
7. [Reading the results and charts](#7-reading-the-results-and-charts)
8. [Show calculation — full transparency](#8-show-calculation--full-transparency)
9. [The Validation tab — model comparison](#9-the-validation-tab--model-comparison)
10. [Calibration against plant data](#10-calibration-against-plant-data)
11. [The physics inside — equations used](#11-the-physics-inside--equations-used)
12. [Units](#12-units)
13. [Error messages and what they mean](#13-error-messages-and-what-they-mean)
14. [FAQ and troubleshooting](#14-faq-and-troubleshooting)
15. [Building the app from source](#15-building-the-app-from-source)

---

## 1. What the application does

This is an **engineering simulation tool**, not a static calculator. It computes how the
**outlet steam temperature of the platen superheater** in a 325 MW natural-circulation
drum boiler (Unit №4) responds **over time** to:

| # | Driver |
|---|--------|
| 1 | Steam mass flow / load changes |
| 2 | Spray-water flow changes at the platen inlet |
| 3 | Burner start/stop events |
| 4 | Furnace heat-input changes |
| 5 | Steam inlet pressure changes |
| 6 | Steam inlet temperature changes |
| 7 | Property changes with P and T (density ρ, cp, μ, k) |
| 8 | Internal convection coefficient h_i changing with flow and properties |
| 9 | Reynolds number changes with load |
| 10 | Prandtl number changes with steam state |
| 11 | Nusselt number changes with Re and Pr |
| 12 | Dynamic overall heat-transfer coefficient U |
| 13 | Tube-metal thermal inertia (the dominant delay source) |
| 14 | Steam thermal capacity / residence time |
| 15 | Spray injection cooling dynamics |

The causal chain is physical, top to bottom:

```
P, T → IF97 properties (ρ, μ, cp, k)
     → velocity → Re → Pr → Nu → h_i → U
     → Q (per segment)
     → metal energy balance → steam energy balance
     → steam outlet T (with spray enthalpy + burner heat entering dynamically)
```

**U is a calculated, load-dependent output — never a hard-coded constant.**

### Plant reference data (locked, Unit №4)

| Parameter | Value |
|---|---|
| Boiler capacity | 325 MW |
| Type | Natural circulation, drum-type |
| Burners | 24 × 40 MW, 3 elevations |
| Full-load steam flow | 1000–1040 t/h |
| Sliding pressure range | 10–167 bar |
| Sliding steam temperature | ~200–540 °C |
| Parallel panels | 43 |
| Tubes per panel | 4 (172 total) |
| Passes per panel | 8, pass length 6 m |
| Tube OD / wall / ID | 45 / 8 / 29 mm |
| Inner (steam-side) area | ~783 m² |
| Outer (furnace-side) area | ~1216 m² |
| Header feed | Dual mid-point (panels 1–21 / 22–43) |

**Sections in flow order** (this is the order steam actually flows, and the order the
model uses):

| # | Section | Length | Material |
|---|---------|--------|----------|
| 1 | Inlet casing | 0.5 m | 12Cr1MoV |
| 2 | Lower radiant | 15.0 m | SA-213 T91 |
| 3 | Inner horizontal | 20.0 m | 12Cr2MoWVTiB |
| 4 | Upper horizontal | 12.0 m | 12Cr2MoWVTiB |
| 5 | Outlet casing | 2.5 m | 12Cr1MoV |

---

## 2. Installing the app

1. Obtain `app-debug.apk` (built from `./gradlew :app:assembleDebug`, located in
   `app/build/outputs/apk/debug/`).
2. Copy it to your Android device (USB, cloud, or direct download).
3. On the device, open the APK. Android will ask to allow installation from that source
   ("Install unknown apps") — allow it once.
4. Tap **Install**, then open **Platen Superheater Transient**.

No special permissions are required. The app works fully offline — the IF97 property
library is compiled into the app.

---

## 3. How the simulation works — the big picture

The platen is **not** modeled as one lump. It is divided into the **five physical
sections** listed above, and each section carries its own state:

- **Metal temperature** `T_m` — the tube-wall metal lump (dominant inertia)
- **Steam temperature** `T_s` — the steam inside that section

Each timestep the model:

1. Reads the current inputs (steam flow, spray flow/temperature, burners, pressure,
   steam temperature) — including any scenario events due at this time.
2. If spray is active, performs the **mixing calculation** (see §5): enthalpy balance →
   mixed enthalpy → mixed temperature via the IF97 backward equation T(P,h).
3. For every segment, evaluates properties (ρ, μ, cp, k) from IF97 at the current
   steam state, computes velocity → Re → Pr → Nu → h_i, then U from the cylindrical
   resistance chain.
4. Computes the per-segment heat flows and integrates both energy balances with the
   **RK4** method (4th-order Runge–Kutta).
5. Passes each segment's outlet steam temperature as the next segment's inlet
   temperature (segment 1 receives the post-spray mixed temperature).
6. Records everything for the charts.

**Why the metal matters:** the tube metal holds ~36 MJ/K — thousands of times more
thermal capacity than the steam in the tubes. When burners step up, the metal heats
first and the steam follows; that lag is exactly what the model reproduces.

**Flow distribution assumption (explicit):** total platen steam flow splits equally
across all 43 panels; within a panel the 4 tubes share equally. So one tube carries
`ṁ_total / 172`. At full load (277.8 kg/s) and 167 bar / 520 °C (ρ ≈ 52 kg/m³) this
gives ≈ 47 m/s tube velocity and Re ≈ 5·10⁵ — physically correct for superheated steam.

---

## 4. The Simulate tab — step by step

### 4.1 Initial state fields

| Field | Meaning | Default |
|---|---|---|
| Steam flow t/h | Total platen steam flow at t = 0 | 100 |
| Pressure bar | Steam pressure at the platen inlet (absolute) | 100 |
| Steam T °C | Steam temperature entering the platen at t = 0 | 400 |
| Spray T °C (<250) | Spray-water temperature — must be below 250 °C | 230 |
| Burners on | Number of burners firing at t = 0 | 2 |
| Duration min | Simulation length | 15 |

### 4.2 Scenario event fields (step at t = 300 s)

One built-in event time (300 s) is provided for quick what-if tests:

| Field | Meaning |
|---|---|
| Flow → t/h | New steam flow applied at t = 300 s (blank/0 = no change) |
| Spray → t/h | New spray flow applied at t = 300 s (0 = spray off) |
| Burners → | New burner count applied at t = 300 s |

Leave all three empty to simulate the initial state alone (useful for seeing the
natural heat-up toward equilibrium).

### 4.3 Running

Tap **RUN SIMULATION**. The engine integrates with dt = 1 s. Results appear below:
summary numbers first, then four charts, then the calculation-details panel.

If anything is wrong with the inputs (e.g. spray hotter than 250 °C, non-physical
pressure), a red error card appears with an engineering-readable explanation instead
of a crash.

---

## 5. Spray water rules (important)

The app **enforces the plant rule** for spray water:

> **Spray water is always subcooled and below 250 °C — in any situation.**

Concretely, the engine checks, before every mixing calculation:

1. **Temperature limit:** spray temperature must be **< 250 °C**.
2. **Subcooling:** spray temperature must be **below the saturation temperature Tsat**
   at the mixing pressure. At 167 bar, Tsat ≈ 350 °C, so the 250 °C limit is the
   binding one there; at 20 bar, Tsat ≈ 212 °C, so subcooling binds first.

If either check fails, the simulation refuses with a typed error explaining exactly
which condition failed and by how much — for example:

```
Spray temperature 262.0 °C exceeds the 250 °C plant limit
Spray water must be subcooled: T=220.0 °C >= Tsat=212.4 °C at 20.00 bar
```

**Why:** desuperheating sprays must evaporate completely in the steam stream. Water
that is saturated or boiling at the injection pressure flashes rather than mixing,
which the single-zone mixing model does not represent. The rule keeps every result in
the physically valid region.

**Spray temperature as an event:** you can also change spray temperature during a
scenario (`SPRAY_TEMP_K` events); the same rule is enforced at every event.

### The mixing calculation (what happens when spray is on)

Four distinct states are always kept separate:

1. **Upstream steam** (pre-spray): P, T, h from IF97
2. **Spray water**: h from IF97 Region 1 at the injection pressure and spray temperature
3. **Mixed state** (post-spray, platen inlet): enthalpy balance → T(P,h)
4. **Platen outlet**: computed by the segment chain

```
h_mix = (ṁ_steam·h_steam + ṁ_spray·h_spray) / (ṁ_steam + ṁ_spray)
T_mix = T(P, h_mix)          ← IF97 Region 2 backward equation
```

The cooling power attributable to the spray is `Q_spray = ṁ_spray·(h_steam − h_mix)`.

---

## 6. Scenario events

Internally every input is a **time series** built from events. The built-in UI exposes
one event time; the engine supports arbitrary lists (the full scenario editor ships in
a coming release):

| Event kind | Units | Default behavior |
|---|---|---|
| Steam flow | kg/s (UI: t/h) | Step at event time |
| Spray flow | kg/s (UI: t/h) | Step (or ramp if spray ramp configured) |
| Spray temperature | K (UI: °C) | Step; must stay < 250 °C |
| Burners firing | count | **Ramped** over 60 s (configurable), not stepped |
| Firing fraction | 0–1 | Step |
| Steam pressure | Pa (UI: bar) | Step |
| Steam temperature | K (UI: °C) | Step |

The example scenario from the specification is exactly the default UI event set:

| t (s) | Event |
|---|---|
| 0 | Steam flow = 100 t/h, 2 burners on, spray = 0 |
| 300 | Steam flow → 150 t/h |
| 300 | Spray 0 → 5 t/h |
| 300 | Burners 2 → 3 |

---

## 7. Reading the results and charts

### Summary numbers (at end of simulation)

| Row | Meaning |
|---|---|
| Outlet T (start → end) | Steam temperature after the last segment, start vs end |
| Metal T avg (end) | Average metal temperature across the 5 segments |
| Mixed T (end) | Post-spray mixed temperature (equals steam T if no spray) |
| h_i (end) | Internal convection coefficient, W/m²K |
| U (end) | Overall heat-transfer coefficient, W/m²K |
| Re (end) | Reynolds number in a tube |
| Q absorbed (end) | Heat picked up by the steam across all segments, MW |

### Charts

Each chart plots the quantity against time; the axis label shows the value range
covered:

1. **Outlet temperature** — the headline result. Watch the step response after events.
2. **Metal temperature** — leads the outlet; shows the thermal-inertia delay.
3. **h_i vs time** — internal convection responding to flow/state changes.
4. **U vs time** — overall coefficient; dominated by the weakest resistance.

**How to interpret a step response:** after a burner step the metal temperature begins
rising immediately, and the outlet follows after a short dead time; after a spray step
the outlet begins falling almost immediately (the spray acts at the inlet), settling
toward a new steady value.

---

## 8. Show calculation — full transparency

Every result must be traceable. Tap **Show calculation details** at the bottom of the
results to see, for the end-of-simulation state:

| Line | Source |
|---|---|
| P, T_out | Current steam state at the outlet |
| ρ | IF97 density at (P, T_out) |
| cp | IF97 isobaric heat capacity |
| μ | IF97 dynamic viscosity |
| k | IF97 thermal conductivity |
| Pr | IF97 Prandtl number |
| Re | ρ·v·D_i/μ from the flow model |
| Nu | 0.023·Re^0.8·Pr^0.4 (Dittus–Boelter) |
| h_i = Nu·k/D_i | internal convection |
| U | cylindrical resistance chain |

This is a core requirement of the tool (engineering traceability), not a convenience
feature.

---

## 9. The Validation tab — model comparison

The app ships **three model levels** and runs them side by side on the same scenario:

| Model | Description | Purpose |
|---|---|---|
| **Constant-U** | Single block, fixed U (800 W/m²K) | Plant-YAML reference behavior |
| **Dynamic h_i** | Single block, h_i from the Re→Pr→Nu chain each step | Isolates the effect of dynamic heat transfer |
| **Segmented (RK4)** | Full 5-segment transient model | The production model |

Tap **Run comparison** to see the end-of-run outlet temperature of all three on the
reference scenario (100→150 t/h and 2→3 burners at t = 300 s).

**The 800 vs ~170 W/m²K discrepancy is surfaced, not hidden.** Plant YAML data gives
U ≈ 800 W/m²K at low load, while the Dittus–Boelter chain gives ~170 W/m²K at full
load. Both paths are provided as separate, clearly labeled modes — the app never
silently blends them. Resolving the gap is a calibration exercise (next section), not
something the app decides for you.

---

## 10. Calibration against plant data

The **Fit parameters** workflow lets you calibrate two effective parameters against
measured plant data:

1. Export a window of historian data containing the **outlet temperature** sampled
   evenly (the simulation grid is 1 s × 900 s; resample your data to match, or paste a
   representative series of at least 10 points).
2. Paste the values (°C), comma- or newline-separated, into the text field.
3. Tap **Fit parameters**.

The fitter grid-searches:

- **F_platen** (fraction of furnace release reaching the platen) in 0.05…0.5
- **h_o** (external/furnace-side convection) in 20…500 W/m²K

and reports fit quality:

| Metric | Meaning |
|---|---|
| MAE | Mean absolute error, K |
| RMSE | Root-mean-square error, K |
| Max error | Worst instantaneous error, K |
| Bias | Mean signed error, K (model too hot or too cold overall) |
| R² | Fraction of variance explained |

**Fitted values are labeled empirical and kept separate** from the physical
parameters — the app never overwrites physics with fitted numbers. Use the fitted
F_platen/h_o as the configuration for subsequent simulation runs.

---

## 11. The physics inside — equations used

### 11.1 Property engine (IAPWS-IF97)

All thermophysical properties come from IAPWS-IF97:
- **Region 1** — compressed/subcooled water (feedwater, spray water)
- **Region 2** — superheated steam (main flow, and T(P,h) backward equation)
- **Region 4** — saturation line (Tsat for the spray subcooling check)

Verified in the test suite against the official IAPWS tables, e.g.:

| Quantity | State | Official value |
|---|---|---|
| h | 3 MPa, 300 K | 115.331 kJ/kg |
| h | 3 MPa, 500 K | 975.542 kJ/kg |
| h | 30 MPa, 700 K | 2631.495 kJ/kg |
| μ | 0.1 MPa, 298.15 K | 0.890022551 mPa·s |
| k | 0.1 MPa, 298.15 K | 0.607509806 W/m·K |
| Tsat | 16.7 MPa | 623.988 K |

### 11.2 Flow

```
A_flow   = π·D_i²/4                D_i = 29 mm
ṁ_tube   = ṁ_total / (43·4)
v        = ṁ_tube / (ρ·A_flow)
Re       = ρ·v·D_i/μ
```

### 11.3 Heat transfer

```
Pr  = cp·μ/k
Nu  = 0.023·Re^0.8·Pr^0.4          (heating; n = 0.3 for cooling)
h_i = Nu·k/D_i
```

Dittus–Boelter validity (Pr 0.7–160, Re > 10 000) is checked and warned, never
silently applied outside its range.

### 11.4 Overall U (cylindrical, per outer area)

```
1/(U·A_o) = 1/(h_i·A_i) + ln(D_o/D_i)/(2π·k_wall·L) + R_fouling + 1/(h_o·A_o)
```

All four resistances are exposed; the dominant one is identified in the results.

### 11.5 Furnace heat input

```
Q_platen = n_burners · 40 MW · firing_fraction · F_platen
```

F_platen default 0.15. Burner changes ramp over 60 s.

### 11.6 Per-segment energy balances

```
C_metal · dT_m/dt = Q_furnace,seg − h_i·A_i·(T_m − T_s) − Q_loss
C_steam · dT_s/dt = ṁ·cp·(T_in − T_s) + h_i·A_i·(T_m − T_s)
```

The steam balance is integrated in exponential-relaxation form with
`τ = max(C_steam/(ṁ·cp + h_i·A_i), Δt)` — the same steady state, stable at any
segment size (the short casing segments hold very little steam).

### 11.7 Solver

RK4, default Δt = 1 s (configurable 0.1–2 s). Verified by the timestep-halving
convergence test: dt = 2 s and dt = 1 s agree within 5 K on the reference scenario.

---

## 12. Units

| Context | Units |
|---|---|
| UI display | bar, °C, t/h, MW, W/m²K |
| Engine internals | SI only: Pa, K, kg/s, J/kg, W, m, kg/m³, J/kg·K, Pa·s, W/m·K |

Conversions happen exactly once, at the UI boundary. Internally nothing is ever
mixed.

---

## 13. Error messages and what they mean

| Message | Cause | What to do |
|---|---|---|
| `Spray temperature X °C exceeds the 250 °C plant limit` | Spray T ≥ 250 °C | Set spray temperature below 250 °C |
| `Spray water must be subcooled: T=… >= Tsat=… at … bar` | Spray T ≥ Tsat at mixing pressure | Lower spray temperature below Tsat |
| `IF97 state out of range: …` | Pressure/temperature outside IF97 validity (e.g. > 100 MPa, < 0 °C) | Bring inputs into the physical range |
| `Steam flow must be positive` | Zero/negative flow entered | Enter a positive flow |
| `Simulation error at t=…` | A state went non-physical mid-run | Reduce event magnitude, check initial state |

All errors are typed results — the app never crashes on bad input; it explains.

---

## 14. FAQ and troubleshooting

**Q: The outlet temperature keeps rising the whole run — is that wrong?**
A: Probably not. The initial metal temperature equals the initial steam temperature;
if the burner heat input exceeds what the flow carries away, the system is heating
toward a higher equilibrium and 15 minutes is not enough to settle. Extend the
duration or compare spray-on vs spray-off runs (the Validation comparison does this).

**Q: Why is tube velocity ~47 m/s at full load? Isn't that high?**
A: Superheated steam at 167 bar/520 °C has density ≈ 52 kg/m³ — a tenth of water.
High velocity at full load is physically correct; Re ≈ 5·10⁵ is comfortably turbulent.

**Q: Why does the metal respond before the outlet?**
A: The furnace heats the metal directly; the steam only receives heat through the
metal. That cascade delay is the dominant transient behavior of a superheater.

**Q: Can I set spray to 300 °C to test the limit?**
A: The engine will refuse — that is the plant rule working as intended. Spray water is
always subcooled and below 250 °C in this plant.

**Q: What pressure should I enter — absolute or gauge?**
A: Absolute (bar a). IF97 works in absolute pressure.

**Q: Where does the IF97 library come from?**
A: `com.hummeling.if97` v2.1.0 by Hummeling Engineering BV, vendored in source form
under the LGPL (license file included in the source tree).

---

## 15. Building the app from source

```bash
# Requirements: JDK 17, Android SDK (see local.properties), internet for Gradle deps
git clone https://github.com/mohsenRahimi1708/SteamMixerCalculator.git
cd SteamMixerCalculator   # checkout unit4-platen-transient
# the app lives in the platen-superheater/ folder of that branch
./gradlew :app:testDebugUnitTest   # 17 tests, all green
./gradlew :app:assembleDebug       # APK at app/build/outputs/apk/debug/app-debug.apk
```

Architecture summary:

```
UI (Compose) → Screens → PlatenSimulator → SteamProperties (IF97)
                             ↓
              FlowModel / HeatTransfer / OverallU
                             ↓
              BurnerModel / SprayModel / ScenarioState
```

The engine package is pure Kotlin/JVM (no Android dependencies) and fully unit-tested;
the property engine sits behind an interface so an alternative implementation (e.g.
CoolProp) can be swapped in without touching the model.

---

*Document version 1.0.0 — generated with the application source. For the full
engineering derivation of every equation, see `ENGINEERING.md` in the repository.*
