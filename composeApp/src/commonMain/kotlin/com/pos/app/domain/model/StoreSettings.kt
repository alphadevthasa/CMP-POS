package com.pos.app.domain.model

data class StoreSettings(
    val storeName: String = "POS Multiplatform",
    val address: String = "",
    val phone: String = "",
    val taxPercent: Double = 0.0,
    val footerText: String = "Terima kasih atas kunjungan Anda",
    val printerAddress: String = "",
    val printerWidthChars: Int = 32,
)
