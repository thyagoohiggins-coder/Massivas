package com.massivas.fai.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.massivas.fai.BuildConfig
import com.massivas.fai.MainViewModel
import com.massivas.fai.UpdateState
import com.massivas.fai.Updater

@Composable
fun UpdateDialogs(state: UpdateState, vm: MainViewModel) {
    val ctx = LocalContext.current
    when (state) {
        is UpdateState.Checking -> AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Verificando atualizações…") },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Nova versão disponível") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("Atual: ${BuildConfig.VERSION_NAME}\nNova: ${state.release.versionName}")
                    if (state.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.release.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Seus VINs continuam salvos — a nova versão instala por cima.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.download(state.release) }) { Text("Atualizar") } },
            dismissButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text("Depois") } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Baixando ${state.release.versionName}…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.progress * 100).toInt()}%")
                }
            },
            confirmButton = {},
        )

        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Pronto para instalar") },
            text = {
                Text(
                    "Toque em Instalar e confirme na tela do Android.\n\n" +
                        "Se for a primeira vez, o Android vai pedir para permitir que o Massivas Fai " +
                        "instale apps: permita, volte e toque em Instalar de novo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Updater.canInstall(ctx)) Updater.install(ctx, state.file)
                    else Updater.openInstallPermissionSettings(ctx)
                }) { Text("Instalar") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text("Depois") } },
        )

        is UpdateState.Error -> AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Atualização") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text("OK") } },
        )

        else -> Unit
    }
}
