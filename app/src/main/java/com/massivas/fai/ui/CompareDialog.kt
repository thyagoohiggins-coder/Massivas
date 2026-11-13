package com.massivas.fai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.massivas.fai.MainViewModel
import com.massivas.fai.Status
import com.massivas.fai.VinCompare

/**
 * Compara os VINs do campo com a lista do app ou com outra lista colada.
 * @param onKeepOnly deixa no campo só esses VINs (ex.: só os novos)
 */
@Composable
fun CompareDialog(
    vm: MainViewModel,
    myVins: List<String>,
    prefixes: List<String>,
    forcePrefix: Boolean,
    digitLen: Int,
    onKeepOnly: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val vehicles by vm.vehicles.collectAsState()
    var withApp by remember { mutableStateOf(true) }
    var ignoreReleased by remember { mutableStateOf(true) }
    var otherText by remember { mutableStateOf("") }
    var confirmRelease by remember { mutableStateOf(false) }

    val otherVins = remember(withApp, ignoreReleased, otherText, vehicles) {
        if (withApp) vehicles.filter { !ignoreReleased || it.status != Status.LIBERADO }.map { it.vin }
        else VinCompare.vinsFromText(otherText, prefixes, forcePrefix, digitLen)
    }
    val diff = remember(myVins, otherVins) { VinCompare.compare(myVins, otherVins) }
    val otherName = if (withApp) "no app" else "na outra lista"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comparar listas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Sua lista: ${myVins.distinct().size} VINs. Comparar com:", fontSize = 14.sp)
                ChoiceRow("Lista do app (${vehicles.size} VINs)", withApp) { withApp = true }
                if (withApp) {
                    Row(
                        Modifier.fillMaxWidth().clickable { ignoreReleased = !ignoreReleased }.padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = ignoreReleased, onCheckedChange = { ignoreReleased = it })
                        Text("Ignorar os já liberados", fontSize = 13.sp)
                    }
                }
                ChoiceRow("Outra lista (colar)", !withApp) { withApp = false }
                if (!withApp) {
                    OutlinedTextField(
                        value = otherText,
                        onValueChange = { otherText = it },
                        placeholder = { Text("Cole a lista do WhatsApp ou VINs separados por vírgula") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 160.dp),
                    )
                    Text("${otherVins.size} VINs lidos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(12.dp))
                DiffGroup(ctx, "Só na sua lista (novos)", diff.onlyMine, Color(0xFF2E7D32))
                DiffGroup(ctx, "Nas duas", diff.both, Color(0xFF0277BD))
                DiffGroup(ctx, "Só $otherName (faltam na sua)", diff.onlyOther, Color(0xFFE65100))

                if (diff.onlyMine.isNotEmpty() && diff.onlyMine.size < myVins.distinct().size) {
                    OutlinedButton(
                        onClick = { onKeepOnly(diff.onlyMine); onDismiss() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Deixar só os ${diff.onlyMine.size} novos para importar") }
                }
                if (withApp && diff.onlyOther.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { confirmRelease = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) { Text("Marcar os ${diff.onlyOther.size} que faltam como Liberado ✅") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("Liberar ${diff.onlyOther.size} VINs?") },
            text = { Text("Os VINs do app que não estão na sua lista serão marcados como Liberado ✅:\n\n" + diff.onlyOther.joinToString(", ")) },
            confirmButton = {
                TextButton(onClick = {
                    vm.markReleased(diff.onlyOther.toSet())
                    Toast.makeText(ctx, "${diff.onlyOther.size} marcados como liberados", Toast.LENGTH_SHORT).show()
                    confirmRelease = false
                }) { Text("Liberar") }
            },
            dismissButton = { TextButton(onClick = { confirmRelease = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun DiffGroup(ctx: Context, title: String, vins: List<String>, color: Color) {
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$title: ${vins.size}", fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1f))
                if (vins.isNotEmpty()) {
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("VINs", vins.joinToString(", ")))
                        Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
                    }) { Text("Copiar") }
                }
            }
            if (vins.isNotEmpty()) {
                Text(vins.joinToString(", "), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}
