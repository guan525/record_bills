package com.autobook.ledger.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.cny(): String = "¥%.2f".format(Locale.CHINA, this / 100.0)

fun Long.formatDateTime(): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(this))

fun String.topCategory(): String = substringBefore("/")

