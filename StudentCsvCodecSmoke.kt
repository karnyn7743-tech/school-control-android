import com.schoolcontrol.core.StudentCsvCodec

fun main() {
    val row = StudentCsvCodec.StudentRow(
        name = "أحمد, محمد",
        feminine = false,
        stage = "F8",
        subjects = (0 until 8).map { i ->
            listOf(if (i == 0) "0" else "", if (i == 1) "غ" else "", "25", if (i == 2) "غ" else "")
        }
    )
    val encoded = StudentCsvCodec.encode(listOf(row))
    val decoded = StudentCsvCodec.decode(encoded)
    check(decoded.size == 1)
    check(decoded[0].name == row.name)
    check(decoded[0].stage == "F8")
    check(decoded[0].subjects == row.subjects)
    val second = row.copy(name = "طالبة \"متميزة\"", feminine = true, stage = "S11SCI",
        subjects = (0 until 9).map { listOf("", "", "0", "") })
    val roundTrip = StudentCsvCodec.decode(StudentCsvCodec.encode(listOf(second)))
    check(roundTrip.single() == second)
    val third = row.copy(name = "اسم\nبسطر جديد", feminine = true)
    val batch = StudentCsvCodec.decode(StudentCsvCodec.encode(listOf(row, second, third)))
    check(batch == listOf(row, second, third))
    check(runCatching { StudentCsvCodec.decode("school_control_csv_v1\n\"bad") }.isFailure)
    check(runCatching { StudentCsvCodec.encode(listOf(row.copy(stage = "UNKNOWN"))) }.isFailure)
    val malformedHeader = encoded.replace("s1_c1", "bad_column")
    check(runCatching { StudentCsvCodec.decode(malformedHeader) }.isFailure)
    val malformedRow = encoded.replaceFirst("\r\n,", "\r\n")
    check(runCatching { StudentCsvCodec.decode(malformedRow) }.isFailure)
    val invalidGender = encoded.replace(",M,F8,", ",X,F8,")
    check(runCatching { StudentCsvCodec.decode(invalidGender) }.isFailure)
    println("ALL CSV codec smoke checks passed: round-trip, batch, Arabic/quoted/multiline fields, header/row/gender validation")
}
