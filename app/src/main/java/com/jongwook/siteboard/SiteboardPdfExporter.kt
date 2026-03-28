package com.jongwook.siteboard

import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SiteboardPdfExporter {
    fun exportProjectPdf(context: Context, siteTitle: String, posts: List<PostEntity>): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 50f

        val titlePaint = Paint().apply {
            textSize = 24f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val textPaint = Paint().apply {
            textSize = 14f
            color = Color.DKGRAY
        }

        for ((index, post) in posts.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText("SITEBOARD 현장 보고서  |  $siteTitle", margin, margin, textPaint)
            canvas.drawLine(margin, margin + 10f, pageWidth - margin, margin + 10f, textPaint)
            canvas.drawText(post.title, margin, margin + 50f, titlePaint)

            var y = margin + 90f
            canvas.drawText("• 위치 : ${post.location ?: "미입력"}", margin, y, textPaint)
            y += 25f
            canvas.drawText("• 일시 : ${post.date}", margin, y, textPaint)
            y += 25f
            canvas.drawText("• 작업 내용 : ${post.description.replace("\n", " ")}", margin, y, textPaint)
            y += 25f
            if (!post.detailLocation.isNullOrBlank()) {
                canvas.drawText("• 상세 위치 : ${post.detailLocation}", margin, y, textPaint)
                y += 25f
            }
            if (!post.memo.isNullOrBlank()) {
                canvas.drawText("• 메모 : ${post.memo.replace("\n", " ")}", margin, y, textPaint)
                y += 25f
            }
            y += 8f

            try {
                val uri = android.net.Uri.parse(post.imageUri)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, _, _ ->
                        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val availableWidth = pageWidth - margin * 2
                val availableHeight = pageHeight - margin - y
                val scale = minOf(
                    availableWidth / bitmap.width.toFloat(),
                    availableHeight / bitmap.height.toFloat()
                )
                if (scale <= 0f) {
                    canvas.drawText("[이미지 배치 공간 부족]", margin, y, textPaint)
                    pdfDocument.finishPage(page)
                    continue
                }
                val targetW = bitmap.width * scale
                val targetH = bitmap.height * scale
                val imageLeft = margin + ((availableWidth - targetW) / 2f)
                canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(imageLeft, y, imageLeft + targetW, y + targetH),
                    Paint().apply {
                        isAntiAlias = true
                        isFilterBitmap = true
                    }
                )
            } catch (_: Exception) {
                canvas.drawText("[이미지 로드 실패]", margin, y, textPaint)
            }

            pdfDocument.finishPage(page)
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SITEBOARD")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "Siteboard_${siteTitle}_$stamp.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }
}

