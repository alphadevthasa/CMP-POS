package com.pos.app.print

interface ReceiptPrinter {
    /**
     * Print raw ESC/POS bytes to the thermal printer at [address] (device-specific).
     * [text] is the plain rendering, used by platforms that print via the OS (iOS AirPrint)
     * instead of raw ESC/POS.
     */
    suspend fun printEscPos(address: String, bytes: ByteArray, text: String = ""): Result<Unit>

    /** Fallback: hand the document to the OS as a PDF (share sheet / AirPrint). */
    suspend fun sharePdf(doc: PdfDoc): Result<Unit>
}
