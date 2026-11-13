package com.massivas.fai.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.massivas.fai.BuildConfig
import com.massivas.fai.CarModel
import com.massivas.fai.MainViewModel
import com.massivas.fai.Station
import com.massivas.fai.Status
import com.massivas.fai.TextFormat
import com.massivas.fai.Vehicle
import com.massivas.fai.normalize
import com.massivas.fai.vinComparator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()

    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Vehicle?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var stationFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var modelFilter by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.checkOnStart() }

    val filtered = remember(vehicles, query, statusFilter, stationFilter, modelFilter) {
        val q = normalize(query)
        vehicles.filter { v ->
            (statusFilter == null || v.status.name == statusFilter) &&
                (modelFilter == null || v.model.name == modelFilter) &&
                (stationFilter == null || v.pendingRepairs.any { it.station.name == stationFilter }) &&
                (q.isEmpty() || v.vin.contains(q.replace(" ", "")) ||
                    normalize(v.notes).contains(q) || v.repairs.any { normalize(it.note).contains(q) })
        }
    }
    val groups = remember(filtered) {
        CarModel.entries.mapNotNull { m ->
            val g = filtered.filter { it.model == m }.sortedWith(vinComparator)
            if (g.isEmpty()) null else m to g
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Buscar VIN ou reparo") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Column {
                            Text("Massivas Fai", fontWeight = FontWeight.Bold)
                            Text("v${BuildConfig.VERSION_NAME}", fontSize = 11.sp)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) query = ""
                        searching = !searching
                    }) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Buscar")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Importar lista (texto)") }, onClick = {
                                menuOpen = false; importOpen = true
                            })
                            DropdownMenuItem(text = { Text("Compartilhar lista") }, onClick = {
                                menuOpen = false
                                share(ctx, TextFormat.export(vehicles, includeReleased = true))
                            })
                            DropdownMenuItem(text = { Text("Compartilhar só pendentes") }, onClick = {
                                menuOpen = false
                                share(ctx, TextFormat.export(vehicles, includeReleased = false))
                            })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Verificar atualizações") }, onClick = {
                                menuOpen = false; vm.checkForUpdate()
                            })
                            DropdownMenuItem(text = { Text("Remover liberados") }, onClick = {
                                menuOpen = false; confirmClear = "released"
                            })
                            DropdownMenuItem(text = { Text("Apagar tudo") }, onClick = {
                                menuOpen = false; confirmClear = "all"
                            })
                            DropdownMenuItem(text = { Text("Sobre") }, onClick = {
                                menuOpen = false; aboutOpen = true
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; editorOpen = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Adicionar VIN") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                SummaryBar(vehicles, statusFilter) { statusFilter = it }
            }
            item {
                ChipRow {
                    CarModel.entries.forEach { m ->
                        val count = vehicles.count { it.model == m }
                        if (count > 0) {
                            FilterChip(
                                selected = modelFilter == m.name,
                                onClick = { modelFilter = if (modelFilter == m.name) null else m.name },
                                label = { Text("${m.label} · $count") },
                            )
                        }
                    }
                }
            }
            item {
                ChipRow {
                    Station.entries.forEach { s ->
                        val count = vehicles.count { v -> v.status != Status.LIBERADO && v.pendingRepairs.any { it.station == s } }
                        FilterChip(
                            selected = stationFilter == s.name,
                            onClick = { stationFilter = if (stationFilter == s.name) null else s.name },
                            label = { Text(if (count > 0) "${s.label} · $count" else s.label) },
                            leadingIcon = {
                                Box(Modifier.width(10.dp).height(10.dp).background(s.color, RoundedCornerShape(50)))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = s.color,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (vehicles.isEmpty()) "Nenhum VIN ainda" else "Nada encontrado com esses filtros",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (vehicles.isEmpty()) "Toque em \"Adicionar VIN\" ou importe uma lista pelo menu ⋮"
                            else "Toque num filtro selecionado para removê-lo",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            groups.forEach { (model, list) ->
                stickyHeader(key = "h_${model.name}") {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                model.label.uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "  ${model.code}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.weight(1f))
                            val pend = list.count { it.status != Status.LIBERADO }
                            Text(
                                "${list.size} VINs · $pend pend.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                items(list, key = { it.id }) { v ->
                    VehicleCard(
                        v = v,
                        onClick = { editing = v; editorOpen = true },
                        onStatus = { vm.setStatus(v.id, it) },
                        onDelete = { vm.delete(v.id) },
                    )
                }
            }
        }
    }

    if (editorOpen) {
        VehicleEditor(
            initial = editing,
            vm = vm,
            onDismiss = { editorOpen = false },
        )
    }

    if (importOpen) {
        ImportDialog(vm = vm, onDismiss = { importOpen = false })
    }

    confirmClear?.let { what ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text(if (what == "all") "Apagar todos os VINs?" else "Remover liberados?") },
            text = {
                Text(
                    if (what == "all") "Todos os ${vehicles.size} VINs serão apagados. Não dá para desfazer."
                    else "${vehicles.count { it.status == Status.LIBERADO }} VINs liberados serão removidos da lista."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "all") vm.clearAll() else vm.clearReleased()
                    confirmClear = null
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text("Cancelar") } },
        )
    }

    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("Massivas Fai") },
            text = {
                Text(
                    "Versão ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})\n\n" +
                        "Organiza os VINs de massivas por modelo e ordem numérica, com estações de reparo, " +
                        "previsão e probabilidade de liberação.\n\n" +
                        "Segure um VIN para mudar o status rapidamente."
                )
            },
            confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("OK") } },
        )
    }

    UpdateDialogs(update, vm)

    LaunchedEffect(update) {
        if (update is com.massivas.fai.UpdateState.UpToDate) {
            Toast.makeText(ctx, "Você já está na versão mais recente", Toast.LENGTH_SHORT).show()
            vm.dismissUpdate()
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SummaryBar(vehicles: List<Vehicle>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryTile("Total", vehicles.size, MaterialTheme.colorScheme.primary, selected == null, Modifier.weight(1f)) { onSelect(null) }
        Status.entries.forEach { s ->
            SummaryTile(
                when (s) {
                    Status.PROCESSO -> "Processo"
                    Status.TUNEL -> "Túnel"
                    Status.LIBERADO -> "Liberados"
                },
                vehicles.count { it.status == s },
                s.color,
                selected == s.name,
                Modifier.weight(1f),
            ) { onSelect(if (selected == s.name) null else s.name) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummaryTile(label: String, count: Int, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.combinedClickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$count",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else color,
            )
            Text(
                label,
                fontSize = 11.sp,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun VehicleCard(v: Vehicle, onClick: () -> Unit, onStatus: (Status) -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val released = v.status == Status.LIBERADO
    val overdue = !released && v.forecast != null && v.forecast < System.currentTimeMillis()

    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier.width(6.dp).fillMaxHeight()
                        .background(v.difficulty?.color ?: if (released) Status.LIBERADO.color else Color.Transparent)
                )
                Column(Modifier.padding(12.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            v.vin,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        v.color?.let {
                            Spacer(Modifier.width(8.dp))
                            ColorDot(it)
                            Text(" ${it.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.weight(1f))
                        if (v.status != Status.PROCESSO) Pill(text = v.status.label, color = v.status.color)
                    }
                    if (v.repairs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            v.repairs.forEach { r ->
                                Pill(text = (if (r.done) "✓ " else "") + r.station.label, color = r.station.color, strike = r.done)
                            }
                        }
                        v.repairs.filter { it.note.isNotBlank() }.forEach { r ->
                            Row(Modifier.padding(top = 4.dp)) {
                                Box(
                                    Modifier.padding(top = 5.dp).width(8.dp).height(8.dp)
                                        .background(r.station.color, RoundedCornerShape(50))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    r.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (r.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    if (v.notes.isNotBlank()) {
                        Text(
                            v.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (!released && (v.forecast != null || v.difficulty != null)) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            v.forecast?.let {
                                Text(
                                    (if (overdue) "⚠ Atrasado · " else "⏱ Prev. ") + TextFormat.formatDate(it),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (overdue) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            v.difficulty?.let {
                                Pill(text = "Nível ${it.label}", color = it.color, textColor = it.onColor)
                            }
                        }
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Status.entries.filter { it != v.status }.forEach { s ->
                DropdownMenuItem(text = { Text("Marcar: ${s.label}") }, onClick = { menu = false; onStatus(s) })
            }
            DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onClick() })
            DropdownMenuItem(text = { Text("Excluir", color = Color(0xFFE53935)) }, onClick = { menu = false; confirmDelete = true })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir ${v.vin}?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

private fun share(ctx: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(intent, "Compartilhar lista"))
}

@Composable
private fun ImportDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { TextFormat.parse(text) }
    val withoutModel = parsed.count { it.model == null && vm.findByVin(it.vin) == null }
    var fallback by remember { mutableStateOf(vm.lastModel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar lista") },
        text = {
            Column {
                Text(
                    "Cole a lista (ex.: do WhatsApp). Linhas com o nome do modelo (CS55, UNI-T, C928…) separam os grupos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    placeholder = { Text("CS55 - 2\nVB002035 - Liberado ✅\nVB002207 - Ex Ok - Falta Delta") },
                )
                Spacer(Modifier.height(8.dp))
                val newCount = parsed.count { vm.findByVin(it.vin) == null }
                Text("${parsed.size} VINs encontrados · $newCount novos · ${parsed.size - newCount} atualizados")
                if (withoutModel > 0) {
                    Text(
                        "$withoutModel VINs sem modelo. Usar:",
                        color = Color(0xFFE65100),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CarModel.entries.forEach { m ->
                            FilterChip(selected = fallback == m, onClick = { fallback = m }, label = { Text(m.label) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed.isNotEmpty(), onClick = {
                val r = vm.importParsed(parsed, fallback)
                Toast.makeText(ctx, "${r.added} adicionados, ${r.updated} atualizados", Toast.LENGTH_LONG).show()
                onDismiss()
            }) { Text("Importar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
