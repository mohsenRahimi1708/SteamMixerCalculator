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
     → Q (uniform coil)
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
| Tube OD / wall / ID | 57 / 8 / 41 mm |
| Header feed | Dual mid-point (panels 1–21 / 22–43) |

**Uniform platen material** (refactored model — the coil is treated as one uniform
tube of a single material):

| Material | ρ (kg/m³) | cp (J/kg·K) | k (W/m·K) |
|---|---|---|---|
| 12Cr2MoWVTiB | 7850 | 520 | 32 |

**Initial platen metal temperature is a user input** (default **450 °C**) and enters
the calculation as the starting point of the metal energy balance.

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

The platen coil is modeled as **one uniform tube** (refactored model — segmentation
removed for simplicity): a single material (12Cr2MoWVTiB), uniform heat flux, and two
lumped state variables:

- **Metal temperature** `T_m` — the tube-wall metal lump (dominant inertia; starts at
  the user's Metal T input, default 450 °C)
- **Steam temperature** `T_s` — the steam in the coil

Each timestep the model:

1. Reads the current inputs (steam flow, spray flow/temperature, burners, pressure,
   steam temperature) — including any enabled scenario events due at this time.
2. If spray is active, performs the **mixing calculation** (see §5): enthalpy balance →
   mixed enthalpy → mixed temperature via the IF97 backward equation T(P,h).
3. Evaluates properties (ρ, μ, cp, k) from IF97 at the current steam state, computes
   velocity → Re → Pr → Nu → h_i, then U from the cylindrical resistance chain.
4. Computes the heat flows and integrates both energy balances with the
   **RK4** method (4th-order Runge–Kutta).
5. Records every thermodynamic parameter for the results and charts.

**Why the metal matters:** the tube metal holds ~36 MJ/K — thousands of times more
thermal capacity than the steam in the tubes. When burners step up, the metal heats
first and the steam follows; that lag is exactly what the model reproduces.

**Flow distribution assumption (explicit):** total platen steam flow splits equally
across all 43 panels; within a panel the 4 tubes share equally. So one tube carries
`ṁ_total / 172`. At full load (277.8 kg/s) and 167 bar / 520 °C (ρ ≈ 52 kg/m³) the
41 mm ID gives ≈ 24 m/s tube velocity and Re ≈ 1.5·10⁶ — physically correct for
superheated steam.

---

## 4. The Simulate tab — step by step

### 4.1 Initial state fields

| Field | Meaning | Default |
|---|---|---|
| Steam flow t/h | Total platen steam flow at t = 0 | 300 |
| Pressure bar | Steam pressure at the platen inlet (absolute) | 100 |
| Steam T °C | Steam temperature entering the platen at t = 0 | 400 |
| Spray T °C (100–180) | Spray-water temperature — inside the enforced window | 150 |
| Metal T °C | Initial platen metal temperature — a **user input** | 450 |
| Burners on | Number of burners firing at t = 0 | 2 |
| Duration min | Simulation length | 15 |

### 4.2 Scenarios — the Scenarios tab

Scenario events live in their own **Scenarios** tab (Simulate | **Scenarios** |
Validation | Help | About). There you can build an **arbitrary multi-event timeline**:

- Tap **+ Add**, pick a quantity (steam flow, spray flow, spray temperature, burners
  firing, firing fraction, steam pressure, steam temperature) and tap **Add**.
- Each event card has its own **time** (s), target **value** (in display units), an
  **Enabled** switch and a **✕** remove button.
- **Any number of events per quantity** — e.g. three steam-flow steps at 200 s, 500 s
  and 800 s — each applied at its own time.
- Invalid rows show an inline, engineering-readable error (bad time, zero flow,
  burners > 24, fraction outside 0–1) and are skipped at run time.
- Disabled events never fire; all enabled ones apply in time order. One switch on =
  **single-scenario** run; several on = **mixed-scenario** run.

Back on the **Simulate** tab the scenario section lists every enabled event in time
order, warns about enabled-but-invalid rows, and offers **Disable all events** for a
quick baseline run. The state is shared — edits in either tab are the same timeline.

Events the engine understands: burner-count changes ramp over 60 s (configurable in
the engine) instead of stepping; all other kinds step at their event time.

### 4.3 Running

Tap **RUN SIMULATION**. The engine integrates with dt = 1 s. Results appear below:
summary numbers first, then the parameter-selection chips and charts, then the
calculation-details panel.

If anything is wrong with the inputs (e.g. spray outside 100–180 °C, non-physical
pressure), a red error card appears with an engineering-readable explanation instead
of a crash.

---

## 5. Spray water rules (important)

The app **enforces the plant rule** for spray water:

> **Spray water is always subcooled and between 100 and 180 °C — in any situation.**

Concretely, the engine checks, before every mixing calculation:

1. **Window:** spray temperature must be **between 100 °C and 180 °C**.
2. **Subcooling:** spray temperature must also be **below the saturation temperature
   Tsat** at the mixing pressure. At 20 bar, Tsat ≈ 212 °C, so the window binds; below
   ≈ 13 bar, Tsat drops inside the window and subcooling binds first.

If either check fails, the simulation refuses with a typed error explaining exactly
which condition failed and by how much — for example:

```
Spray temperature 200.0 °C outside the 100-180 °C plant window
Spray water must be subcooled: T=120.0 °C >= Tsat=110.8 °C at 1.50 bar
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
4. **Platen outlet**: computed by the uniform-coil energy balance

```
h_mix = (ṁ_steam·h_steam + ṁ_spray·h_spray) / (ṁ_steam + ṁ_spray)
T_mix = T(P, h_mix)          ← IF97 Region 2 backward equation
```

The cooling power attributable to the spray is `Q_spray = ṁ_spray·(h_steam − h_mix)`.

---

## 6. Scenario events

Internally every input is a **time series** built from events. The **Scenarios** tab
edits arbitrary event lists — any number of events per quantity, each with its own
time, value and enable switch:

| Event kind | Units | Default behavior |
|---|---|---|
| Steam flow | kg/s (UI: t/h) | Step at event time |
| Spray flow | kg/s (UI: t/h) | Step (or ramp if spray ramp configured) |
| Spray temperature | K (UI: °C) | Step; must stay in 100–180 °C |
| Burners firing | count | **Ramped** over 60 s (configurable), not stepped |
| Firing fraction | 0–1 | Step |
| Steam pressure | Pa (UI: bar) | Step |
| Steam temperature | K (UI: °C) | Step |

The example scenario from the specification is the default scenario set (each event
ships disabled — enable the ones you want):

| t (s) | Event |
|---|---|
| 0 | Steam flow = 300 t/h, 2 burners on, spray = 0 |
| 300 | Steam flow → 450 t/h |
| 400 | Spray 0 → 5 t/h |
| 500 | Burners 2 → 3 |

You can add further events to the same timeline — e.g. a firing-fraction drop to 0.8 at
600 s, or a second spray step at 700 s.

---

## 7. Reading the results and charts

### Summary numbers (at end of simulation)

| Row | Meaning |
|---|---|
| Outlet T (start → end) | Outlet steam temperature of the uniform coil, start vs end |
| Metal T (start → end) | Lumped metal temperature (starts at your Metal T input) |
| Mixed T (end) | Post-spray mixed temperature (equals steam T if no spray) |
| Velocity (end) | Steam velocity in one tube, m/s |
| h_i (end) | Internal convection coefficient, W/m²K |
| U (end) | Overall heat-transfer coefficient, W/m²K |
| Re (end) | Reynolds number in a tube |
| Q absorbed (end) | Heat picked up by the steam, MW |
| ρ / cp (end) | IF97 density and heat capacity at the outlet state |

### Charts — every parameter selectable

After a run, **every thermodynamic parameter** is available as a toggle chip:

Outlet T, Metal T, Mixed T, Steam inlet T, Pressure, Steam flow, Spray flow, Burner
heat, Heat absorbed, Velocity, Re, Pr, Nu, h_i, U, ρ, cp, μ, k, h — tap any combination
to plot them versus time.

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
| **Uniform (RK4)** | Uniform-lumped transient model (metal + steam nodes) | The production model |

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
A_flow   = π·D_i²/4                D_i = 41 mm
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

### 11.6 Uniform energy balances

```
C_metal · dT_m/dt = Q_platen − h_i·A_i·(T_m − T_s) − Q_loss
C_steam · dT_s/dt = ṁ·cp·(T_in − T_s) + h_i·A_i·(T_m − T_s)
```

One metal node and one steam node for the whole coil (uniform model). The steam
balance is integrated in exponential-relaxation form with
`τ = max(C_steam/(ṁ·cp + h_i·A_i), Δt)` — the same steady state, stable at any
timestep.

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
| `Spray temperature X °C outside the 100-180 °C plant window` | Spray T outside 100–180 °C | Set spray temperature inside the window |
| `Spray water must be subcooled: T=… >= Tsat=… at … bar` | Spray T ≥ Tsat at mixing pressure | Lower spray temperature below Tsat |
| `IF97 state out of range: …` | Pressure/temperature outside IF97 validity (e.g. > 100 MPa, < 0 °C) | Bring inputs into the physical range |
| `Steam flow must be positive` | Zero/negative flow entered | Enter a positive flow |
| `Simulation error at t=…` | A state went non-physical mid-run | Reduce event magnitude, check initial state |

All errors are typed results — the app never crashes on bad input; it explains.

---

## 14. FAQ and troubleshooting

**Q: The outlet temperature keeps rising the whole run — is that wrong?**
A: Probably not. The metal starts at your Metal T input (default 450 °C); if the
burner heat input exceeds what the flow carries away, the system is heating toward a
higher equilibrium and 15 minutes is not enough to settle. Extend the duration or
compare spray-on vs spray-off runs.

**Q: Why is tube velocity ~24 m/s at full load?**
A: Superheated steam at 167 bar/520 °C has density ≈ 52 kg/m³ — a tenth of water.
With the 41 mm ID this gives ≈ 24 m/s and Re ≈ 1.5·10⁶ — comfortably turbulent and
physically correct.

**Q: Why does the metal respond before the outlet?**
A: The furnace heats the metal directly; the steam only receives heat through the
metal. That cascade delay is the dominant transient behavior of a superheater.

**Q: Can I set spray to 200 °C to test the limit?**
A: The engine will refuse — that is the plant rule working as intended. Spray water is
always subcooled and within 100–180 °C in this plant.

**Q: Can I change the metal temperature?**
A: Yes — Metal T °C is a user input (default 450 °C) and enters the calculation as the
initial temperature of the metal node.

**Q: Can I run just one scenario?**
A: Yes — every scenario card has its own switch. One switch on = single-scenario run;
several switches on = mixed-scenario run. Disabled scenarios never fire.

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
./gradlew :app:testDebugUnitTest   # 29 tests, all green
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

*Document version 2.1.0 — reflects the refactored uniform model and the Scenarios tab
(arbitrary multi-event timelines with per-event enable switches). For the full
engineering derivation of every equation, see `ENGINEERING.md` in the repository.*
