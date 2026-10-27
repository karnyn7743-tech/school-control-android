import com.schoolcontrol.core.GradeRules
import com.schoolcontrol.core.StudentCsvCodec
import java.io.File

fun main(args: Array<String>) {
    val file = File(args.single())
    val rows = StudentCsvCodec.decode(file.readText(Charsets.UTF_8))
    check(rows.size == 5) { "Expected 5 demo students, got ${rows.size}" }
    check(rows.map { it.stage } == listOf("F8", "F8", "S10", "S11SCI", "S10"))
    check(rows[0].name == "نموذج تجريبي ١")
    check(rows[2].subjects[2][1] == "غ")
    check(rows[3].subjects[4][3] == "غ")

    fun total(row: StudentCsvCodec.StudentRow): Double? {
        val totals = row.subjects.map { cells ->
            GradeRules.subjectTotal(cells.map { cell ->
                cell.trim().let { v -> if (v.isEmpty()) null else if (GradeRules.isAbsenceMarker(v)) v else v.toDoubleOrNull() ?: v }
            })
        }
        return when (row.stage) {
            "F8" -> GradeRules.sheetFFinalTotal(totals)
            "S10" -> GradeRules.grade10FinalTotal(totals)
            else -> GradeRules.grade11ScienceFinalTotal(totals)
        }
    }
    fun result(row: StudentCsvCodec.StudentRow): GradeRules.ResultStatus? {
        val cells = row.subjects.map { subject -> subject.map { v ->
            v.trim().let { x -> if (x.isEmpty()) null else if (GradeRules.isAbsenceMarker(x)) x else x.toDoubleOrNull() ?: x }
        } }
        return when (row.stage) {
            "F8" -> GradeRules.classifySheetFResult(row.name.isNotBlank(), cells)
            "S10" -> GradeRules.classifySheetSResult(row.name.isNotBlank(), cells, 12)
            else -> GradeRules.classifySheetSResult(row.name.isNotBlank(), cells, 9)
        }
    }
    val expectedTotals = listOf(480.0, 640.0, 470.0, 420.0, 0.0)
    val expectedResults = listOf(
        GradeRules.ResultStatus.PASS,
        GradeRules.ResultStatus.PASS,
        GradeRules.ResultStatus.FAIL,
        GradeRules.ResultStatus.ABSENT,
        GradeRules.ResultStatus.FAIL
    )
    rows.forEachIndexed { i, row ->
        check(total(row) == expectedTotals[i]) { "Unexpected total for ${row.name}: ${total(row)}" }
        check(result(row) == expectedResults[i]) { "Unexpected result for ${row.name}: ${result(row)}" }
    }
    val roundTrip = StudentCsvCodec.decode(StudentCsvCodec.encode(rows))
    check(roundTrip == rows) { "Encode/decode round-trip changed sample data" }
    println("PASS: 5 fictional students decoded; totals, result statuses, absence precedence and CSV round-trip verified")
    rows.forEachIndexed { i, row -> println("${i + 1}. ${row.name}: total=${total(row)}, result=${result(row)}") }
}
