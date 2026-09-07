# Steam Mixer Calculator v3 — Operator & Shift Engineer Guide

**App:** Steam Mixer Calculator v3 (Android)
**Calculation engine:** IAPWS-IF97 industrial formulation via `com.hummeling:if97` (LGPL)
**Audience:** Boiler operators, shift engineers, commissioning and process engineers
**Document version:** 1.0 — September 2026

> ⚠️ **Safety notice.** This app is an *engineering estimation tool*. It is **not** a
> substitute for the boiler OEM's documentation, the plant DCS/controls logic, or your
> site operating procedures. Never change spray-water control or burner firing based on
> this app alone — always cross-check against plant instrumentation and follow your
> management-of-change process.

---

## 1. Purpose and Scope

The application calculates **spray-water attemperation** behaviour for a platen
superheater. It answers three practical questions:

| # | Question | Screen |
|---|----------|--------|
| 1 | *"I want the outlet at X °C — how much spray water do I need?"* | **Mode A — Required Spray** |
| 2 | *"I am injecting Y t/h of spray — what outlet temperature will I actually get?"* | **Mode B — Spray Impact** |
| 3 | *"After burner ignition, how fast does the platen metal heat up and how does spray demand grow?"* | **Transient Simulation** |

A fourth screen, **Energy Balance**, shows the enthalpy chain behind any result so you
can see *why* the app produced that number.

All results are computed **on the phone, offline** — no internet connection is used or
required. Steam/water properties come from the international standard **IAPWS-IF97**
formulation (the same basis used by commercial steam-table software), not from
simplified Cp shortcuts.

---

## 2. Quick Start (shift floor use)

1. Pick the screen matching your question (bottom tabs).
2. Enter **all** fields — every field is required, including *Initial metal temperature*.
3. Press **CALCULATE**. The result card shows the number **plus the full set of
   assumptions and any warnings**. Never write down the number without the assumptions.
4. If a warning appears (e.g. *thermal shock advisory* or *wet steam*), read Section 8
   before acting on the result.

---

## 3. Calculation Modes Overview

| Mode | Type | You provide | It solves | Iterations |
|------|------|-------------|-----------|------------|
| **A — Required Spray** | Reverse | Target outlet temperature | Required spray-water flow | None (closed-form) |
| **B — Spray Impact** | Forward | Spray-water flow | Resulting outlet temperature | None (IF97 backward equation) |
| **Transient** | Time-stepped simulation | Full burner + metal data | Metal temperature & spray demand vs. time | Euler stepping (dt) |

### 2.1 Unit conventions (used everywhere in the app)

| Quantity | Unit |
|----------|------|
| Mass flow | t/h (tonnes per hour) |
| Pressure | bar (absolute) |
| Temperature | °C |
| Specific enthalpy | kJ/kg |
| Heat rate | MW |
| Metal mass | kg |
| Specific heat | J/(kg·K) |
| Time | seconds |

Useful conversions: `1 t/h = 0.2778 kg/s`; `Q [MW] = ṁ [t/h] × Δh [kJ/kg] / 3600`.

---

## 4. Input Parameter Reference

These fields appear on every calculation screen. **All are mandatory.**

| Parameter | Symbol | Valid range (app) | Typical value | Notes |
|-----------|--------|-------------------|---------------|-------|
| Steam mass flow | ṁ_s | > 0 | 600 t/h | Main steam flow through the platen |
| Steam pressure | P | 20 – 170 bar | 167 bar | Absolute pressure at the attemperator |
| Steam inlet temperature | T_s | 200 – 560 °C | 540 °C | Must be superheated (checked automatically) |
| Target outlet temperature | T_out | ≤ T_s | 520 °C | Mode A and Transient only |
| Spray-water flow | ṁ_w | ≥ 0, ≤ 2 × ṁ_s | 10 t/h | Mode B only; 2× limit guards against input error |
| Spray-water temperature | T_w | 0 – 600 °C, < T_sat(P) | 230 °C | Must be **subcooled liquid** — checked automatically |
| Initial platen metal temperature | T_m0 | 0 – 700 °C | 540 °C | Starting metal condition; also drives the thermal-shock advisory |
| Platen metal mass | M | > 0 | 62 600 kg | Transient only |
| Metal specific heat | Cp | > 0 | 500 J/(kg·K) | Alloy-steel value; Transient only |
| Burner heat per burner | Q_burner | > 0 | 40 MW | Transient only |
| Number of burners | N | ≥ 1 | 4 | Transient only |
| Burner-to-platen fraction | η | 0.0 – 1.0 | 0.07 | Fraction of burner heat absorbed by the platen |
| Time step | dt | > 0 | 10 s | Transient only |
| Simulation duration | — | > 0 | 600 s | Transient only |

**Why is initial metal temperature required everywhere, even for steady-state modes?**
Because a steady-state mixing calculation says nothing about the metal's thermal
condition. Recording it (a) documents the state the plant was in when the estimate was
made, and (b) drives the *thermal-shock advisory* (Section 8.3). It never silently
changes your result.

---

## 5. Mode A — Required Spray (reverse calculation)

### 5.1 What it does
Given the steam condition and a desired outlet temperature, it computes the spray-water
flow needed at the mixing point, assuming adiabatic mixing at constant pressure.

### 5.2 Formula

```
h_s = h(P, T_s)          steam enthalpy        (IF97 Region 2)
h_w = h(P, T_w)          spray-water enthalpy  (IF97 Region 1, compressed liquid)
h_out = h(P, T_target)   target enthalpy       (IF97 Region 2)

Energy balance:  ṁ_s·h_s + ṁ_w·h_w = (ṁ_s + ṁ_w)·h_out

Solved for spray flow:

        ṁ_w = ṁ_s · (h_s − h_out) / (h_out − h_w)          ← closed form, no iteration
```

### 5.3 Worked example (computed with the IAPWS-IF97 engine)

**Example A1** — Typical main-steam attemperator:

| Input | Value |
|-------|-------|
| Steam flow | 600 t/h |
| Pressure | 167 bar (T_sat = 350.8 °C) |
| Steam inlet temp | 540 °C |
| Target outlet temp | 520 °C |
| Spray-water temp | 230 °C |

Chain: `h_s = 3404.3`, `h_out = 3347.0`, `h_w = 993.4 kJ/kg`

```
ṁ_w = 600 × (3404.3 − 3347.0) / (3347.0 − 993.4)
    = 600 × 57.3 / 2353.6
    = 14.61 t/h          combined outlet flow = 614.6 t/h
```

**Example A2** — Reheater-class conditions: 350 t/h, 100 bar (T_sat = 311.0 °C),
480 → 460 °C, spray 180 °C → `h_s = 3322.9`, `h_out = 3269.5`, `h_w = 767.8`
→ **7.47 t/h** spray, combined 357.5 t/h.

**Example A3** — IP/low-pressure platen: 200 t/h, 50 bar (T_sat = 263.9 °C),
400 → 380 °C, spray 150 °C → `h_s = 3196.6`, `h_out = 3146.8`, `h_w = 635.1`
→ **3.96 t/h** spray, combined 204.0 t/h.

### 5.4 Automatic input checks (Mode A)
- Steam flow must be positive.
- Target temperature must be ≤ steam inlet temperature.
- Steam must be **superheated** at (P, T_s) — else rejected with the local T_sat.
- Spray water must be **subcooled liquid** (T_w < T_sat at P) — else rejected.
- If the spray-water enthalpy ≥ target enthalpy, cooling is physically impossible —
  reported as an infinite spray requirement with a warning.

---

## 6. Mode B — Spray Impact (forward calculation)

### 6.1 What it does
Given the spray flow you actually intend to inject, it computes the resulting outlet
temperature and the temperature drop. This is the "what happens if I use this much
spray" mode — no target temperature involved.

### 6.2 Formula

```
h_out = (ṁ_s·h_s + ṁ_w·h_w) / (ṁ_s + ṁ_w)     mixing-point enthalpy

T_out = T(P, h_out)                            IF97 backward equation — direct call
ΔT    = T_s − T_out
```

The outlet temperature comes from the official IF97 *backward* equation `T(P, h)` —
not from iteration or a Cp approximation — so Mode B is exact within IF97's
industrial accuracy.

### 6.3 Worked examples (600 t/h, 167 bar, 540 °C, spray 230 °C)

| Spray flow | h_out (kJ/kg) | T_out (°C) | ΔT (K) |
|-----------:|--------------:|-----------:|-------:|
| 0 t/h | 3404.3 | 540.0 (inlet) | 0.0 |
| 10 t/h | 3364.8 | 526.1 | 13.9 |
| 25 t/h | 3307.8 | 506.7 | 33.3 |
| 60 t/h | 3185.1 | 467.3 | 72.7 |

*Reading the table:* the first 10 t/h of spray buys ~14 K of cooling; going from 10 to
25 t/h (an extra 15 t/h) buys another ~19 K. Spray demand is **non-linear** — this is
why guessing from a single previous data point often misleads.

### 6.4 Automatic input checks (Mode B)
- Spray flow must be non-negative and ≤ 2 × steam flow (larger values are rejected as
  probable input errors).
- Same superheated-steam and subcooled-spray checks as Mode A.
- Zero spray is allowed and simply returns the inlet temperature with an explanatory
  warning.

---

## 7. Transient Simulation (burner ignition / platen thermal inertia)

### 7.1 Physical model

The platen superheater's metal mass is a real thermal capacitance that responds on its
own timescale. The model treats the platen as a lumped (uniform-temperature) mass:

```
Thermal capacitance:   C = M · Cp                        [kJ/K]

Burner heat to platen: Q_in = η · N · Q_burner           [MW]

Spray heat removal:    Q_spray = ṁ_w · (h_target − h_w) / 3600    [MW]

Net heat:              Q_net = Q_in − Q_spray            [MW]

Metal energy balance:  dT_metal/dt = Q_net · 1000 / C    [K/s]

Integration:           T(t+dt) = T(t) + Q_net · 1e6 · dt / (M·Cp)   (explicit Euler)
```

At every time step the model assumes the **steam leaving the platen would be at the
metal temperature** (simplified heat transfer), computes the spray flow that would hold
the target outlet temperature, removes that heat from the metal, and steps forward.

### 7.2 Worked example — default inputs, ignition from 540 °C metal

600 t/h steam, 167 bar, target 520 °C, spray 230 °C, M = 62 600 kg,
Cp = 500 J/(kg·K), 4 × 40 MW burners, η = 0.07 (→ Q_in = 11.2 MW), dt = 10 s.

| Time | Metal temp (°C) | Spray demand (t/h) | Spray heat (MW) | Net heat (MW) |
|-----:|----------------:|-------------------:|----------------:|--------------:|
| 0 s | 540.00 | 14.61 | 9.55 | +1.65 |
| 60 s | 542.19 | 16.18 | 10.58 | +0.62 |
| 120 s | 543.01 | 16.77 | 10.97 | +0.23 |
| 300 s | 543.48 | 17.11 | 11.19 | +0.01 |
| 600 s | 543.51 | 17.13 | 11.20 | +0.00 |

*Reading the table:* starting with metal at the steam inlet temperature, spray demand
**rises** from 14.6 to ~17.1 t/h within minutes as the metal accumulates the unbalanced
burner heat, then stabilises. The chart on the screen plots exactly these curves.

### 7.3 What the transient mode is good for
- Estimating how fast spray demand grows after burner ignition or a load jump.
- Checking whether your spray control valve has enough **range and response speed**.
- Understanding why a steady-state estimate made on a cold platen under-predicts
  demand minutes later.

---

## 8. Warnings and Advisories (exact trigger conditions)

Every result card shows the number **plus** all triggered warnings. These are the
conditions the engine checks and the exact text triggers:

### 8.1 Wet steam / over-spray warning
**Trigger:** predicted outlet enthalpy falls below the saturation enthalpy at pressure
(Mode A), or outlet temperature ≤ T_sat(P) (Mode B).
**Meaning:** you are over-spraying — outlet flow would be wet steam. Wet steam in a
superheater circuit risks water carry-over, turbine erosion, and tube thermal stress.
The steady-state mixing model is **not valid** for wet outlet conditions.

### 8.2 No-cooling / impossible-cooling warnings
- Target ≥ steam inlet temperature → *no spray required* (Mode A).
- Spray-water enthalpy ≥ target enthalpy → *cooling not possible*; spray requirement is
  reported as infinite.

### 8.3 Thermal-shock advisory (all modes)
**Trigger:** `T_steam − T_initial_metal > 50 °C` (fixed threshold constant in the app).
**Text:** *"Steam temperature significantly exceeds current platen metal temperature —
expect a real response lag beyond this steady-state estimate; consider the transient
model instead."*
**Meaning:** the mixing number is still correct for the steam side, but the platen
metal will lag and may see transient thermal stress. Switch to the Transient screen to
quantify the lag.

### 8.4 Input rejections (hard stops)
| Condition | Applies to |
|-----------|-----------|
| Steam flow ≤ 0 | A, B |
| Pressure outside 20–170 bar | A, B, Transient |
| Steam temp outside 200–560 °C | A, B, Transient |
| Target temp > steam temp | A, Transient |
| Spray flow < 0 or > 2× steam flow | B |
| Spray temp outside 0–600 °C or ≥ T_sat(P) | A, B |
| Metal temp outside 0–700 °C | A, B, Transient |
| Steam not superheated at inlet | A, B, Transient |
| Metal mass / Cp / burner heat ≤ 0, burners < 1 | Transient |
| η outside 0.0–1.0, dt or duration ≤ 0 | Transient |

A rejected input shows a red error card with the exact reason — the app never
silently defaults a value.

---

## 9. Energy Balance Screen

Tapping **View Full Energy Balance** on any result opens a report with:

- **Mode label** (A / B / Transient) — which model produced the number.
- **Input conditions** — steam flow, pressure, inlet temp, target temp, spray temp and
  flow, initial metal temperature.
- **Steam-side enthalpies** — h_steam(P, T_in), h_spray(P, T_w), h_out/target.
- **Calculation result** — spray flow or outlet temperature with the temperature drop.

This is your "why?" screen: use it when you need to justify a number in a shift log or
compare the app's chain against a plant heat-balance calculation.

---

## 10. Model Assumptions and Limitations

Know these before trusting a number:

1. **Adiabatic mixing at constant pressure.** No heat losses from the attemperator to
   ambient; no pressure drop across the spray point.
2. **Spray fully evaporated into superheated steam.** The mixing equations assume the
   outlet is superheated. Wet-steam outcomes are flagged as warnings, not modelled.
3. **Complete, instantaneous mixing.** No spray evaporation lag, no distribution
   effects along the header, no thermal fatigue of the spray nozzle itself.
4. **IF97 industrial accuracy.** Property values comply with the IAPWS-IF97
   industrial standard (typically ±0.05 % in the superheated region) — not
   thermodynamic-reference (IAPWS-95) accuracy.
5. **Transient model simplifications.** Lumped metal temperature (no internal
   gradients); steam leaving the platen assumed at metal temperature; η constant over
   time; explicit Euler integration (keep dt ≤ 10 s for smooth curves).
6. **Steady-state modes ignore metal inertia entirely** — that is exactly what the
   thermal-shock advisory and Transient mode are for.

---

## 11. Practical Guidance for Operations

**Use Mode A when** the control target is given (e.g. "hold 520 °C after the platen")
and you want the required spray setpoint.

**Use Mode B when** the spray valve position/flow is fixed (e.g. manual standby
control) and you want to predict where the outlet temperature will settle.

**Cross-check the two modes:** take Mode A's required spray flow, feed it into Mode B —
you must get your original target temperature back. The app's own unit tests do exactly
this; it is also a good field sanity check.

**Cold platen ignition:** if the thermal-shock advisory fires, the steady answer
applies *now* but demand will **drift upward** as the metal heats — see the worked
transient table in Section 7.2, where 14.6 t/h became 17.1 t/h in ten minutes under
11.2 MW of unbalanced burner heat.

**Approximate sensitivity** at 167 bar / 540 °C / 230 °C spray (from the Mode B table):
every **1 t/h** of spray gives roughly **1.3–1.4 K** of cooling near these conditions.
This factor is *not* constant — recompute when load or pressure changes.

**Always compare with DCS:** the app assumes adiabatic mixing with no losses and no
desuperheater effectiveness losses. Real installations typically need *more* spray than
the ideal calculation (often 5–15 % more) because of imperfect evaporation and mixing.
Use the app for estimation and sanity-checking, not as a control setpoint source.

---

## 12. FAQ / Troubleshooting

**Q: Why does the app reject my spray temperature?**
Spray water must be subcooled liquid at the attemperator pressure. At 167 bar,
T_sat = 350.8 °C — a spraywater temperature at or above this cannot exist as liquid.

**Q: Why does the result show a huge/infinite spray flow?**
Your spray-water enthalpy is at or above the target enthalpy — with that spray
temperature you can never reach the target. Lower the target, or (physically) the
spraywater condition is wrong.

**Q: Why is initial metal temperature mandatory even for a quick steady-state calc?**
It documents the plant state behind the estimate and enables the thermal-shock
advisory. It never alters the mixing number.

**Q: The app says "wet steam / over-spray". What now?**
Reduce the spray flow (Mode B) or raise the target temperature (Mode A). The mixing
model does not quantify two-phase outlet conditions.

**Q: Can I use it for the reheater?**
Yes, the math is the same — enter reheater pressure, flows and temperatures.

---

## 13. References and License

- **IAPWS-IF97**, *Revised Release on the IAPWS Industrial Formulation 1997 for the
  Thermodynamic Properties of Water and Steam* (incl. backward equations
  T(P,h), T(P,s) — used directly by this app for Mode B).
- **`com.hummeling:if97`** Java/Kotlin library, LGPL 2.1 — implements IF97 Regions
  1–5, saturation line, and backward equations. This copyleft-friendly license is the
  reason the app uses it instead of the GPL-family Python steam libraries.
- App unit tests cross-validate Mode A ↔ Mode B (spray flow from A reproduces the
  target temperature in B) and verify the transient model against the analytical
  no-spray case. 36 unit tests, all passing.

---

*Steam Mixer Calculator v3 — built for the people who run the boiler.*
