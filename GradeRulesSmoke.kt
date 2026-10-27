import com.schoolcontrol.core.GradeRules

private var passed = 0
private fun checkCase(name: String, condition: Boolean) {
    check(condition) { "FAILED: $name" }
    passed++
    println("PASS: $name")
}

fun main() {
    checkCase("second-cell marker fails subject despite total >=50",
        GradeRules.classifySubject(listOf(20, "غ", 20, 20)) == GradeRules.SubjectStatus.FAIL)
    checkCase("fourth-cell marker means absent",
        GradeRules.classifySubject(listOf(20, 20, 20, "غ")) == GradeRules.SubjectStatus.ABSENT)
    checkCase("fourth-cell marker has priority within the same subject",
        GradeRules.classifySubject(listOf(20, "غ", 20, "غ")) == GradeRules.SubjectStatus.ABSENT)

    val row = listOf<Any?>(10, "غ", 20, 20, 50, 10, 10, 10, "غ", 30, 20, 20, 20, 20, 80)
    val subjects = GradeRules.extractSubjectsFromFiveColumnBlocks(row, 3)
    checkCase("extract four grades from each five-column block",
        subjects == listOf(listOf(10, "غ", 20, 20), listOf(10, 10, 10, "غ"), listOf(20, 20, 20, 20)))
    checkCase("fourth-cell absence globally overrides second-cell fail",
        GradeRules.classifyOverallFromSubjects(true, subjects) == GradeRules.ResultStatus.ABSENT)
    val row2 = listOf<Any?>(10, 10, 10, 10, 40, 25, "غ", 20, 20, 65)
    checkCase("second cell is checked in later subject",
        GradeRules.classifyOverallFromSubjects(true, GradeRules.extractSubjectsFromFiveColumnBlocks(row2, 2)) == GradeRules.ResultStatus.FAIL)
    checkCase("missing student remains blank",
        GradeRules.classifyOverallFromSubjects(false, subjects) == null)
    checkCase("all applicable subjects pass",
        GradeRules.classifyOverallFromSubjects(true, listOf(listOf(15,15,10,10), listOf(20,20,20,20))) == GradeRules.ResultStatus.PASS)

    val (second12, fourth12) = GradeRules.secondAndFourthAbsenceColumns("D", 12)
    checkCase("12-subject second-cell map is complete",
        second12 == listOf("E", "J", "O", "T", "Y", "AD", "AI", "AN", "AS", "AX", "BC", "BH"))
    checkCase("12-subject fourth-cell map includes AP and BE",
        fourth12 == listOf("G", "L", "Q", "V", "AA", "AF", "AK", "AP", "AU", "AZ", "BE", "BJ"))
    val (second9, fourth9) = GradeRules.secondAndFourthAbsenceColumns("D", 9)
    checkCase("9-subject second-cell map is complete",
        second9 == listOf("E", "J", "O", "T", "Y", "AD", "AI", "AN", "AS"))
    checkCase("9-subject fourth-cell map is complete",
        fourth9 == listOf("G", "L", "Q", "V", "AA", "AF", "AK", "AP", "AU"))
    checkCase("column mapping works beyond Z",
        GradeRules.mapFiveColumnSubjectBlocks("AX", 2)[1].total == "BG")

    // ورقة F تعتمد 8 مواد، وقد تكون مجاميع المواد غير المقررة فارغة.
    checkCase("sheet F sums eight subject totals and ignores unassigned blanks",
        GradeRules.sheetFFinalTotal(listOf(60, null, 70, "", 0, 80, null, 90)) == 300.0)
    checkCase("sheet F returns blank when all eight subject totals are blank",
        GradeRules.sheetFFinalTotal(listOf(null, "", null, "   ", null, null, "", null)) == null)
    checkCase("sheet F preserves a real zero total",
        GradeRules.sheetFFinalTotal(listOf(0, null, null, null, null, null, null, null)) == 0.0)
    checkCase("sheet F uses exactly the eight source total columns H,M,R,W,AB,AG,AL,AQ",
        GradeRules.sheetFSubjectTotalColumns == listOf("H", "M", "R", "W", "AB", "AG", "AL", "AQ"))
    for (i in 0 until 8) {
        val totals = MutableList<Any?>(8) { null }
        totals[i] = 17
        checkCase("sheet F column ${GradeRules.sheetFSubjectTotalColumns[i]} contributes to final total",
            GradeRules.sheetFFinalTotal(totals) == 17.0)
    }
    checkCase("sheet F ignores blanks before, between and after assigned subjects",
        GradeRules.sheetFFinalTotal(listOf(null, 25, "", 40, null, 60, "   ", null)) == 125.0)
    checkCase("sheet F rejects totals list with wrong subject count",
        runCatching { GradeRules.sheetFFinalTotal(listOf(10, 20, 30)) }.isFailure)

    // Exhaustive rule check: every one of the 12 subject positions must affect the overall result.
    for (i in 0 until 12) {
        val base = MutableList(12) { listOf<Any?>(20, 20, 20, 20) }
        base[i] = listOf(20, "غ", 20, 20)
        checkCase("12-subject second-cell position ${i + 1} => overall fail",
            GradeRules.classifyOverallFromSubjects(true, base) == GradeRules.ResultStatus.FAIL)
    }
    for (i in 0 until 12) {
        val base = MutableList(12) { listOf<Any?>(20, 20, 20, 20) }
        base[i] = listOf(20, 20, 20, "غ")
        checkCase("12-subject fourth-cell position ${i + 1} => overall absent",
            GradeRules.classifyOverallFromSubjects(true, base) == GradeRules.ResultStatus.ABSENT)
    }
    // Cross-position precedence, not just same-subject precedence.
    for (secondIndex in 0 until 12) for (fourthIndex in 0 until 12) {
        val base = MutableList(12) { listOf<Any?>(20, 20, 20, 20) }
        base[secondIndex] = listOf(20, "غ", 20, 20)
        base[fourthIndex] = listOf(20, 20, 20, "غ")
        checkCase("priority: second=${secondIndex + 1}, fourth=${fourthIndex + 1}",
            GradeRules.classifyOverallFromSubjects(true, base) == GradeRules.ResultStatus.ABSENT)
    }

    // Eight-subject pattern found in sum_deg_pry / sum_deg_pry_m.
    val (second8, fourth8) = GradeRules.secondAndFourthAbsenceColumns("D", 8)
    checkCase("8-subject second-cell map is complete",
        second8 == listOf("E", "J", "O", "T", "Y", "AD", "AI", "AN"))
    checkCase("8-subject fourth-cell map is complete",
        fourth8 == listOf("G", "L", "Q", "V", "AA", "AF", "AK", "AP"))
    for (i in 0 until 8) {
        val base = MutableList(8) { listOf<Any?>(20, 20, 20, 20) }
        base[i] = listOf(20, "غ", 20, 20)
        checkCase("8-subject second-cell position ${i + 1} => overall fail",
            GradeRules.classifyOverallFromSubjects(true, base) == GradeRules.ResultStatus.FAIL)
    }
    for (i in 0 until 8) {
        val base = MutableList(8) { listOf<Any?>(20, 20, 20, 20) }
        base[i] = listOf(20, 20, 20, "غ")
        checkCase("8-subject fourth-cell position ${i + 1} => overall absent",
            GradeRules.classifyOverallFromSubjects(true, base) == GradeRules.ResultStatus.ABSENT)
    }
    for (secondIndex in 0 until 8) for (fourthIndex in 0 until 8) {
        val base = MutableList(8) { listOf<Any?>(20, 20, 20, 20) }
        base[secondIndex] = listOf(20, "غ", 20, 20)
        base[fourthIndex] = listOf(20, 20, 20, "غ")
        checkCase("8-subject priority: second=${secondIndex + 1}, fourth=${fourthIndex + 1}",
            GradeRules.classifyOverallFromSubjects(true, base) == GradeRules.ResultStatus.ABSENT)
    }

    // قاعدة الغياب والرسوب نفسها تُطبّق صراحة على الورقتين F وS.
    val fBase = List(8) { listOf<Any?>(20, 20, 20, 20) }
    for (i in 0 until 8) {
        val second = fBase.toMutableList().also { it[i] = listOf(20, "غ", 20, 20) }
        checkCase("F second-cell غ at subject ${i + 1} => fail",
            GradeRules.classifySheetFResult(true, second) == GradeRules.ResultStatus.FAIL)
        val fourth = fBase.toMutableList().also { it[i] = listOf(20, 20, 20, "غ") }
        checkCase("F fourth-cell غ at subject ${i + 1} => absent",
            GradeRules.classifySheetFResult(true, fourth) == GradeRules.ResultStatus.ABSENT)
    }
    for (count in listOf(12, 9)) {
        val base = List(count) { listOf<Any?>(20, 20, 20, 20) }
        for (i in 0 until count) {
            val second = base.toMutableList().also { it[i] = listOf(20, "غ", 20, 20) }
            checkCase("S/$count second-cell غ at subject ${i + 1} => fail",
                GradeRules.classifySheetSResult(true, second, count) == GradeRules.ResultStatus.FAIL)
            val fourth = base.toMutableList().also { it[i] = listOf(20, 20, 20, "غ") }
            checkCase("S/$count fourth-cell غ at subject ${i + 1} => absent",
                GradeRules.classifySheetSResult(true, fourth, count) == GradeRules.ResultStatus.ABSENT)
        }
        for (secondIndex in 0 until count) for (fourthIndex in 0 until count) {
            val both = base.toMutableList()
            both[secondIndex] = listOf(20, "غ", 20, 20)
            both[fourthIndex] = listOf(20, 20, 20, "غ")
            checkCase("S/$count precedence second=$secondIndex fourth=$fourthIndex => absent",
                GradeRules.classifySheetSResult(true, both, count) == GradeRules.ResultStatus.ABSENT)
        }
    }

    println("ALL $passed Kotlin smoke checks passed")
}
