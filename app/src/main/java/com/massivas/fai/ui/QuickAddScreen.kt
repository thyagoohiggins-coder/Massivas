package com.massivas.fai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.massivas.fai.Bolsao
import com.massivas.fai.CarModel
import com.massivas.fai.MainViewModel
import com.massivas.fai.ParsedVehicle
import com.massivas.fai.QuickVin
import com.massivas.fai.Status
import com.massivas.fai.VoiceDictation

/** Adicionar vários VINs de uma vez: digitando separados por vírgula ou falando. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf<CarModel?>(null) }
    var bolsao by remember { mutableStateOf<Bolsao?>(null) }
    var compareOpen by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current

    var prefix by remember { mutableStateOf(vm.quickPrefix) }
    var prefix2 by remember { mutableStateOf(vm.quickPrefix2) }
    val prefixes = listOf(prefix, prefix2).filter { it.isNotBlank() }
    var forcePrefix by remember { mutableStateOf(vm.quickForcePrefix) }
    val digitLen = remember { vm.commonDigitLength() }
    val result = remember(text, prefix, prefix2, forcePrefix) { QuickVin.parse(text, prefixes, forcePrefix, digitLen) }
    val entries = result.entries
    fun modelOf(e: QuickVin.Entry) = e.model ?: vm.findByVin(e.vin)?.model ?: defaultModel
    val missingModel = entries.count { modelOf(it) == null }

    val dictation = remember {
        VoiceDictation(
            ctx,
            onPartial = { partial = it },
            onFinal = { said ->
                // Só "vírgula" separa os VINs: uma pausa no meio do código não quebra o VIN.
                heard = said
                val converted = QuickVin.convertSpeech(said)
                if (converted.isNotBlank()) {
                    text = QuickVin.tidy(text.trimEnd() + " " + converted, listOf(prefix, prefix2), forcePrefix, digitLen)
                }
            },
            onListening = { listening = it },
            onError = { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() },
        )
    }
    DisposableEffect(Unit) { onDispose { dictation.destroy() } }
    dictation.extraBias = prefixes

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) dictation.start() else Toast.makeText(ctx, "Sem permissão do microfone", Toast.LENGTH_SHORT).show()
    }

    fun toggleMic() {
        if (listening) { dictation.stop(); return }
        if (!dictation.available) {
            Toast.makeText(ctx, "Reconhecimento de voz não disponível neste aparelho (instale o app Google).", Toast.LENGTH_LONG).show()
            return
        }
        focus.clearFocus() // esconde o teclado para ver a conferência
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            dictation.start()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    BackHandler(onBack = onClose)

    if (compareOpen) {
        CompareDialog(
            vm = vm,
            myVins = entries.map { it.vin },
            prefixes = prefixes,
            forcePrefix = forcePrefix,
            digitLen = digitLen,
            onKeepOnly = { keep ->
                val set = keep.toSet()
                text = entries.filter { it.vin in set }
                    .joinToString(", ") { e -> if (e.model != null) "${e.vin} ${e.model.short}" else e.vin } + ", "
            },
            onDismiss = { compareOpen = false },
        )
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
                title = { Text("Vários VINs", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { compareOpen = true }) {
                        Text("Comparar", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    if (text.isNotEmpty()) TextButton(onClick = { text = "" }) {
                        Text("Limpar", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (missingModel > 0) {
                        Text(
                            "$missingModel VINs sem modelo: vão para o grupo \"Sem modelo\" (dá para escolher depois).",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MicButton(listening = listening, onClick = { toggleMic() })
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (listening) dictation.stop()
                                val parsed = entries.map { e ->
                                    ParsedVehicle(
                                        e.vin, e.model ?: if (vm.findByVin(e.vin) == null) defaultModel else null,
                                        if (bolsao != null) Status.BOLSAO else null, emptyList(), "", color = e.color, bolsao = bolsao,
                                    )
                                }
                                val r = vm.importParsed(parsed, defaultModel)
                                Toast.makeText(ctx, "${r.added} adicionados, ${r.updated} atualizados", Toast.LENGTH_LONG).show()
                                onClose()
                            },
                            enabled = entries.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Text(
                                if (entries.isEmpty()) "Importar" else "Importar ${entries.size} VINs",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Digite ou fale os VINs separados por vírgula", fontWeight = FontWeight.Bold)
                    Text(
                        "O modelo e a cor (azul, cinza, preto, branco) depois do VIN são opcionais.\n" +
                            "Digitando: VB001778 CS55, VB000433 C928, VB004163\n" +
                            "Falando: \"VB zero zero um sete sete oito CS cinquenta e cinco azul, VB zero zero zero quatro três três…\"\n" +
                            "Quando o VIN completa $digitLen dígitos ou você fala outro VB, o app já passa para o próximo. " +
                            "Pode pausar no meio do código; \"vírgula\" também separa.",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("VINs") },
                placeholder = { Text("VB001778 CS55, VB000433 C928, …") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            if (listening || partial.isNotEmpty()) {
                Text(
                    if (partial.isEmpty()) "🎤 Ouvindo… fale os VINs em sequência" else "🎤 $partial",
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (heard.isNotEmpty()) {
                Text(
                    "Último trecho ouvido: \"$heard\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Ajustes que deixam a voz bem mais precisa
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Prefixos dos VINs", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { v ->
                                prefix = v.uppercase().filter { it.isLetter() }.take(4)
                                vm.quickPrefix = prefix
                            },
                            label = { Text("Prefixo 1") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = prefix2,
                            onValueChange = { v ->
                                prefix2 = v.uppercase().filter { it.isLetter() }.take(4)
                                vm.quickPrefix2 = prefix2
                            },
                            label = { Text("Prefixo 2 (opcional)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "O que for ouvido vira o prefixo mais parecido. Falando só os números, repete o último prefixo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OptionRow(
                        "Sempre usar ${if (prefixes.size > 1) "um destes prefixos" else "o prefixo $prefix"} (corrige \"BB\", \"VV\", \"bebê\"…)",
                        forcePrefix,
                    ) { forcePrefix = it; vm.quickForcePrefix = it }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Modelo para VINs sem modelo (opcional)")
                Grid(CarModel.selectable, columns = 2) { m, mod ->
                    SelectTile(
                        selected = defaultModel == m,
                        onClick = { defaultModel = if (defaultModel == m) null else m },
                        modifier = mod,
                    ) { c ->
                        Column {
                            Text(m.label, fontWeight = FontWeight.Bold, color = c)
                            Text(m.code, fontSize = 11.sp, color = c.copy(alpha = 0.75f))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Bolsão para todos (opcional)")
                Grid(Bolsao.entries.toList(), columns = 2) { b, mod ->
                    SelectTile(
                        selected = bolsao == b,
                        onClick = { bolsao = if (bolsao == b) null else b },
                        modifier = mod,
                        selectedColor = Bolsao.color,
                        selectedContentColor = Color.White,
                        accent = Bolsao.color,
                    ) { c -> Text(b.label, fontWeight = FontWeight.SemiBold, color = c) }
                }
            }

            if (entries.isNotEmpty() || result.unknown.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Conferir (${entries.size})")
                    entries.forEach { e ->
                        val existing = vm.findByVin(e.vin)
                        val m = modelOf(e)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.vin, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            if (m != null) Pill(text = m.label, color = MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary)
                            else Pill(text = "sem modelo", color = Color(0xFF78909C))
                            e.color?.let {
                                Spacer(Modifier.width(6.dp))
                                ColorDot(it, 14)
                                Text(" ${it.label}", fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                buildString {
                                    append(if (existing != null) "já existe" else "novo")
                                    if (e.prefixAdded) append(" · prefixo repetido")
                                    if (e.prefixFixed) append(" · letras corrigidas")
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    result.unknown.forEach {
                        Text("Não entendi: \"$it\"", color = Color(0xFFE53935), fontSize = 13.sp)
                    }
                    result.unknownModels.forEach {
                        Text("Modelo não reconhecido em $it", color = Color(0xFFE65100), fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "micScale",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (listening) Color(0xFFE53935) else MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(56.dp).scale(if (listening) scale else 1f),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (listening) "■" else "🎤", fontSize = if (listening) 22.sp else 24.sp, color = Color.White)
        }
    }
}
