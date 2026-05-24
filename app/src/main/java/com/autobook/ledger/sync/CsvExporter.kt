package com.autobook.ledger.sync

import android.content.Context
import com.autobook.ledger.domain.ParsedBill
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvExporter(
    private val context: Context,
) {
    fun export(entries: List<ParsedBill>): File {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, "auto-ledger-${System.currentTimeMillis()}.csv")
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("时间,类型,状态,金额,分类,商户,账户,来源,备注,原文")
            entries.sortedByDescending { it.occurredAt }.forEach { entry ->
                writer.appendLine(
                    listOf(
                        formatter.format(Date(entry.occurredAt)),
                        entry.type.name,
                        entry.status.name,
                        "%.2f".format(Locale.US, entry.amountCents / 100.0),
                        entry.categoryPath,
                        entry.merchant,
                        entry.account,
                        entry.sourceAppName,
                        entry.note,
                        entry.rawText,
                    ).joinToString(",") { it.csvEscape() }
                )
            }
        }
        return file
    }

    private fun String.csvEscape(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }
}

