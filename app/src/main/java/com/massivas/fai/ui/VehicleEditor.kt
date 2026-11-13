package com.massivas.fai.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Tela cheia para adicionar/editar um VIN. O botão Salvar fica sempre visível no rodapé. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditor(initial: Vehicle?, vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
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
    var moreOpen by remember {
        mutableStateOf(initial != null && (initial.color != null || initial.forecast != null || initial.difficulty != null || initial.notes.isNotBlank()))
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var savedCount by remember { mutableStateOf(0) }
    val vinFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()

    BackHandler(onBack = onClose)

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
            savedCount++
            Toast.makeText(ctx, "${v.vin} salvo", Toast.LENGTH_SHORT).show()
            reset()
        } else onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Fechar") } },
                title = {
                    Column {
                        Text(if (current == null) "Novo VIN" else "Editar VIN", fontWeight = FontWeight.Bold)
                        if (savedCount > 0) Text("$savedCount salvos nesta sequência", fontSize = 12.sp)
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (current == null) {
                        OutlinedButton(
                            onClick = { trySave(next = true) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text("Salvar e próximo", fontWeight = FontWeight.SemiBold) }
                    }
                    Button(
                        onClick = { trySave(next = false) },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Salvar", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // VIN
            OutlinedTextField(
                value = vin,
                onValueChange = { vin = it.uppercase().filter { c -> c.isLetterOrDigit() } },
                label = { Text("VIN") },
                placeholder = { Text("Ex.: VB002035") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                supportingText = {
                    if (duplicate != null) Text(
                        "Já cadastrado (${duplicate.model.label} · ${duplicate.status.label}). Salvar vai atualizar.",
                        color = Color(0xFFE65100),
                    )
                },
                modifier = Modifier.fillMaxWidth().focusRequester(vinFocus),
            )

            // Modelo
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Modelo")
                Grid(CarModel.entries.toList(), columns = 2) { m, mod ->
                    SelectTile(selected = model == m, onClick = { model = m; modelTouched = true }, modifier = mod) { c ->
                        Column {
                            Text(m.label, fontWeight = FontWeight.Bold, color = c)
                            Text(m.code, fontSize = 11.sp, color = c.copy(alpha = 0.75f))
                        }
                    }
                }
            }

            // Estações
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Estações de reparo")
                Grid(Station.entries.toList(), columns = 2) { s, mod ->
                    val idx = repairs.indexOfFirst { it.station == s }
                    SelectTile(
                        selected = idx >= 0,
                        onClick = { if (idx >= 0) repairs.removeAt(idx) else repairs.add(Repair(s)) },
                        modifier = mod,
                        selectedColor = s.color,
                        selectedContentColor = Color.White,
                        accent = s.color,
                    ) { c ->
                        Text(s.label, fontWeight = FontWeight.SemiBold, color = c, modifier = Modifier.weight(1f))
                        if (idx >= 0) Text("✓", color = c, fontWeight = FontWeight.Bold)
                    }
                }
                repairs.forEachIndexed { i, r ->
                    RepairNoteRow(
                        repair = r,
                        onNote = { repairs[i] = r.copy(note = it) },
                        onDone = { repairs[i] = r.copy(done = it) },
                    )
                }
            }

            // Status
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Status")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    Status.entries.forEach { s ->
                        SelectTile(
                            selected = status == s,
                            onClick = { status = s },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            selectedColor = s.color,
                            selectedContentColor = Color.White,
                        ) { c ->
                            Text(
                                when (s) {
                                    Status.PROCESSO -> "No processo"
                                    Status.TUNEL -> "Túnel"
                                    Status.LIBERADO -> "Liberado ✅"
                                },
                                color = c,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            // Mais detalhes (opcionais)
            Surface(
                onClick = { moreOpen = !moreOpen },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mais detalhes (opcional)", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Cor, previsão, probabilidade de liberação e observações",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(if (moreOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                }
            }

            AnimatedVisibility(visible = moreOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Cor")
                        Grid(CarColor.entries.toList(), columns = 4, spacing = 6.dp) { c, mod ->
                            SelectTile(
                                selected = color == c,
                                onClick = { color = if (color == c) null else c },
                                modifier = mod,
                            ) { tc ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    ColorDot(c, 18)
                                    Text(c.label, fontSize = 12.sp, color = tc)
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Probabilidade de liberação")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Difficulty.entries.forEach { d ->
                                SelectTile(
                                    selected = difficulty == d,
                                    onClick = { difficulty = if (difficulty == d) null else d },
                                    modifier = Modifier.weight(1f),
                                    selectedColor = d.color,
                                    selectedContentColor = d.onColor,
                                    accent = d.color,
                                ) { c -> Text(d.label, color = c, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Previsão do reparo")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { pickDateTime(ctx, forecast) { forecast = it } },
                                modifier = Modifier.height(48.dp),
                            ) { Text("⏱  " + (forecast?.let { TextFormat.formatDate(it) } ?: "Escolher data e hora")) }
                            if (forecast != null) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { forecast = null }) { Text("Limpar") }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Observações") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
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
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935)),
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RepairNoteRow(repair: Repair, onNote: (String) -> Unit, onDone: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = repair.station.color.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(repair.station.color))
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repair.station.label,
                        fontWeight = FontWeight.Bold,
                        color = repair.station.color,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Concluído", fontSize = 12.sp)
                    Checkbox(checked = repair.done, onCheckedChange = onDone)
                }
                OutlinedTextField(
                    value = repair.note,
                    onValueChange = onNote,
                    placeholder = { Text("O que falta? (opcional)") },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        }
    }
    HorizontalDivider(color = Color.Transparent)
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
