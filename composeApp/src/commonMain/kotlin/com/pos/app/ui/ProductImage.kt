package com.pos.app.ui

import com.pos.app.res.Res
import com.pos.app.res.air_mineral
import com.pos.app.res.americano
import com.pos.app.res.ayam_geprek
import com.pos.app.res.cappuccino
import com.pos.app.res.donat_gula
import com.pos.app.res.es_jeruk
import com.pos.app.res.es_teh_manis
import com.pos.app.res.keripik_singkong
import com.pos.app.res.kopi_susu
import com.pos.app.res.mie_ayam
import com.pos.app.res.nasi_goreng
import com.pos.app.res.nasi_uduk
import com.pos.app.res.pisang_goreng
import com.pos.app.res.teh_botol
import org.jetbrains.compose.resources.DrawableResource

/** ponytail: name-keyed map for the seeded catalog; null falls back to initials. */
fun productImage(name: String): DrawableResource? = when (name) {
    "Nasi Goreng" -> Res.drawable.nasi_goreng
    "Mie Ayam" -> Res.drawable.mie_ayam
    "Ayam Geprek" -> Res.drawable.ayam_geprek
    "Nasi Uduk" -> Res.drawable.nasi_uduk
    "Es Teh Manis" -> Res.drawable.es_teh_manis
    "Es Jeruk" -> Res.drawable.es_jeruk
    "Air Mineral" -> Res.drawable.air_mineral
    "Teh Botol" -> Res.drawable.teh_botol
    "Kopi Susu" -> Res.drawable.kopi_susu
    "Americano" -> Res.drawable.americano
    "Cappuccino" -> Res.drawable.cappuccino
    "Keripik Singkong" -> Res.drawable.keripik_singkong
    "Pisang Goreng" -> Res.drawable.pisang_goreng
    "Donat Gula" -> Res.drawable.donat_gula
    else -> null
}
