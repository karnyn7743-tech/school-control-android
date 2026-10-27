package com.schoolcontrol.core

/**
 * Portable CSV interchange for manual/converted grade rows.
 * XLSB itself is intentionally not parsed here.
 */
object StudentCsvCodec {
    data class StudentRow(
        val name: String,
        val feminine: Boolean,
        val stage: String, // F8, S10, S11SCI
        val subjects: List<List<String>>
    )

    private val stages = mapOf("F8" to 8, "S10" to 12, "S11SCI" to 9)
    private const val HEADER_PREFIX = "school_control_csv_v1"

    fun encode(rows: List<StudentRow>): String {
        val header = listOf(HEADER_PREFIX, "name", "gender", "stage") +
            (1..12).flatMap { i -> (1..4).map { c -> "s${i}_c$c" } }
        val lines = mutableListOf(csvLine(header))
        for (row in rows) {
            val count = stages[row.stage]
                ?: throw IllegalArgumentException("مرحلة غير مدعومة: ${row.stage}")
            require(row.subjects.size == count) { "عدد المواد لا يطابق المرحلة ${row.stage}" }
            require(row.subjects.all { it.size == 4 }) { "كل مادة تتطلب أربع خلايا" }
            val cells = mutableListOf(row.name, if (row.feminine) "F" else "M", row.stage)
            for (i in 0 until 12) {
                val subject = row.subjects.getOrNull(i)
                repeat(4) { c -> cells += subject?.get(c).orEmpty() }
            }
            lines += csvLine(listOf("") + cells)
        }
        return "\uFEFF" + lines.joinToString("\r\n", postfix = "\r\n")
    }

    fun decode(csv: String): List<StudentRow> {
        val parsed = parse(csv.removePrefix("\uFEFF").trimEnd('\r', '\n'))
        require(parsed.isNotEmpty()) { "ملف CSV فارغ" }
        require(parsed[0].firstOrNull() == HEADER_PREFIX) {
            "تنسيق CSV غير معروف؛ يجب أن يبدأ رأس الملف بـ $HEADER_PREFIX"
        }
        val expectedHeader = listOf(HEADER_PREFIX, "name", "gender", "stage") +
            (1..12).flatMap { i -> (1..4).map { c -> "s${i}_c$c" } }
        require(parsed[0] == expectedHeader) { "رأس CSV لا يطابق إصدار التنسيق المطلوب" }
        return parsed.drop(1).filter { it.isNotEmpty() && it.any(String::isNotBlank) }.mapIndexed { index, row ->
            // Each data row reserves an empty first cell, so the row width must match the header.
            require(row.size == expectedHeader.size) {
                "عدد الأعمدة غير صحيح في السجل ${index + 1}: المتوقع ${expectedHeader.size}، الموجود ${row.size}"
            }
            require(row[0].isEmpty()) { "بداية السجل ${index + 1} غير صحيحة" }
            val name = row[1]
            val gender = row[2]
            require(gender.equals("F", true) || gender.equals("M", true)) {
                "صيغة النوع غير صحيحة في السجل ${index + 1}"
            }
            val stage = row[3]
            val count = stages[stage] ?: throw IllegalArgumentException("مرحلة غير مدعومة في السجل ${index + 1}: $stage")
            val subjects = (0 until count).map { i ->
                (0 until 4).map { c -> row[4 + i * 4 + c] }
            }
            StudentRow(name, gender.equals("F", true), stage, subjects)
        }
    }

    private fun csvLine(cells: List<String>): String = cells.joinToString(",") { cell ->
        if (cell.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else cell
    }

    /** Small RFC-4180-compatible parser for commas, escaped quotes and multiline fields. */
    private fun parse(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                quoted && ch == '"' && i + 1 < input.length && input[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    row += cell.toString(); cell.setLength(0)
                }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    rows += row; row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        require(!quoted) { "علامات الاقتباس في CSV غير مكتملة" }
        row += cell.toString()
        if (row.any(String::isNotEmpty)) rows += row
        return rows
    }
}
