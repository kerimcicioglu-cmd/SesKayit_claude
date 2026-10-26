package com.kerim.seskayit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object Source {
    const val MANUAL = "MANUAL"
    const val PHONE = "PHONE"
    const val WHATSAPP = "WHATSAPP"
}

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val createdAt: Long,
    val durationMs: Long,
    val transcript: String? = null,
    val summary: String? = null,
    val isWhatsApp: Boolean = false,
    /** LOCAL = yalnızca cihazda; ileride bulut senkronizasyonu için SYNCED / PENDING */
    val syncStatus: String = "LOCAL",
    val source: String = Source.MANUAL,
    val phoneNumber: String? = null,
    val contactName: String? = null,
)

/** Otomatik kayıt listesindeki kişi / numara */
@Entity(tableName = "auto_contacts")
data class AutoContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val number: String,
)
