package com.pos.app.print

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android ESC/POS printer over classic Bluetooth (RFCOMM/SPP).
 * Fallback: render the document to a PDF and open the system share/print sheet.
 */
class AndroidReceiptPrinter(private val context: Context) : ReceiptPrinter {

    override suspend fun printEscPos(address: String, bytes: ByteArray, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(address.isNotBlank()) { "Alamat printer belum diatur di Pengaturan" }
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: error("Bluetooth tidak tersedia di perangkat ini")
            @Suppress("MissingPermission")
            val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission")
            val socket: BluetoothSocket =
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            @Suppress("MissingPermission")
            adapter.cancelDiscovery()
            socket.use { s ->
                s.connect()
                s.outputStream.use { out ->
                    out.write(bytes)
                    out.flush()
                }
            }
        }
    }

    override suspend fun sharePdf(doc: PdfDoc): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "${doc.fileName}.pdf")
            writePdf(doc.text, file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, "Cetak / Bagikan struk")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun writePdf(text: String, file: File) {
        val pdf = PdfDocument()
        val paint = Paint().apply {
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        val lines = text.split("\n")
        val linesPerPage = 60
        var index = 0
        var pageNumber = 1
        while (index < lines.size) {
            val info = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
            val page = pdf.startPage(info)
            var y = 40f
            var count = 0
            while (index < lines.size && count < linesPerPage) {
                page.canvas.drawText(lines[index], 32f, y, paint)
                y += 14f
                index++
                count++
            }
            pdf.finishPage(page)
            pageNumber++
        }
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()
    }

    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
