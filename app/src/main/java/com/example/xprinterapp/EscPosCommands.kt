package com.example.xprinterapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Tiện ích tạo lệnh ESC/POS cho máy in nhiệt (tương thích Xprinter XP-428A).
 *
 * Ghi chú quan trọng:
 * - Chế độ TEXT (gửi text thô + lệnh) rất nhanh, nhưng máy in cần đúng
 *   bảng mã (code page) để hiển thị tiếng Việt có dấu; đa số máy Xprinter
 *   giá rẻ KHÔNG có bảng mã tiếng Việt đầy đủ nên dấu có thể bị lỗi/mất.
 * - Chế độ IMAGE (khuyên dùng): chuyển văn bản thành ảnh bitmap rồi in
 *   dưới dạng ảnh (GS v 0), đảm bảo hiển thị đúng 100% dấu tiếng Việt,
 *   không phụ thuộc bảng mã của máy in. Chỉ đánh đổi là in chậm hơn text.
 */
object EscPosCommands {

    private const val ESC = 0x1B
    private const val GS = 0x1D

    /** Reset máy in về trạng thái mặc định */
    fun initPrinter(): ByteArray = byteArrayOf(ESC.toByte(), '@'.code.toByte())

    /** Xuống dòng đơn giản */
    fun lineFeed(lines: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        repeat(lines) { out.write(byteArrayOf(0x0A)) }
        return out.toByteArray()
    }

    /** Cắt giấy (partial cut) - hầu hết Xprinter hỗ trợ GS V 1 hoặc GS V 66 */
    fun cutPaper(): ByteArray = byteArrayOf(GS.toByte(), 'V'.code.toByte(), 1)

    /** In văn bản thô ở chế độ text (nhanh, có thể lỗi dấu tiếng Việt) */
    fun textToBytes(text: String, charset: String = "UTF-8"): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(initPrinter())
        out.write(text.toByteArray(charset(charset)))
        out.write(lineFeed(1))
        return out.toByteArray()
    }

    /**
     * Chuyển văn bản thành lệnh in ảnh raster (GS v 0), đảm bảo hiển thị
     * đúng dấu tiếng Việt vì được vẽ bằng font hệ thống rồi in dưới dạng
     * ảnh đen trắng (dithering ngưỡng đơn giản).
     *
     * @param text nội dung cần in
     * @param printWidthDots chiều rộng giấy tính theo dot.
     *        Máy in 58mm thường dùng 384 dots, máy in 80mm thường dùng 576 dots.
     * @param textSizePx cỡ chữ (px) khi vẽ lên bitmap
     */
    fun textToImageBytes(
        text: String,
        printWidthDots: Int = 384,
        textSizePx: Float = 32f
    ): ByteArray {
        val bitmap = renderTextToBitmap(text, printWidthDots, textSizePx)
        val raster = bitmapToEscPosRasterBands(bitmap)
        val out = ByteArrayOutputStream()
        out.write(initPrinter())
        out.write(raster)
        out.write(lineFeed(1))
        return out.toByteArray()
    }

    /** DPI mặc định của đa số máy in nhiệt Xprinter dòng nhỏ (8 dot/mm) */
    const val DEFAULT_DPI = 203

    /** Đổi milimet sang số dot theo DPI của máy in (1 inch = 25.4mm) */
    fun mmToDots(mm: Float, dpi: Int = DEFAULT_DPI): Int =
        Math.round(mm * dpi / 25.4f)

    /** Giới hạn an toàn để tránh OutOfMemory khi người dùng nhập DPI/kích thước quá lớn */
    private const val MAX_LABEL_PIXELS = 20_000_000 // ~20 triệu điểm ảnh (đủ cho khổ A5 ở 300 DPI)

    /**
     * Sinh lệnh in NHÃN (label) với kích thước cố định theo mm, ví dụ
     * nhãn vận chuyển 100mm x 150mm. Khác với [textToImageBytes] (chiều
     * cao tự co giãn theo nội dung), hàm này tạo một bitmap có kích thước
     * CỐ ĐỊNH đúng bằng khổ nhãn thật, đảm bảo nhãn in ra đúng tỉ lệ và
     * không bị lệch/thiếu khi máy in dùng cảm biến khoảng cách giữa các nhãn.
     *
     * @param text nội dung cần in trên nhãn
     * @param labelWidthMm chiều rộng nhãn (mm), mặc định 100mm
     * @param labelHeightMm chiều cao nhãn (mm), mặc định 150mm
     * @param dpi độ phân giải máy in (dot/inch). Xprinter phổ biến dùng 203 DPI.
     *        Nếu nhãn in ra bị sai tỉ lệ, thử đổi sang 180 hoặc 300 tuỳ máy.
     * @param textSizePx cỡ chữ (px) khi vẽ nội dung lên nhãn
     * @param cutAfter tự động cắt giấy sau khi in xong nhãn (nếu máy có dao cắt)
     * @throws IllegalArgumentException nếu kích thước/DPI không hợp lệ hoặc quá lớn
     */
    fun textToLabelBytes(
        text: String,
        labelWidthMm: Float = 100f,
        labelHeightMm: Float = 150f,
        dpi: Int = DEFAULT_DPI,
        textSizePx: Float = 40f,
        cutAfter: Boolean = true
    ): ByteArray {
        val widthDots = mmToDots(labelWidthMm, dpi)
        val heightDots = mmToDots(labelHeightMm, dpi)

        require(widthDots > 0 && heightDots > 0) {
            "Kích thước nhãn không hợp lệ"
        }
        require(widthDots.toLong() * heightDots.toLong() <= MAX_LABEL_PIXELS) {
            "Kích thước nhãn/DPI quá lớn (${widthDots}x${heightDots} dot), " +
                "có thể gây tràn bộ nhớ. Hãy giảm DPI hoặc kích thước nhãn."
        }

        val bitmap = renderLabelBitmap(text, widthDots, heightDots, textSizePx)
        val raster = bitmapToEscPosRasterBands(bitmap)
        bitmap.recycle()

        val out = ByteArrayOutputStream()
        out.write(initPrinter())
        out.write(raster)
        out.write(lineFeed(2))
        if (cutAfter) out.write(cutPaper())
        return out.toByteArray()
    }

    /**
     * Vẽ nội dung nhãn lên một bitmap có kích thước CỐ ĐỊNH (widthDots x
     * heightDots), có lề (padding) xung quanh, chữ căn từ trên xuống.
     */
    private fun renderLabelBitmap(
        text: String,
        widthDots: Int,
        heightDots: Int,
        textSizePx: Float
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthDots, heightDots, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = textSizePx
        }

        val padding = (widthDots * 0.06f).toInt()
        val contentWidth = (widthDots - padding * 2).coerceAtLeast(1)

        val staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.25f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(padding.toFloat(), padding.toFloat())
        staticLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun renderTextToBitmap(text: String, widthDots: Int, textSizePx: Float): Bitmap {
        val paint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = textSizePx
        }

        val staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthDots)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()

        val height = staticLayout.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthDots, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        staticLayout.draw(canvas)
        return bitmap
    }

    /**
     * Chuyển bitmap đen trắng thành lệnh GS v 0 (raster bit image) chuẩn ESC/POS,
     * TỰ ĐỘNG CHIA NHỎ theo từng dải ngang (band) thay vì gửi 1 lệnh khổng lồ.
     *
     * Lý do chia dải: nhiều máy in nhiệt giá rẻ (kể cả Xprinter) có bộ đệm
     * (buffer) nhận dữ liệu giới hạn. Gửi một ảnh lớn (ví dụ nhãn 100x150mm
     * ~ 1 triệu điểm ảnh) trong đúng 1 lệnh GS v 0 dễ khiến máy in bị lỗi,
     * treo, hoặc in thiếu. Chia thành nhiều dải nhỏ (mỗi dải cao tối đa
     * [bandHeightDots] dot) giúp in ổn định hơn nhiều trên phần cứng thật.
     *
     * Hiệu năng: dùng [Bitmap.getPixels] để đọc toàn bộ điểm ảnh của một
     * dải trong 1 lần gọi (native, rất nhanh) thay vì gọi getPixel() hàng
     * triệu lần riêng lẻ.
     */
    private fun bitmapToEscPosRasterBands(bitmap: Bitmap, bandHeightDots: Int = 256): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val threshold = 160 // ngưỡng độ sáng để coi là điểm đen (0-255)

        val out = ByteArrayOutputStream()
        var y = 0
        while (y < height) {
            val bandHeight = minOf(bandHeightDots, height - y)
            val pixels = IntArray(width * bandHeight)
            bitmap.getPixels(pixels, 0, width, 0, y, width, bandHeight)

            // GS v 0 m xL xH yL yH d1...dk (lệnh riêng cho từng dải)
            out.write(GS)
            out.write('v'.code)
            out.write(0x30) // '0'
            out.write(0) // m = 0 (chế độ bình thường)
            out.write(widthBytes and 0xFF)
            out.write((widthBytes shr 8) and 0xFF)
            out.write(bandHeight and 0xFF)
            out.write((bandHeight shr 8) and 0xFF)

            for (row in 0 until bandHeight) {
                var bitBuffer = 0
                var bitCount = 0
                val rowOffset = row * width
                for (x in 0 until width) {
                    val pixel = pixels[rowOffset + x]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val luminance = (r * 0.299 + g * 0.587 + b * 0.114).toInt()
                    val isBlack = luminance < threshold

                    bitBuffer = (bitBuffer shl 1) or (if (isBlack) 1 else 0)
                    bitCount++
                    if (bitCount == 8) {
                        out.write(bitBuffer)
                        bitBuffer = 0
                        bitCount = 0
                    }
                }
                if (bitCount > 0) {
                    bitBuffer = bitBuffer shl (8 - bitCount)
                    out.write(bitBuffer)
                }
            }
            y += bandHeight
        }
        return out.toByteArray()
    }

    /** Ghi mảng byte ra một OutputStream (kết nối Bluetooth) */
    fun writeTo(stream: OutputStream, data: ByteArray) {
        stream.write(data)
        stream.flush()
    }
}
