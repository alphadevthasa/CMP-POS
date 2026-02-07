package com.pos.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val store = LocalStore.current
    val state by store.state.collectAsState()
    val s = state.settings

    var storeName by remember(s.storeName) { mutableStateOf(s.storeName) }
    var address by remember(s.address) { mutableStateOf(s.address) }
    var phone by remember(s.phone) { mutableStateOf(s.phone) }
    var taxPercent by remember(s.taxPercent) { mutableStateOf(s.taxPercent.toString()) }
    var footer by remember(s.footerText) { mutableStateOf(s.footerText) }
    var printerAddress by remember(s.printerAddress) { mutableStateOf(s.printerAddress) }
    var printerWidth by remember(s.printerWidthChars) { mutableStateOf(s.printerWidthChars.toString()) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pengaturan Toko", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        OutlinedTextField(storeName, { storeName = it; saved = false }, label = { Text("Nama toko") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it; saved = false }, label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it; saved = false }, label = { Text("Telepon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            taxPercent,
            { taxPercent = it.filter { c -> c.isDigit() || c == '.' }; saved = false },
            label = { Text("Pajak (%)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(footer, { footer = it; saved = false }, label = { Text("Footer struk") }, modifier = Modifier.fillMaxWidth())

        Text("Printer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            printerAddress,
            { printerAddress = it; saved = false },
            label = { Text("Alamat / MAC printer Bluetooth") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            printerWidth,
            { printerWidth = it.filter { c -> c.isDigit() }; saved = false },
            label = { Text("Lebar kertas (karakter, 32 = 58mm / 48 = 80mm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                store.saveSettings(
                    s.copy(
                        storeName = storeName,
                        address = address,
                        phone = phone,
                        taxPercent = taxPercent.toDoubleOrNull() ?: 0.0,
                        footerText = footer,
                        printerAddress = printerAddress,
                        printerWidthChars = printerWidth.toIntOrNull() ?: 32,
                    ),
                )
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (saved) "Tersimpan" else "Simpan") }
    }
}
