package com.massivas.fai.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.massivas.fai.CarColor
import com.massivas.fai.CarModel
import com.massivas.fai.Difficulty
import com.massivas.fai.MainViewModel
import com.massivas.fai.Repair
import com.massivas.fai.Station
import com.massivas.fai.Status
import com.massivas.fai.TextFormat
import com.massivas.fai.Vehicle
import com.massivas.fai.cleanVin
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleEditor(initial: Vehicle?, vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(initial) }
    var vin by remember { mutableStateOf(initial?.vin ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: vm.lastModel) }
    var modelTouched by remember { mutableStateOf(initial != null) }
    var color by remember { mutableStateOf(initial?.color) }
    var status by remember { mutableStateOf(initial?.status ?: Status.PROCESSO) }
    val repairs = remember { mutableStateListOf<Repair>().apply { addAll(initial?.repairs ?: emptyList()) } }
    var forecast by remember { mutableStateOf(initial?.forecast) }
    var difficulty by remember { mutableStateOf(initial?.difficulty) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val vinFocus = remember { FocusRequester() }

    val cleaned = cleanVin(vin)
    val duplicate = if (cleaned.isEmpty()) null else vm.findByVin(cleaned)?.takeIf { it.id != current?.id }

    LaunchedEffect(cleaned) {
        if (!modelTouched) vm.suggestModel(cleaned)?.let { model = it }
    }
    LaunchedEffect(Unit) { if (initial == null) vinFocus.requestFocus() }

    fun reset() {
        current = null
        vin = ""
        status = Status.PROCESSO
        repairs.clear()
        forecast = null
        difficulty = null
        notes = ""
        color = null
        modelTouched = false
        vinFocus.requestFocus()
    }

    fun trySave(next: Boolean) {
        val m = model
        if (cleaned.length < 4) {
            Toast.makeText(ctx, "Digite o VIN", Toast.LENGTH_SHORT).show(); return
        }
        if (m == null) {
            Toast.makeText(ctx, "Escolha o modelo", Toast.LENGTH_SHORT).show(); return
        }
        val base = current ?: duplicate
        val v = Vehicle(
            id = base?.id ?: vm.newId(),
            vin = cleaned,
            model = m,
            color = color,
            status = status,
            repairs = repairs.toList(),
            forecast = forecast,
            difficulty = difficulty,
            notes = notes.trim(),
            createdAt = base?.createdAt ?: System.currentTimeMillis(),
        )
        vm.save(v)
        if (next) {
            Toast.makeText(ctx, "${v.vin} salvo", Toast.LENGTH_SHORT).show()
            reset()
        } else onDismiss()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Fechar") } },
                    title = { Text(if (current == null) "Novo VIN" else "Editar ${current?.vin}") },
                    actions = {
                        if (current != null) {
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
                        }
                    },
                )
            },
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().imePadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (current == null) {
                        OutlinedButton(onClick = { trySave(next = true) }, modifier = Modifier.weight(1f)) {
                            Text("Salvar e próximo")
                        }
                    }
                    Button(onClick = { trySave(next = false) }, modifier = Modifier.weight(1f)) { Text("Salvar") }
                }
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = vin,
                    onValueChange = { vin = it.uppercase() },
                    label = { Text("VIN") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = {
                        when {
                            duplicate != null -> Text(
                                "Já existe (${duplicate.model.label}, ${duplicate.status.label}) — será atualizado",
                                color = Color(0xFFE65100),
                            )
                            cleaned.isNotEmpty() -> Text("${cleaned.length} caracteres")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(vinFocus),
                )

                Section("Modelo") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CarModel.entries.forEach { m ->
                            FilterChip(
                                selected = model == m,
                                onClick = { model = m; modelTouched = true },
                                label = {
                                    Column {
                                        Text(m.label, fontWeight = FontWeight.SemiBold)
                                        Text(m.code, fontSize = 10.sp)
                                    }
                                },
                            )
                        }
                    }
                }

                Section("Cor (opcional)") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CarColor.entries.forEach { c ->
                            FilterChip(
                                selected = color == c,
                                onClick = { color = if (color == c) null else c },
                                label = { Text(c.label) },
                                leadingIcon = { ColorDot(c) },
                            )
                        }
                    }
                }

                Section("Status") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Status.entries.forEach { s ->
                            FilterChip(
                                selected = status == s,
                                onClick = { status = s },
                                label = { Text(s.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = s.color,
                                    selectedLabelColor = Color.White,
                                ),
                            )
                        }
                    }
                }

                Section("Estações de reparo") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Station.entries.forEach { s ->
                            val idx = repairs.indexOfFirst { it.station == s }
                            FilterChip(
                                selected = idx >= 0,
                                onClick = { if (idx >= 0) repairs.removeAt(idx) else repairs.add(Repair(s)) },
                                label = { Text(s.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = s.color,
                                    selectedLabelColor = Color.White,
                                    labelColor = s.color,
                                ),
                            )
                        }
                    }
                    repairs.forEachIndexed { i, r ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = r.station.color.copy(alpha = 0.10f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Pill(text = r.station.label, color = r.station.color, strike = r.done)
                                    Spacer(Modifier.weight(1f))
                                    Text("Concluído", fontSize = 12.sp)
                                    Checkbox(checked = r.done, onCheckedChange = { repairs[i] = r.copy(done = it) })
                                }
                                OutlinedTextField(
                                    value = r.note,
                                    onValueChange = { repairs[i] = r.copy(note = it) },
                                    placeholder = { Text("Descrição do reparo (opcional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                )
                            }
                        }
                    }
                }

                Section("Previsão de reparo (opcional)") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickDateTime(ctx, forecast) { forecast = it } }) {
                            Text(forecast?.let { TextFormat.formatDate(it) } ?: "Escolher data e hora")
                        }
                        if (forecast != null) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { forecast = null }) { Text("Limpar") }
                        }
                    }
                }

                Section("Probabilidade de liberação (opcional)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.entries.forEach { d ->
                            FilterChip(
                                selected = difficulty == d,
                                onClick = { difficulty = if (difficulty == d) null else d },
                                label = { Text(d.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = d.color,
                                    selectedLabelColor = d.onColor,
                                ),
                                leadingIcon = { ColorDotRaw(d.color) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir ${current?.vin}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        current?.let { vm.delete(it.id) }
                        confirmDelete = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935)),
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

private fun pickDateTime(ctx: Context, initial: Long?, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance()
    if (initial != null) cal.timeInMillis = initial
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, min ->
            cal.set(y, m, d, h, min, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onPicked(cal.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}
