package com.steamcalc.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * In-app Help / Operator Guide — bilingual (EN / فا) with search.
 *
 * Condensed version of OPERATOR_GUIDE.md (EN) and OPERATOR_GUIDE_FA.md (FA) so the
 * documentation travels with the APK. Persian content renders right-to-left; formula
 * blocks always render left-to-right.
 */

// ─── Content model ───────────────────────────────────────────────────────

private data class HelpBlock(
    val text: String,
    val formula: Boolean = false,
    val bold: Boolean = false,
    val small: Boolean = false
)

private data class HelpSectionData(
    val title: String,
    val danger: Boolean = false,
    val blocks: List<HelpBlock>
)

private val englishSections: List<HelpSectionData> = listOf(
    HelpSectionData(
        "⚠ Safety First", danger = true, blocks = listOf(
            HelpBlock(
                "This app is an engineering estimation tool. It is NOT a substitute for " +
                        "the boiler OEM documentation, the plant DCS logic, or site operating " +
                        "procedures. Never change spray-water control or burner firing based on " +
                        "this app alone — always cross-check against plant instrumentation."
            )
        )
    ),
    HelpSectionData(
        "The Three Modes", blocks = listOf(
            HelpBlock("A — Required Spray (reverse)", bold = true),
            HelpBlock("\u201CI want the outlet at X °C — how much spray water do I need?\u201D " +
                    "Enter the target outlet temperature; the app solves for the spray flow."),
            HelpBlock("B — Spray Impact (forward)", bold = true),
            HelpBlock("\u201CI am injecting Y t/h of spray — what outlet temperature will I get?\u201D " +
                    "Enter the spray flow; the app computes the resulting outlet temperature and drop."),
            HelpBlock("Transient (time-stepped)", bold = true),
            HelpBlock("After burner ignition, how fast does the platen metal heat up and how does " +
                    "spray demand grow over time? Uses platen metal mass, Cp, burner power and η."),
            HelpBlock("Energy Balance", bold = true),
            HelpBlock("Tap \u201CView Full Energy Balance\u201D on any result to see the enthalpy chain, " +
                    "all inputs and the mode that produced the number — your \u201Cwhy?\u201D screen.")
        )
    ),
    HelpSectionData(
        "Thermodynamic Formulas", blocks = listOf(
            HelpBlock("Mode A — mixing-point energy balance:", bold = true),
            HelpBlock("ṁ_w = ṁ_s · (h_s − h_out) / (h_out − h_w)", formula = true),
            HelpBlock("h_s = h(P, T_steam), h_out = h(P, T_target), h_w = h(P, T_spray) — all from IF97."),
            HelpBlock("Mode B — forward mixing + backward equation:", bold = true),
            HelpBlock(
                "h_out = (ṁ_s·h_s + ṁ_w·h_w) / (ṁ_s + ṁ_w)\n" +
                        "T_out = T(P, h_out)   ← IF97 backward eq.\n" +
                        "ΔT = T_steam − T_out",
                formula = true
            ),
            HelpBlock("Transient — lumped metal capacitance (Euler):", bold = true),
            HelpBlock(
                "C = M · Cp\n" +
                        "Q_in = η · N · Q_burner\n" +
                        "Q_spray = ṁ_w · (h_target − h_w) / 3600\n" +
                        "Q_net = Q_in − Q_spray\n" +
                        "T(t+dt) = T(t) + Q_net·10⁶·dt / (M·Cp)",
                formula = true
            ),
            HelpBlock(
                "Units: flow t/h · pressure bar · temperature °C · enthalpy kJ/kg · " +
                        "heat MW · Q[MW] = ṁ[t/h]·Δh[kJ/kg]/3600.",
                small = true
            )
        )
    ),
    HelpSectionData(
        "Worked Example (engine-computed)", blocks = listOf(
            HelpBlock("600 t/h @ 167 bar (T_sat 350.8 °C), 540 → 520 °C, spray 230 °C:", bold = true),
            HelpBlock(
                "h_s = 3404.3  h_out = 3347.0  h_w = 993.4 kJ/kg\n" +
                        "spray = 600 × 57.3 / 2353.6 = 14.61 t/h",
                formula = true
            ),
            HelpBlock("Mode B sweep at the same conditions:", bold = true),
            HelpBlock(
                "  0 t/h → 540.0 °C (ΔT  0.0 K)\n" +
                        " 10 t/h → 526.1 °C (ΔT 13.9 K)\n" +
                        " 25 t/h → 506.7 °C (ΔT 33.3 K)\n" +
                        " 60 t/h → 467.3 °C (ΔT 72.7 K)",
                formula = true
            ),
            HelpBlock(
                "Spray demand is non-linear — the first 10 t/h buys ~14 K; do not " +
                        "extrapolate from a single data point.",
                small = true
            )
        )
    ),
    HelpSectionData(
        "Warnings — What They Mean", blocks = listOf(
            HelpBlock("Wet steam / over-spray", bold = true),
            HelpBlock("Predicted outlet enthalpy is below saturation at the pressure. Wet steam " +
                    "risks water carry-over, turbine erosion and tube stress. The mixing model " +
                    "is not valid for wet outlet — reduce spray or raise the target."),
            HelpBlock("Cooling not possible", bold = true),
            HelpBlock("Spray-water enthalpy ≥ target enthalpy: with this spray temperature the " +
                    "target cannot be reached. Spray requirement shows as infinite."),
            HelpBlock("Thermal-shock advisory", bold = true),
            HelpBlock("Fires when steam temperature exceeds the initial platen metal temperature " +
                    "by more than 50 °C. The mixing number is still correct, but the metal lags " +
                    "and may see transient stress — use the Transient screen to quantify the lag."),
            HelpBlock("Input rejections", bold = true),
            HelpBlock("Pressure 20–170 bar · steam 200–560 °C · target ≤ steam inlet · spray water " +
                    "must be subcooled (below T_sat) · metal temp 0–700 °C · Mode B spray ≤ 2× " +
                    "steam flow · steam must be superheated. The app never silently defaults a value.")
        )
    ),
    HelpSectionData(
        "Practical Tips for Operations", blocks = listOf(
            HelpBlock("• Cross-check modes: feed Mode A's spray flow into Mode B — you must get " +
                    "your target temperature back."),
            HelpBlock("• Sensitivity at 167 bar / 540 °C / 230 °C spray: every 1 t/h of spray gives " +
                    "roughly 1.3–1.4 K of cooling. Not constant — recompute when load or pressure changes."),
            HelpBlock("• Cold platen: if the thermal-shock advisory fires, spray demand will drift " +
                    "UPWARD as the metal heats. In the default transient case demand rose from " +
                    "14.6 to 17.1 t/h in ten minutes under 11.2 MW of unbalanced burner heat."),
            HelpBlock("• Compare with DCS: the app assumes ideal adiabatic mixing with no losses. " +
                    "Real installations typically need 5–15 % more spray due to imperfect " +
                    "evaporation and mixing. Use for estimation, not as a control setpoint source.")
        )
    ),
    HelpSectionData(
        "FAQ", blocks = listOf(
            HelpBlock("Why is initial metal temperature mandatory everywhere?", bold = true),
            HelpBlock("It documents the plant state behind the estimate and drives the " +
                    "thermal-shock advisory. It never alters the mixing number."),
            HelpBlock("Why is my spray temperature rejected?", bold = true),
            HelpBlock("Spray water must be subcooled liquid at the attemperator pressure — at " +
                    "167 bar, T_sat = 350.8 °C, so anything above that cannot exist as liquid."),
            HelpBlock("Can I use it for the reheater?", bold = true),
            HelpBlock("Yes — the math is identical. Enter reheater pressure, flows and temperatures.")
        )
    ),
    HelpSectionData(
        "About", blocks = listOf(
            HelpBlock("Steam properties: IAPWS-IF97 industrial formulation via the " +
                    "com.hummeling:if97 library (LGPL). All calculations run on-device, offline."),
            HelpBlock("The engine's unit tests cross-validate Mode A ↔ Mode B and verify the " +
                    "transient model against the analytical no-spray case (36 tests passing)."),
            HelpBlock("Full guide: OPERATOR_GUIDE.md in the project repository.", small = true)
        )
    )
)

private val persianSections: List<HelpSectionData> = listOf(
    HelpSectionData(
        "⚠ هشدار ایمنی", danger = true, blocks = listOf(
            HelpBlock(
                "این اپلیکیشن یک ابزار برآورد مهندسی است و جایگزین مستندات سازنده بویلر (OEM)، " +
                        "منطق کنترل DCS پست، یا دستورالعمل‌های بهره‌برداری سایت نیست. هرگز صرفاً بر اساس " +
                        "این اپلیکیشن کنترل آب اسپری یا شارژ مشعل‌ها را تغییر ندهید — همیشه با ابزار " +
                        "دقیق پست کنترل کنید و فرآیند مدیریت تغییر سایت خود را دنبال نمایید."
            )
        )
    ),
    HelpSectionData(
        "سه حالت محاسباتی", blocks = listOf(
            HelpBlock("حالت A — اسپری موردنیاز (معکوس)", bold = true),
            HelpBlock("«می‌خواهم خروجی روی X درجه باشد — چه مقدار آب اسپری لازم است؟» دمای هدف " +
                    "خروجی را وارد کنید؛ برنامه دبی اسپری لازم را حل می‌کند."),
            HelpBlock("حالت B — اثر اسپری (مستقیم)", bold = true),
            HelpBlock("«Y تن بر ساعت اسپری تزریق می‌کنم — دمای خروجی چند درجه می‌شود؟» دبی اسپری " +
                    "را وارد کنید؛ برنامه دمای خروجی و افت دما را محاسبه می‌کند."),
            HelpBlock("شبیه‌سازی گذرا (Transient)", bold = true),
            HelpBlock("بعد از روشن‌شدن مشعل، فلز پلاتن چقدر سریع گرم می‌شود و تقاضای اسپری چگونه " +
                    "رشد می‌کند؟ از جرم فلز، Cp، توان مشعل و η استفاده می‌کند."),
            HelpBlock("موازنه انرژی", bold = true),
            HelpBlock("با زدن «View Full Energy Balance» روی هر نتیجه، زنجیره آنتالپی، همه ورودی‌ها " +
                    "و حالتی که عدد را تولید کرده را می‌بینید — صفحه «چرا؟» شما.")
        )
    ),
    HelpSectionData(
        "فرمول‌های ترمودینامیکی", blocks = listOf(
            HelpBlock("حالت A — موازنه انرژی نقطه اختلاط:", bold = true),
            HelpBlock("ṁ_w = ṁ_s · (h_s − h_out) / (h_out − h_w)", formula = true),
            HelpBlock("h_s = h(P, T_steam)، h_out = h(P, T_target)، h_w = h(P, T_spray) — همگی از IF97."),
            HelpBlock("حالت B — اختلاط مستقیم + معادله معکوس:", bold = true),
            HelpBlock(
                "h_out = (ṁ_s·h_s + ṁ_w·h_w) / (ṁ_s + ṁ_w)\n" +
                        "T_out = T(P, h_out)   ← معادله معکوس IF97\n" +
                        "ΔT = T_steam − T_out",
                formula = true
            ),
            HelpBlock("گذرا — ظرفیت حرارتی متمرکز فلز (اویلر):", bold = true),
            HelpBlock(
                "C = M · Cp\n" +
                        "Q_in = η · N · Q_burner\n" +
                        "Q_spray = ṁ_w · (h_target − h_w) / 3600\n" +
                        "Q_net = Q_in − Q_spray\n" +
                        "T(t+dt) = T(t) + Q_net·10⁶·dt / (M·Cp)",
                formula = true
            ),
            HelpBlock(
                "واحدها: دبی t/h · فشار bar · دما °C · آنتالپی kJ/kg · حرارت MW · " +
                        "Q[MW] = ṁ[t/h]·Δh[kJ/kg]/3600.",
                small = true
            )
        )
    ),
    HelpSectionData(
        "مثال حل‌شده (محاسبه با موتور IF97)", blocks = listOf(
            // Conditions line kept as a forced-LTR formula block: embedded in Persian prose,
            // the bidi algorithm visually reverses "540 → 520" (numbers are neutral-adjacent
            // in RTL paragraphs), which would read as "520 → 540" to an LTR-trained engineer.
            HelpBlock(
                "600 t/h · 167 bar (T_sat 350.8 °C) · 540 → 520 °C · spray 230 °C",
                formula = true
            ),
            HelpBlock(
                "h_s = 3404.3  h_out = 3347.0  h_w = 993.4 kJ/kg\n" +
                        "اسپری = 600 × 57.3 / 2353.6 = 14.61 t/h",
                formula = true
            ),
            HelpBlock("جاروب حالت B در همان شرایط:", bold = true),
            HelpBlock(
                "  0 t/h → 540.0 °C (ΔT  0.0 K)\n" +
                        " 10 t/h → 526.1 °C (ΔT 13.9 K)\n" +
                        " 25 t/h → 506.7 °C (ΔT 33.3 K)\n" +
                        " 60 t/h → 467.3 °C (ΔT 72.7 K)",
                formula = true
            ),
            HelpBlock(
                "تقاضای اسپری غیرخطی است — اولین 10 t/h حدود 14 کلوین می‌خرد؛ از یک نقطه داده " +
                        "منفرد برون‌یابی نکنید.",
                small = true
            )
        )
    ),
    HelpSectionData(
        "هشدارها — معنی آن‌ها", blocks = listOf(
            HelpBlock("بخار تر / اسپری بیش از حد", bold = true),
            HelpBlock("آنتالپی خروجی پیش‌بینی‌شده از اشباع در فشار پایین‌تر است. بخار تر خطر حمل " +
                    "قطرات آب، فرسایش توربین و تنش لوله‌ها دارد. مدل اختلاط برای خروجی تر معتبر " +
                    "نیست — اسپری را کم کنید یا هدف را بالا ببرید."),
            HelpBlock("خنک‌کاری غیرممکن", bold = true),
            HelpBlock("آنتالپی آب اسپری ≥ آنتالپی هدف: با این دمای اسپری نمی‌توان به هدف رسید. " +
                    "نیاز اسپری بی‌نهایت نمایش داده می‌شود."),
            HelpBlock("هشدار شوک حرارتی", bold = true),
            HelpBlock("وقتی دمای بخار بیش از 50 درجه از دمای اولیه فلز پلاتن بالاتر باشد فعال " +
                    "می‌شود. عدد اختلاط هنوز درست است اما فلز عقب می‌ماند و ممکن است تنش گذرا " +
                    "ببیند — برای کمّی‌کردن تأخیر از صفحه گذرا استفاده کنید."),
            HelpBlock("رد ورودی‌ها", bold = true),
            HelpBlock("فشار 20–170 bar · بخار 200–560 °C · هدف ≤ ورودی بخار · آب اسپری باید " +
                    "ساب‌کول باشد (زیر T_sat) · دمای فلز 0–700 °C · اسپری حالت B ≤ دو برابر دبی بخار · " +
                    "بخار باید سوپرهیت باشد. برنامه هرگز مقداری را پنهانی پیش‌فرض نمی‌کند.")
        )
    ),
    HelpSectionData(
        "نکات عملی برای بهره‌برداری", blocks = listOf(
            HelpBlock("• بررسی متقابل حالت‌ها: دبی اسپری حالت A را به حالت B بدهید — باید دمای " +
                    "هدف اولیه خود را برگردانید."),
            HelpBlock("• حساسیت در 167 bar / 540 °C / اسپری 230 °C: هر 1 t/h اسپری تقریباً " +
                    "1.3–1.4 K خنک‌کاری می‌دهد. ثابت نیست — با تغییر بار یا فشار دوباره محاسبه کنید."),
            HelpBlock("• پلاتن سرد: اگر هشدار شوک حرارتی فعال شود، تقاضای اسپری با گرم‌شدن فلز " +
                    "رو به بالا رانده می‌شود. در حالت گذرای پیش‌فرض، تقاضا در ده دقیقه تحت " +
                    "11.2 MW حرارت نامتوازن مشعل از 14.6 به 17.1 t/h رسید."),
            HelpBlock("• مقایسه با DCS: اپ اختلاط آدیاباتیک ایده‌آل بدون تلفات را فرض می‌کند. " +
                    "نصب‌های واقعی معمولاً 5–15 % اسپری بیشتر نیاز دارند. برای برآورد استفاده کنید، " +
                    "نه به‌عنوان منبع ست‌پوینت کنترل.")
        )
    ),
    HelpSectionData(
        "سؤالات متداول", blocks = listOf(
            HelpBlock("چرا دمای اولیه فلز همه‌جا اجباری است؟", bold = true),
            HelpBlock("این مقدار وضعیت پست را پشت برآورد مستند می‌کند و هشدار شوک حرارتی را " +
                    "فعال می‌کند. هرگز عدد اختلاط را تغییر نمی‌دهد."),
            HelpBlock("چرا دمای اسپری من رد می‌شود؟", bold = true),
            HelpBlock("آب اسپری باید در فشار اسپری‌کن مایع ساب‌کول باشد — در 167 bar، " +
                    "T_sat = 350.8 °C، پس بالاتر از آن نمی‌تواند به‌صورت مایع وجود داشته باشد."),
            HelpBlock("می‌توانم برای ری‌هیتر استفاده کنم؟", bold = true),
            HelpBlock("بله — ریاضیات همان است. فشار، دبی‌ها و دماهای ری‌هیتر را وارد کنید.")
        )
    ),
    HelpSectionData(
        "درباره", blocks = listOf(
            HelpBlock("خواص بخار و آب: فرمول صنعتی IAPWS-IF97 از طریق کتابخانه " +
                    "com.hummeling:if97 (LGPL). همه محاسبات روی خود گوشی و آفلاین انجام می‌شود."),
            HelpBlock("تست‌های واحد موتور، حالت A ↔ حالت B را متقابلاً اعتبارسنجی می‌کنند و مدل " +
                    "گذرا را در برابر حالت تحلیلی بدون اسپری بررسی می‌کنند (36 تست موفق)."),
            HelpBlock("راهنمای کامل: OPERATOR_GUIDE.md و OPERATOR_GUIDE_FA.md در مخزن پروژه.", small = true)
        )
    )
)

// ─── Screen ──────────────────────────────────────────────────────────────

@Composable
fun HelpScreen(onBack: () -> Unit) {
    var langFa by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val rtl = langFa
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Header + language toggle ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (langFa) "راهنمای اپراتور" else "Operator Guide",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (langFa) "اسپری بخار · IAPWS-IF97 · نسخه 3"
                        else "Spray attemperation · IAPWS-IF97 · v3",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                LanguageToggle(langFa, onToggle = { langFa = it })
            }

            Spacer(Modifier.height(12.dp))

            // ── Search box ───────────────────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (langFa) "جستجو در راهنما…" else "Search help…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            // ── Filtered sections ────────────────────────────────────
            val allSections = if (langFa) persianSections else englishSections
            val q = query.trim()
            val visible = if (q.isEmpty()) allSections else allSections.filter { section ->
                section.title.contains(q, ignoreCase = true) ||
                        section.blocks.any { it.text.contains(q, ignoreCase = true) }
            }

            if (visible.isEmpty()) {
                Text(
                    if (langFa) "بخشی یافت نشد." else "No matching sections.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            visible.forEach { section ->
                HelpCard(section, langFa)
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                // In RTL the back affordance must point RIGHT (mirrored), so the FA label uses →.
                Text(if (langFa) "→ بازگشت به ماشین‌حساب" else "← Back to Calculator")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LanguageToggle(faSelected: Boolean, onToggle: (Boolean) -> Unit) {
    Row {
        OutlinedButton(
            onClick = { onToggle(false) },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = if (!faSelected) ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) else ButtonDefaults.outlinedButtonColors()
        ) { Text("EN") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(
            onClick = { onToggle(true) },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = if (faSelected) ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) else ButtonDefaults.outlinedButtonColors()
        ) { Text("فا") }
    }
}

@Composable
private fun HelpCard(section: HelpSectionData, rtl: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (section.danger) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface,
            contentColor = if (section.danger) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            section.blocks.forEach { block ->
                when {
                    block.formula -> Formula(block.text)
                    else -> HelpText(
                        block.text,
                        bold = block.bold,
                        small = block.small,
                        rtl = rtl
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpText(text: String, bold: Boolean = false, small: Boolean = false, rtl: Boolean = false) {
    // Persian falls back to Noto Naskh Arabic, which renders visually smaller and tighter
    // than Latin at the same sp — bump the line height in FA mode so diacritics don't clip.
    val base = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val style = if (rtl) base.copy(lineHeight = if (small) 18.sp else 22.sp) else base
    Text(
        text,
        style = style,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.padding(bottom = if (bold) 2.dp else 0.dp)
    )
}

/** Formula blocks are always laid out left-to-right, even inside the RTL Persian layout. */
@Composable
private fun Formula(text: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        )
    }
}
