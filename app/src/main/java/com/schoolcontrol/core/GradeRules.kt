package com.schoolcontrol.core

/**
 * قواعد جمع الدرجات المستخلصة من سجل المراجعة.
 *
 * لا تتعامل هذه الوحدة مع ملفات XLSB مباشرة، ولا تغيّر المصدر.
 * الفراغ ممثل بـ null أو نص فارغ؛ الصفر الرقمي يبقى قيمة فعلية.
 */
object GradeRules {
    data class RowBlock(val start: Int, val endInclusive: Int)

    val grade10Blocks = listOf(
        RowBlock(6, 396),
        RowBlock(5006, 5396)
    )

    val grade11ScienceBlocks = listOf(
        RowBlock(406, 796),
        RowBlock(5406, 5796)
    )

    val grade10SubjectTotalColumns = listOf(
        "H", "M", "R", "W", "AB", "AG", "AL", "AQ", "AV", "BA", "BF", "BK"
    )

    val grade11ScienceSubjectTotalColumns = listOf(
        "H", "M", "R", "W", "AB", "AG", "AL", "AQ", "AV"
    )

    /** أعمدة مجاميع المواد الثماني في الورقة F وفق تخطيط sum_deg_pry. */
    val sheetFSubjectTotalColumns = listOf(
        "H", "M", "R", "W", "AB", "AG", "AL", "AQ"
    )

    /** كتل الصفوف المعروفة في sum_deg_pry / sum_deg_pry_m؛ فواصل الصفوف مستثناة. */
    val sheetFBlocks = listOf(
        RowBlock(6, 205), RowBlock(207, 396),
        RowBlock(406, 605), RowBlock(607, 796),
        RowBlock(806, 1005), RowBlock(1007, 1196),
        RowBlock(1206, 1405), RowBlock(1407, 1596),
        RowBlock(1606, 1805), RowBlock(1807, 1996),
        RowBlock(2006, 2205), RowBlock(2207, 2396),
        RowBlock(2406, 2605), RowBlock(2607, 2796),
        RowBlock(2806, 3005), RowBlock(3007, 3196)
    )

    private fun isBlank(value: Any?): Boolean =
        value == null || (value is String && value.isBlank())

    private fun numericValue(value: Any?): Double =
        when (value) {
            is Byte -> value.toDouble()
            is Short -> value.toDouble()
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Float -> if (value.isFinite()) value.toDouble() else 0.0
            is Double -> if (value.isFinite()) value else 0.0
            else -> 0.0 // النصوص/علامة الغياب لا تدخل في الجمع
        }

    /** مجموع خلايا الدرجات الأربع؛ يبقى فارغًا إن كانت الأربع فارغة. */
    fun subjectTotal(gradeCells: List<Any?>): Double? {
        require(gradeCells.size == 4) { "يلزم تمرير أربع خلايا درجات" }
        if (gradeCells.all(::isBlank)) return null
        return gradeCells.sumOf(::numericValue)
    }

    /**
     * يجمع مجاميع المواد المقررة فقط.
     * يتجاوز الفراغات، ويحافظ على الصفر الحقيقي، ويرفض اختلاف عدد المواد.
     */
    fun finalTotal(subjectTotals: List<Any?>, expectedSubjects: Int): Double? {
        require(expectedSubjects > 0) { "عدد المواد يجب أن يكون موجبًا" }
        require(subjectTotals.size == expectedSubjects) {
            "المتوقع $expectedSubjects مجموع مادة، والموجود ${subjectTotals.size}"
        }
        if (subjectTotals.all(::isBlank)) return null
        return subjectTotals.sumOf(::numericValue)
    }

    fun grade10FinalTotal(subjectTotals: List<Any?>): Double? =
        finalTotal(subjectTotals, expectedSubjects = 12)

    fun grade11ScienceFinalTotal(subjectTotals: List<Any?>): Double? =
        finalTotal(subjectTotals, expectedSubjects = 9)

    /**
     * الورقة F: تعتمد ثماني مواد. مجموع المادة غير المقررة قد يبقى فارغًا؛
     * finalTotal يتجاوز الخلايا الفارغة ويجمع كل المجاميع المتاحة فقط.
     */
    fun sheetFFinalTotal(subjectTotals: List<Any?>): Double? =
        finalTotal(subjectTotals, expectedSubjects = 8)

    /** علامة الغياب الخاصة لا تتحول إلى صفر أو إلى رقم. */
    fun isAbsenceMarker(value: Any?): Boolean =
        value is String && value.trim() in setOf("غ", "غائب", "غائبة")

    enum class SubjectStatus { PASS, FAIL, ABSENT, NOT_APPLICABLE }

    /**
     * نتيجة مادة واحدة وفق ترتيب خلاياها الأربع:
     * [0] الخلية الأولى، [1] الثانية، [2] الثالثة، [3] الرابعة.
     * - «غ» في الخلية الرابعة: غياب في هذه المادة.
     * - «غ» في الخلية الثانية فقط: رسوب في هذه المادة، حتى لو بلغ المجموع 50.
     * - إذا كانت الخلايا الأربع فارغة، فلا توجد مادة مسجلة في هذا الصف.
     */
    fun classifySubject(gradeCells: List<Any?>): SubjectStatus {
        require(gradeCells.size == 4) { "يلزم تمرير خلايا المادة الأربع بالترتيب" }
        if (gradeCells.all(::isBlank)) return SubjectStatus.NOT_APPLICABLE
        if (isAbsenceMarker(gradeCells[3])) return SubjectStatus.ABSENT
        if (isAbsenceMarker(gradeCells[1])) return SubjectStatus.FAIL
        val total = subjectTotal(gradeCells) ?: return SubjectStatus.NOT_APPLICABLE
        return if (total >= 50.0) SubjectStatus.PASS else SubjectStatus.FAIL
    }

    enum class ResultStatus(val masculineLabel: String, val feminineLabel: String) {
        PASS("ناجح", "ناجحة"),
        FAIL("راسب", "راسبة"),
        ABSENT("غائب", "غائبة")
    }

    /**
     * تجميع نتيجة الصف حسب المقارنة الحالية لعدد المواد الناجحة والمقررة.
     * absenceCells: خلايا العمود الرابع؛ وجود «غ» في أي مادة يجعل النتيجة العامة غائب.
     * secondColumnAbsenceCells: خلايا العمود الثاني؛ وجود «غ» في أي مادة يجعل النتيجة العامة راسب.
     * عند اجتماع العلامتين، الغياب في العمود الرابع له الأولوية.
     */
    fun classifyResult(
        studentNamePresent: Boolean,
        subjectHeaders: List<Any?>,
        subjectMarks: List<Any?>,
        absenceCells: List<Any?>,
        secondColumnAbsenceCells: List<Any?> = emptyList()
    ): ResultStatus? {
        require(subjectHeaders.size == subjectMarks.size) {
            "يجب أن تتطابق أعداد عناوين المواد وعلاماتها"
        }
        if (!studentNamePresent) return null
        if (absenceCells.any(::isAbsenceMarker)) return ResultStatus.ABSENT
        if (secondColumnAbsenceCells.any(::isAbsenceMarker)) return ResultStatus.FAIL

        val applicableSubjects = subjectHeaders.count { !isBlank(it) }
        val passedSubjects = subjectHeaders.indices.count { i ->
            !isBlank(subjectHeaders[i]) && numericValue(subjectMarks[i]) >= 50.0
        }
        return if (passedSubjects == applicableSubjects) ResultStatus.PASS
        else ResultStatus.FAIL
    }

    /**
     * يستخرج خلايا الدرجات الأربع من صف يبدأ عند أول درجة مادة، حيث تتكرر
     * بنية VBA المؤكدة: أربع خلايا درجات ثم خلية مجموع (خطوة 5 أعمدة).
     * لا يُمرَّر عمود المجموع إلى منطق تصنيف المادة.
     * rowCells يبدأ من أول عمود درجات، وليس من العمود A.
     */
    fun extractSubjectsFromFiveColumnBlocks(
        rowCells: List<Any?>,
        subjectCount: Int
    ): List<List<Any?>> {
        require(subjectCount >= 0) { "عدد المواد لا يمكن أن يكون سالبًا" }
        require(rowCells.size >= subjectCount * 5) {
            "الصف لا يحتوي على $subjectCount كتل مواد كاملة (5 أعمدة لكل كتلة)"
        }
        return (0 until subjectCount).map { subjectIndex ->
            val start = subjectIndex * 5
            rowCells.subList(start, start + 4).toList()
        }
    }

    data class SubjectColumnMap(
        val subjectNumber: Int,
        val first: String,
        val second: String,
        val third: String,
        val fourth: String,
        val total: String
    )

    /**
     * يبني خريطة الأعمدة من تخطيط الخمس خلايا المؤكد: أربع درجات ثم مجموع.
     * مثال البداية D ينتج للمادة الأولى D:E:F:G ومجموعها H، ثم I:M وهكذا.
     * لا تُستخدم هذه الخريطة إلا بعد التأكد من أن الصف يتبع هذا التخطيط.
     */
    fun mapFiveColumnSubjectBlocks(firstGradeColumn: String, subjectCount: Int): List<SubjectColumnMap> {
        require(subjectCount >= 0) { "عدد المواد لا يمكن أن يكون سالبًا" }
        val firstIndex = excelColumnToNumber(firstGradeColumn)
        return (0 until subjectCount).map { i ->
            val start = firstIndex + i * 5
            SubjectColumnMap(
                subjectNumber = i + 1,
                first = excelColumnName(start),
                second = excelColumnName(start + 1),
                third = excelColumnName(start + 2),
                fourth = excelColumnName(start + 3),
                total = excelColumnName(start + 4)
            )
        }
    }

    fun secondAndFourthAbsenceColumns(firstGradeColumn: String, subjectCount: Int): Pair<List<String>, List<String>> {
        val mapping = mapFiveColumnSubjectBlocks(firstGradeColumn, subjectCount)
        return mapping.map { it.second } to mapping.map { it.fourth }
    }

    private fun excelColumnToNumber(column: String): Int {
        val normalized = column.trim().uppercase()
        require(normalized.isNotEmpty() && normalized.all { it in 'A'..'Z' }) {
            "اسم عمود Excel غير صالح: $column"
        }
        return normalized.fold(0) { acc, char -> acc * 26 + (char - 'A' + 1) }
    }

    private fun excelColumnName(number: Int): String {
        require(number > 0) { "رقم العمود يجب أن يكون موجبًا" }
        var n = number
        val result = StringBuilder()
        while (n > 0) {
            val remainder = (n - 1) % 26
            result.append(('A'.code + remainder).toChar())
            n = (n - 1) / 26
        }
        return result.reverse().toString()
    }

    /**
     * يحسب النتيجة العامة مباشرة من خلايا المواد الأربع لكل مادة.
     * الأولوية العامة: غ في الخلية الرابعة لأي مادة => غائب؛ وإلا غ في
     * الخلية الثانية لأي مادة => راسب؛ وإلا يجب نجاح كل مادة منطبقة.
     * المواد الأربع الفارغة لا تدخل في الحكم. إذا لم توجد مواد منطبقة،
     * تبقى المقارنة متوافقة مع قاعدة COUNTIF الحالية (0 = 0).
     */
    fun classifyOverallFromSubjects(
        studentNamePresent: Boolean,
        subjects: List<List<Any?>>
    ): ResultStatus? {
        if (!studentNamePresent) return null
        require(subjects.all { it.size == 4 }) {
            "يجب تمرير أربع خلايا بالترتيب لكل مادة"
        }
        val statuses = subjects.map(::classifySubject)
        if (statuses.any { it == SubjectStatus.ABSENT }) return ResultStatus.ABSENT
        if (statuses.any { it == SubjectStatus.FAIL }) return ResultStatus.FAIL
        return ResultStatus.PASS
    }

    /** النتيجة العامة في الورقة F: ثماني مواد، وقاعدة الغياب/الرسوب نفسها. */
    fun classifySheetFResult(
        studentNamePresent: Boolean,
        subjects: List<List<Any?>>
    ): ResultStatus? {
        require(subjects.size == 8) { "الورقة F تعتمد ثماني مواد" }
        return classifyOverallFromSubjects(studentNamePresent, subjects)
    }

    /**
     * النتيجة العامة في الورقة S: 12 مادة للصف العاشر أو 9 مواد للصف الحادي عشر العلمي.
     * علامات «غ» في الخلية الثانية والرابعة تُطبّق على كل مادة، لا على مواد مختارة فقط.
     */
    fun classifySheetSResult(
        studentNamePresent: Boolean,
        subjects: List<List<Any?>>,
        expectedSubjects: Int
    ): ResultStatus? {
        require(expectedSubjects == 12 || expectedSubjects == 9) {
            "الورقة S مدعومة هنا بتخطيط 12 مادة أو 9 مواد"
        }
        require(subjects.size == expectedSubjects) {
            "المتوقع $expectedSubjects مادة في الورقة S، والموجود ${subjects.size}"
        }
        return classifyOverallFromSubjects(studentNamePresent, subjects)
    }

    fun resultLabel(status: ResultStatus?, feminine: Boolean): String? =
        status?.let { if (feminine) it.feminineLabel else it.masculineLabel }

}
