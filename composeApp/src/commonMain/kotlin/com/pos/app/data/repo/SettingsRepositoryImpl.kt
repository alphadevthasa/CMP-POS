package com.pos.app.data.repo

import com.pos.app.db.PosDatabase
import com.pos.app.domain.model.StoreSettings
import com.pos.app.domain.repository.SettingsRepository

class SettingsRepositoryImpl(private val db: PosDatabase) : SettingsRepository {

    override fun load(): StoreSettings {
        fun get(key: String, fallback: String): String =
            db.posQueries.getSetting(key).executeAsOneOrNull() ?: fallback

        return StoreSettings(
            storeName = get("store_name", "POS Multiplatform"),
            address = get("address", ""),
            phone = get("phone", ""),
            taxPercent = get("tax_percent", "0").toDoubleOrNull() ?: 0.0,
            footerText = get("footer", "Terima kasih atas kunjungan Anda"),
            printerAddress = get("printer_address", ""),
            printerWidthChars = get("printer_width", "32").toIntOrNull() ?: 32,
        )
    }

    override fun save(settings: StoreSettings) {
        db.posQueries.putSetting("store_name", settings.storeName)
        db.posQueries.putSetting("address", settings.address)
        db.posQueries.putSetting("phone", settings.phone)
        db.posQueries.putSetting("tax_percent", settings.taxPercent.toString())
        db.posQueries.putSetting("footer", settings.footerText)
        db.posQueries.putSetting("printer_address", settings.printerAddress)
        db.posQueries.putSetting("printer_width", settings.printerWidthChars.toString())
    }
}
