package com.pos.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pos.app.print.RLine
import com.pos.app.print.ReceiptFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ReceiptDialog(
    lines: List<RLine>,
    fileName: String,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val store = LocalStore.current
    val state by store.state.collectAsState()
    val printer = store.printer()
    val text = ReceiptFormatter.renderText(lines, state.settings.printerWidthChars)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pratinjau") },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(
                    text = text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    val result = printLines(printer, state.settings, lines)
                    snackbar.showSnackbar(
                        result.fold(
                            onSuccess = { "Terkirim ke printer" },
                            onFailure = { "Gagal cetak: ${it.message ?: "printer tidak tersedia"}" },
                        ),
                    )
                }
            }) { Text("Cetak") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        val result = shareLines(printer, state.settings, lines, fileName)
                        snackbar.showSnackbar(
                            result.fold(
                                onSuccess = { "Dibuka menu bagikan/PDF" },
                                onFailure = { "Gagal: ${it.message ?: "tidak didukung"}" },
                            ),
                        )
                    }
                }) { Text("PDF / Bagikan") }
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        },
    )
}
