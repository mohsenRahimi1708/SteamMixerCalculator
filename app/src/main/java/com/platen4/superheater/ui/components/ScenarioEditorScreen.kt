package com.platen4.superheater.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.platen4.superheater.engine.EventRow
import com.platen4.superheater.engine.ScenarioEvent
import com.platen4.superheater.engine.ScenarioTimeline

/**
 * Scenario editor (refactored spec §15): arbitrary time-stamped events, any number
 * per quantity, each with its own time, value and enable switch. The rows live in
 * the shared app state (hoisted to [com.platen4.superheater.ui.screens.MainScreen])
 * so the Simulate tab runs exactly this timeline.
 */
@Composable
fun ScenarioEditorScreen(
    rows: List<EventRow>,
    onRowsChange: (List<EventRow>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nextId by remember { mutableStateOf((rows.maxOfOrNull { it.id } ?: 0L) + 1L) }
    var addKind by remember { mutableStateOf(ScenarioEvent.Kind.STEAM_FLOW_KGS) }
    var kindDropdown by remember { mutableStateOf(false) }

    fun update(transform: (List<EventRow>) -> List<EventRow>) = onRowsChange(transform(rows))

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Scenario timeline", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add any number of events per quantity, each with its own time. Enabled events " +
                "apply at their times; disabled ones never fire. Run everything from the Simulate tab.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))

        // ---- Add-event row ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("Add event", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { kindDropdown = true }) {
                            Text("${ScenarioTimeline.displayLabel(addKind)} ▾")
                        }
                        DropdownMenu(expanded = kindDropdown, onDismissRequest = { kindDropdown = false }) {
                            ScenarioEvent.Kind.entries.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(ScenarioTimeline.displayLabel(kind)) },
                                    onClick = {
                                        addKind = kind
                                        kindDropdown = false
                                    },
                                )
                            }
                        }
                    }
                    Button(onClick = {
                        update { it + ScenarioTimeline.newRow(nextId, addKind) }
                        nextId += 1
                    }) { Text("+ Add") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (rows.isEmpty()) {
            Text("No events yet — add one above.", style = MaterialTheme.typography.bodyMedium)
        }

        // ---- Event rows ----
        rows.forEachIndexed { index, row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                ScenarioTimeline.displayLabel(row.kind),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "unit: ${ScenarioTimeline.displayUnit(row.kind)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Enabled", style = MaterialTheme.typography.labelSmall)
                        Switch(
                            checked = row.enabled,
                            onCheckedChange = { on ->
                                update { list ->
                                    list.mapIndexed { i, r -> if (i == index) r.copy(enabled = on) else r }
                                }
                            },
                        )
                        TextButton(onClick = { update { list -> list.filterIndexed { i, _ -> i != index } } }) {
                            Text("✕")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = row.timeText,
                            onValueChange = { v ->
                                update { list ->
                                    list.mapIndexed { i, r -> if (i == index) r.copy(timeText = v) else r }
                                }
                            },
                            label = { Text("t (s)", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = row.valueText,
                            onValueChange = { v ->
                                update { list ->
                                    list.mapIndexed { i, r -> if (i == index) r.copy(valueText = v) else r }
                                }
                            },
                            label = { Text("→ ${ScenarioTimeline.displayUnit(row.kind)}", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ScenarioTimeline.validate(row)?.let { msg ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        val enabledCount = rows.count { it.enabled && ScenarioTimeline.validate(it) == null }
        Text(
            "$enabledCount event(s) will fire. Scenario order does not matter — events are applied in time order.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Burner-count events ramp over 60 s instead of stepping; " +
                "spray values must obey the 100–180 °C subcooled window (checked at run time).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
    }
}
