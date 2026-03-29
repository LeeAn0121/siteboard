package com.jongwook.siteboard

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataExportManager {
    fun exportPostsToCsv(context: Context): File {
        val postList = AppDatabase.getDatabase(context).postDao().getAllPostsOnce()
        val csvData = StringBuilder()
        csvData.append("ID,현장명,작업내용,위치(GPS),상세위치,메모,날짜,원본파일명\n")
        for (post in postList) {
            val title = post.title.replace(",", " ")
            val desc = post.description.replace(",", " ").replace("\n", " ")
            val loc = post.location?.replace(",", " ") ?: ""
            val detailLoc = post.detailLocation?.replace(",", " ") ?: ""
            val memo = post.memo?.replace(",", " ") ?: ""
            val origName = post.originalFileName ?: ""
            csvData.append("${post.id},$title,$desc,$loc,$detailLoc,$memo,${post.date},$origName\n")
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val siteboardDir = File(baseDir, "SITEBOARD")
        if (!siteboardDir.exists()) siteboardDir.mkdirs()
        val file = File(siteboardDir, "Siteboard_Data_$timeStamp.csv")
        FileWriter(file).use { it.write(csvData.toString()) }
        return file
    }
}
