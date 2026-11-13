package com.massivas.fai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextDecoration
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
import com.massivas.fai.UpdateState
import com.massivas.fai.Vehicle
import com.massivas.fai.normalize
import com.massivas.fai.vinComparator

private const val NEW = "__new__"
private const val QUICK = "__quick__"

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val vehicles by vm.vehicles.collectAsStateWithLifecycle()
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }

    val id = editorId
    if (id == QUICK) {
        QuickAddScreen(vm = vm, onClose = { editorId = null })
    } else if (id != null) {
        val initial = if (id == NEW) null else vehicles.firstOrNull { it.id == id }
        VehicleEditor(initial = initial, vm = vm, onClose = { editorId = null })
    } else {
        ListScreen(
            vm = vm,
            vehicles = vehicles,
            onAdd = { editorId = NEW },
            onQuickAdd = { editorId = QUICK },
            onEdit = { editorId = it.id },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ListScreen(
    vm: MainViewModel,
    vehicles: List<Vehicle>,
    onAdd: () -> Unit,
    onQuickAdd: () -> Unit,
    onEdit: (Vehicle) -> Unit,
) {
    val ctx = LocalContext.current
    val update by vm.update.collectAsStateWithLifecycle()

    var importOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var stationMenu by remember { mutableStateOf(false) }

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var statusTab by rememberSaveable { mutableStateOf(0) } // 0 todos, 1 processo, 2 túnel, 3 liberados
    var stationFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var modelFilter by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.checkOnStart() }

    val statusOfTab: Status? = when (statusTab) {
        1 -> Status.PROCESSO; 2 -> Status.TUNEL; 3 -> Status.LIBERADO; else -> null
    }
    val filtered = remember(vehicles, query, statusTab, stationFilter, modelFilter) {
        val q = normalize(query)
        vehicles.filter { v ->
            (statusOfTab == null || v.status == statusOfTab) &&
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
    val filtersActive = statusTab != 0 || stationFilter != null || modelFilter != null || query.isNotBlank()

    Scaffold(
        topBar = {
            Column {
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
                                    focusedIndicatorColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Column {
                                Text("Massivas Fai", fontWeight = FontWeight.Bold)
                                val pend = vehicles.count { it.status != Status.LIBERADO }
                                Text("$pend pendentes · ${vehicles.size} no total", fontSize = 12.sp)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (searching) query = ""
                            searching = !searching
                        }) { Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Buscar") }
                        if (!searching) {
                            IconButton(onClick = { shareOpen = true }) { Icon(Icons.Filled.Share, "Enviar lista") }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Vários VINs (digitar ou falar)") }, onClick = {
                                    menuOpen = false; onQuickAdd()
                                })
                                DropdownMenuItem(text = { Text("Colar lista do WhatsApp") }, onClick = {
                                    menuOpen = false; importOpen = true
                                })
                                DropdownMenuItem(text = { Text("Enviar lista") }, onClick = {
                                    menuOpen = false; shareOpen = true
                                })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Remover liberados") }, onClick = {
                                    menuOpen = false; confirmClear = "released"
                                })
                                DropdownMenuItem(text = { Text("Apagar tudo") }, onClick = {
                                    menuOpen = false; confirmClear = "all"
                                })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Verificar atualizações") }, onClick = {
                                    menuOpen = false; vm.checkForUpdate()
                                })
                                DropdownMenuItem(text = { Text("Sobre") }, onClick = {
                                    menuOpen = false; aboutOpen = true
                                })
                            }
                        }
                    },
                )
                // Abas de status com contagem
                TabRow(
                    selectedTabIndex = statusTab,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    val tabs = listOf(
                        "Todos" to vehicles.size,
                        "Processo" to vehicles.count { it.status == Status.PROCESSO },
                        "Túnel" to vehicles.count { it.status == Status.TUNEL },
                        "Liberados" to vehicles.count { it.status == Status.LIBERADO },
                    )
                    tabs.forEachIndexed { i, (label, count) ->
                        Tab(
                            selected = statusTab == i,
                            onClick = { statusTab = i },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$count", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(label, fontSize = 11.sp, maxLines = 1)
                                }
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onQuickAdd,
                    icon = { Text("🎤", fontSize = 18.sp) },
                    text = { Text("Vários VINs") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Adicionar VIN", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 170.dp),
        ) {
            // Filtro por modelo + estação
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        val st = stationFilter?.let { n -> Station.entries.first { it.name == n } }
                        FilterChip(
                            selected = st != null,
                            onClick = { stationMenu = true },
                            label = { Text(st?.label ?: "Estação") },
                            leadingIcon = if (st != null) { { ColorDotRaw(Color.White, 10) } } else null,
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = st?.color ?: MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                selectedTrailingIconColor = Color.White,
                            ),
                        )
                        DropdownMenu(expanded = stationMenu, onDismissRequest = { stationMenu = false }) {
                            DropdownMenuItem(text = { Text("Todas as estações") }, onClick = {
                                stationFilter = null; stationMenu = false
                            })
                            Station.entries.forEach { s ->
                                val count = vehicles.count { v -> v.status != Status.LIBERADO && v.pendingRepairs.any { it.station == s } }
                                DropdownMenuItem(
                                    leadingIcon = { ColorDotRaw(s.color, 14) },
                                    text = { Text(if (count > 0) "${s.label}  ($count)" else s.label) },
                                    onClick = { stationFilter = s.name; stationMenu = false },
                                )
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    FilterChip(
                        selected = modelFilter == null,
                        onClick = { modelFilter = null },
                        label = { Text("Todos modelos") },
                        colors = selectedPrimary(),
                    )
                    CarModel.entries.forEach { m ->
                        val count = vehicles.count { it.model == m }
                        if (count > 0) {
                            FilterChip(
                                selected = modelFilter == m.name,
                                onClick = { modelFilter = if (modelFilter == m.name) null else m.name },
                                label = { Text("${m.label}  $count") },
                                colors = selectedPrimary(),
                            )
                        }
                    }
                }
            }

            if (groups.isEmpty()) {
                item {
                    EmptyState(
                        hasVehicles = vehicles.isNotEmpty(),
                        onAdd = onAdd,
                        onQuickAdd = onQuickAdd,
                        onImport = { importOpen = true },
                        onClearFilters = {
                            statusTab = 0; stationFilter = null; modelFilter = null; query = ""; searching = false
                        },
                    )
                }
            }

            groups.forEach { (model, list) ->
                stickyHeader(key = "h_${model.name}") {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(model.label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (model.code.isNotEmpty()) Text("  ${model.code}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Pill(text = "${list.size}", color = MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                items(list, key = { it.id }) { v ->
                    VehicleCard(
                        v = v,
                        onClick = { onEdit(v) },
                        onStatus = { vm.setStatus(v.id, it) },
                        onToggleRepair = { vm.toggleRepair(v.id, it) },
                        onDelete = { vm.delete(v.id) },
                    )
                }
            }
        }
    }

    if (importOpen) ImportDialog(vm = vm, onDismiss = { importOpen = false })

    if (shareOpen) {
        ShareDialog(
            vm = vm,
            all = vehicles,
            filtered = if (filtersActive) filtered else null,
            onDismiss = { shareOpen = false },
        )
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
                        "• Toque no VIN para editar.\n" +
                        "• Use os botões do cartão para mandar ao Túnel ou Liberar.\n" +
                        "• Toque numa estação do cartão para marcar o reparo como feito.\n" +
                        "• Segure o cartão para mais opções."
                )
            },
            confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("OK") } },
        )
    }

    UpdateDialogs(update, vm)

    LaunchedEffect(update) {
        if (update is UpdateState.UpToDate) {
            Toast.makeText(ctx, "Você já está na versão mais recente", Toast.LENGTH_SHORT).show()
            vm.dismissUpdate()
        }
    }
}

@Composable
private fun selectedPrimary() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun EmptyState(
    hasVehicles: Boolean,
    onAdd: () -> Unit,
    onQuickAdd: () -> Unit,
    onImport: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hasVehicles) {
            Text("Nenhum VIN com esses filtros", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onClearFilters) { Text("Limpar filtros") }
        } else {
            Text("Nenhum VIN ainda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Adicione um VIN ou cole a lista que vocês mandam no WhatsApp — o app separa por modelo sozinho.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Adicionar VIN") }
            OutlinedButton(onClick = onQuickAdd, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("🎤  Vários VINs (digitar ou falar)") }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Colar lista do WhatsApp") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VehicleCard(
    v: Vehicle,
    onClick: () -> Unit,
    onStatus: (Status) -> Unit,
    onToggleRepair: (Station) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val released = v.status == Status.LIBERADO
    val overdue = !released && v.forecast != null && v.forecast < System.currentTimeMillis()
    val green = Status.LIBERADO.color

    Box(Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (released) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (released) 0.dp else 2.dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                // faixa lateral: probabilidade de liberação (ou verde se liberado)
                Box(
                    Modifier.width(6.dp).fillMaxHeight()
                        .background(if (released) green else v.difficulty?.color ?: Color.Transparent)
                )
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp).weight(1f)) {
                    // Linha 1: VIN + cor + status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            v.vin,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            textDecoration = if (released) TextDecoration.LineThrough else null,
                        )
                        v.color?.let {
                            Spacer(Modifier.width(8.dp))
                            ColorDot(it, 14)
                        }
                        Spacer(Modifier.weight(1f))
                        when (v.status) {
                            Status.TUNEL -> Pill(text = "TÚNEL", color = Status.TUNEL.color)
                            Status.LIBERADO -> Pill(text = "LIBERADO ✓", color = green)
                            Status.PROCESSO -> v.difficulty?.let { Pill(text = it.label, color = it.color, textColor = it.onColor) }
                        }
                    }

                    // Reparos: toque para marcar como feito
                    if (v.repairs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        v.repairs.forEach { r ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleRepair(r.station) }
                                    .heightIn(min = 32.dp)
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Pill(
                                    text = (if (r.done) "✓ " else "") + r.station.label,
                                    color = r.station.color,
                                    strike = r.done,
                                )
                                if (r.note.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        r.note,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (r.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (r.done) TextDecoration.LineThrough else null,
                                    )
                                }
                            }
                        }
                    }
                    if (v.notes.isNotBlank()) {
                        Text(
                            v.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (!released && v.forecast != null) {
                        Text(
                            (if (overdue) "⚠ Previsão vencida: " else "⏱ Previsão: ") + TextFormat.formatDate(v.forecast),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (overdue) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    // Ações rápidas
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (v.status) {
                            Status.PROCESSO -> {
                                QuickButton("→ Túnel", Status.TUNEL.color, filled = false) { onStatus(Status.TUNEL) }
                                QuickButton("✓ Liberar", green, filled = true) { onStatus(Status.LIBERADO) }
                            }
                            Status.TUNEL -> {
                                QuickButton("← Processo", Status.PROCESSO.color, filled = false) { onStatus(Status.PROCESSO) }
                                QuickButton("✓ Liberar", green, filled = true) { onStatus(Status.LIBERADO) }
                            }
                            Status.LIBERADO -> {
                                QuickButton("Desfazer liberação", Status.PROCESSO.color, filled = false) { onStatus(Status.PROCESSO) }
                            }
                        }
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onClick() })
            Status.entries.filter { it != v.status }.forEach { s ->
                DropdownMenuItem(text = { Text("Marcar: ${s.label}") }, onClick = { menu = false; onStatus(s) })
            }
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

@Composable
private fun QuickButton(text: String, color: Color, filled: Boolean, onClick: () -> Unit) {
    val pad = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    if (filled) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            contentPadding = pad,
            modifier = Modifier.height(36.dp),
        ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            contentPadding = pad,
            modifier = Modifier.height(36.dp),
        ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ---------------- Enviar lista (WhatsApp) ----------------

@Composable
private fun ShareDialog(vm: MainViewModel, all: List<Vehicle>, filtered: List<Vehicle>?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(vm.shareTitle) }
    var includeReleased by remember { mutableStateOf(vm.shareIncludeReleased) }
    var includeDate by remember { mutableStateOf(vm.shareIncludeDate) }
    var onlyFiltered by remember { mutableStateOf(false) }
    val source = if (onlyFiltered && filtered != null) filtered else all
    val text = remember(title, includeReleased, includeDate, source) {
        TextFormat.export(source, title, includeReleased, includeDate)
    }
    val count = source.count { includeReleased || it.status != Status.LIBERADO }

    fun saveOptions() {
        vm.shareTitle = title
        vm.shareIncludeReleased = includeReleased
        vm.shareIncludeDate = includeDate
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enviar lista") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título da lista") },
                    placeholder = { Text("Ex.: MASSIVA FAI - Turno B") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CheckRow("Incluir liberados", includeReleased) { includeReleased = it }
                CheckRow("Incluir data e hora", includeDate) { includeDate = it }
                if (filtered != null) {
                    CheckRow("Só os VINs filtrados na tela (${filtered.size})", onlyFiltered) { onlyFiltered = it }
                }
                Spacer(Modifier.height(6.dp))
                Text("Prévia · $count VINs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        text.lines().take(12).joinToString("\n") + if (text.lines().size > 12) "\n…" else "",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                saveOptions()
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                ctx.startActivity(Intent.createChooser(intent, "Enviar lista"))
                onDismiss()
            }) { Text("Enviar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    saveOptions()
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Massivas", text))
                    Toast.makeText(ctx, "Lista copiada", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text("Copiar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------- Importar (colar do WhatsApp) ----------------

@Composable
private fun ImportDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { TextFormat.parse(text) }
    val withoutModel = parsed.filter { it.model == null && vm.findByVin(it.vin) == null }
    var fallback by remember { mutableStateOf(vm.lastModel) }

    LaunchedEffect(Unit) {
        // Já preenche com o que estiver copiado.
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        if (text.isEmpty() && TextFormat.parse(clip).isNotEmpty()) text = clip
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colar lista do WhatsApp") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    placeholder = { Text("1 - VB001778 - CS55 - PINTURA(cab.04)\n2 - VB000433 - C928 - TÚNEL") },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                if (parsed.isEmpty()) {
                    Text(
                        if (text.isBlank()) "Cole a lista acima. O modelo pode vir na mesma linha do VIN ou como título do grupo."
                        else "Nenhum VIN reconhecido.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    val newCount = parsed.count { vm.findByVin(it.vin) == null }
                    Text(
                        "${parsed.size} VINs · $newCount novos · ${parsed.size - newCount} atualizados",
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    parsed.forEach { p ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.vin, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                (p.model ?: vm.findByVin(p.vin)?.model)?.label ?: "sem modelo",
                                fontSize = 12.sp,
                                color = if (p.model == null && vm.findByVin(p.vin) == null) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            when (p.status) {
                                Status.TUNEL -> Pill(text = "Túnel", color = Status.TUNEL.color)
                                Status.LIBERADO -> Pill(text = "Liberado", color = Status.LIBERADO.color)
                                else -> p.repairs.firstOrNull()?.let { Pill(text = it.station.label, color = it.station.color) }
                            }
                        }
                    }
                    if (withoutModel.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${withoutModel.size} VINs sem modelo. Escolha um (opcional) ou eles vão para \"Sem modelo\":",
                            color = Color(0xFFE65100),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CarModel.selectable.forEach { m ->
                                FilterChip(selected = fallback == m, onClick = { fallback = m }, label = { Text(m.label) }, colors = selectedPrimary())
                            }
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
