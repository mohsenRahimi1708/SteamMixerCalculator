# Engineering documentation — equations and sources

## 1. Property engine (IAPWS-IF97)

All properties come from the vendored `com.hummeling.if97` v2.1.0 library (LGPL).
The facade `SteamProperties` exposes pure SI (Pa, K, J/kg, kg/m³, J/kg·K, Pa·s, W/m·K);
the library's DEFAULT unit system (MPa, K, kJ/kg, kg/m³, kJ/kg·K, Pa·s, W/m·K) is
converted exactly once, inside `If97SteamProperties`.

Verification points used in tests (official IAPWS-IF97 tables):

| Quantity | State | Official value |
|---|---|---|
| h | 3 MPa, 300 K | 115.331273 kJ/kg (Table 5) |
| h | 3 MPa, 500 K | 975.542239 kJ/kg (Table 5) |
| h | 80 MPa, 300 K | 184.142828 kJ/kg (Table 5) |
| h | 30 MPa, 700 K | 2631.494744 kJ/kg (Table 15) |
| ρ | 3 MPa, 300 K | 997.85 kg/m³ (v = 0.100215168e-2) |
| ρ | 0.0035 MPa, 700 K | 0.01083 kg/m³ (v = 0.923015898e2) |
| cp | 3 MPa, 300 K | 4.173012 kJ/kg·K |
| cp | 0.0035 MPa, 700 K | 2.081412 kJ/kg·K |
| μ | 0.1 MPa, 298.15 K | 0.890022551 mPa·s |
| μ | 20 MPa, 873.15 K | 0.0339743835 mPa·s |
| μ | 60 MPa, 673.15 K | 0.0726093560 mPa·s |
| k | 0.1 MPa, 298.15 K | 0.607509806 W/m·K |
| k | 40 MPa, 673.15 K | 0.398506911 W/m·K |
| T_sat | 16.7 MPa | 623.988 K |

T(P,h) uses the library's Region 2 backward equation implementation
(`temperaturePH`), which reproduces the guide's worked example:
T(16.7 MPa, 3347 kJ/kg) = 520.0 °C.

## 2. Flow (spec §5)

```
A_flow = π·D_i²/4            D_i = 41 mm, 43 panels × 4 tubes
ṁ_panel = ṁ_total / n_panels
ṁ_tube  = ṁ_panel / 4
v = ṁ_tube / (ρ·A_flow)
```

The equal-split assumption is explicit. At full load (277.78 kg/s, 167 bar/520 °C,
ρ ≈ 52.1 kg/m³) the 41 mm ID gives v ≈ 24 m/s and Re ≈ 1.5·10⁶ — physically correct for
superheated steam.

## 3. Heat transfer (spec §6–§7)

Dittus–Boelter with heating exponent n = 0.4:

```
Re = ρ·v·D_i/μ
Pr = cp·μ/k
Nu = 0.023·Re^0.8·Pr^0.4
h_i = Nu·k/D_i
```

Validity (Pr 0.7–160, Re > 10 000) is checked and warnings surfaced, never silently ignored.

Overall U (cylindrical resistances, per outer area):

```
1/(U·A_o) = 1/(h_i·A_i) + ln(D_o/D_i)/(2π·k_wall·L) + R_fouling + 1/(h_o·A_o)
```

Every resistance is exposed (`OverallU.Terms`) with the dominant term identified.
U is recomputed every timestep from the current state — never a constant.

## 4. Burner / furnace (spec §8)

```
Q_platen = n_burners · 40 MW · firing_fraction · F_platen
```

F_platen default 0.15 (platen gets part of total furnace release). Burner changes ramp
over `burnerRampSeconds` (default 60 s).

## 5. Spray mixing (spec §9–§10)

```
h_mix = (ṁ_s·h_s + ṁ_sp·h_sp) / (ṁ_s + ṁ_sp)
T_mix = T(P, h_mix)        (IF97 Region 2 backward)
Q_spray = ṁ_sp · (h_s − h_mix)
```

**Plant rule (enforced, typed error):** spray water must be subcooled
(T < T_sat at mixing pressure) and within **100–180 °C**. Spray temperature is a scenario
input (`SPRAY_TEMP_K` events) with default 150 °C.

## 6. Uniform transient model (spec §11–§13, refactored)

The platen coil is one uniform tube (single material 12Cr2MoWVTiB, OD 57 / ID 41 mm,
50 m × 172 tubes) with two lumped state variables — the metal node and the steam node:

```
C_m · dT_m/dt = Q_platen − h_i·A_i·(T_m − T_s) − Q_loss
C_s · dT_s/dt = ṁ·cp·(T_in − T_s) + h_i·A_i·(T_m − T_s)
```

C_m = wall cross-section × length × 172 tubes × 7850 kg/m³, times the metal cp
(520 J/kg·K); C_s = ρ(P,T_s)·steam volume. The initial metal temperature is a user
input (default 450 °C).

The steam balance is integrated with an exponential-relaxation form

```
τ = max(C_s/(ṁ·cp + h_i·A_i), Δt)
dT_s/dt = (T_steady − T_s)/τ,   T_steady = (ṁ·cp·T_in + h_i·A_i·T_m)/(ṁ·cp + h_i·A_i)
```

which preserves the exact steady-state fixed point (same energy balance) while keeping
explicit RK4 stable for any timestep. Metal balances are integrated in raw form by RK4
(metal τ ~ minutes).

## 7. Validation & calibration (spec §16–§17)

Three model levels run side by side: constant-U single block, dynamic-h_i single block,
uniform-lumped transient. The 800 W/m²K (plant YAML, low load) vs ~170 W/m²K
(Dittus–Boelter, full load) discrepancy is displayed, not hidden.

Calibration fits F_platen and h_o by grid search minimizing RMSE against imported
historian data; reports MAE, RMSE, max error, bias, R². Fitted values are returned as
separate labeled-empirical results and never overwrite physical parameters.

## 8. Reference targets (§3.3)

The model reproduces (not hard-codes) the reference values: tube metal mass ≈ 62.5 t,
metal+header capacity ≈ 36.3 MJ/K, steam-side capacity ≈ 1.35 MJ/K, τ ≈ 66 s and θ ≈ 88 s
at 20% load. `StepMetrics` computes θ/τ from simulated step responses.
