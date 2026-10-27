package com.schoolcontrol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeRulesTest {
    @Test fun subjectSumAddsFourGrades() {
        assertEquals(100.0, GradeRules.subjectTotal(listOf(10, 20, 30, 40))!!, 0.0)
    }

    @Test fun subjectSumIgnoresBlankCells() {
        assertEquals(30.0, GradeRules.subjectTotal(listOf(10, null, 20, ""))!!, 0.0)
    }

    @Test fun allBlankSubjectRemainsBlank() {
        assertNull(GradeRules.subjectTotal(listOf(null, "", null, "")))
    }

    @Test fun numericZeroRemainsZero() {
        assertEquals(0.0, GradeRules.subjectTotal(listOf(0, 0, 0, 0))!!, 0.0)
        assertEquals(0.0, GradeRules.grade10FinalTotal(listOf(0) + List(11) { null })!!, 0.0)
    }

    @Test fun grade10AddsAllTwelveSubjectsIncludingWAndALPositions() {
        val totals = listOf<Any?>(10, null, 5, 7, 20, null, 1, null, 2, 3, 4, 6)
        assertEquals(58.0, GradeRules.grade10FinalTotal(totals)!!, 0.0)
    }

    @Test fun grade11ScienceAddsNineSubjects() {
        val totals = listOf<Any?>(1, null, 3, 4, null, 6, 7, 8, 9)
        assertEquals(38.0, GradeRules.grade11ScienceFinalTotal(totals)!!, 0.0)
    }

    @Test fun allBlankFinalTotalsRemainBlank() {
        assertNull(GradeRules.grade10FinalTotal(List(12) { null }))
        assertNull(GradeRules.grade11ScienceFinalTotal(List(9) { "" }))
    }

    @Test fun sheetFUsesEightSubjectsAndIgnoresUnassignedBlankTotals() {
        assertEquals(300.0, GradeRules.sheetFFinalTotal(listOf(60, null, 70, "", 0, 80, null, 90))!!, 0.0)
    }

    @Test fun sheetFAllBlankTotalsRemainBlankAndZeroIsPreserved() {
        assertNull(GradeRules.sheetFFinalTotal(listOf(null, "", null, "   ", null, null, "", null)))
        assertEquals(0.0, GradeRules.sheetFFinalTotal(listOf(0, null, null, null, null, null, null, null))!!, 0.0)
    }

    @Test fun sheetFSubjectTotalColumnsAreAllEightAndIncludeWAndAL() {
        assertEquals(listOf("H", "M", "R", "W", "AB", "AG", "AL", "AQ"),
            GradeRules.sheetFSubjectTotalColumns)
    }

    @Test fun sheetFEachSubjectColumnContributesToFinalTotal() {
        for (index in 0 until 8) {
            val totals = MutableList<Any?>(8) { null }
            totals[index] = 17
            assertEquals("subject column ${GradeRules.sheetFSubjectTotalColumns[index]}",
                17.0, GradeRules.sheetFFinalTotal(totals)!!, 0.0)
        }
    }

    @Test fun sheetFBlanksBeforeBetweenAndAfterAssignedSubjectsAreIgnored() {
        assertEquals(125.0,
            GradeRules.sheetFFinalTotal(listOf(null, 25, "", 40, null, 60, "   ", null))!!, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sheetFRejectsAnythingOtherThanEightSubjectTotals() {
        GradeRules.sheetFFinalTotal(listOf(10, 20, 30))
    }

    @Test fun absenceMarkerIsNotNumericZero() {
        assertTrue(GradeRules.isAbsenceMarker("غ"))
        assertTrue(GradeRules.isAbsenceMarker("غائبة"))
        assertFalse(GradeRules.isAbsenceMarker("0"))
        assertEquals("غ", "غ") // تظل العلامة النصية محفوظة في طبقة البيانات؛ لا تُحوّل إلى 0
    }

    @Test(expected = IllegalArgumentException::class)
    fun grade10RejectsWrongSubjectCount() {
        GradeRules.grade10FinalTotal(listOf(1, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun subjectTotalRejectsWrongCellCount() {
        GradeRules.subjectTotal(listOf(1, 2, 3))
    }


    @Test fun resultIsBlankWhenStudentNameIsMissing() {
        assertNull(GradeRules.classifyResult(false, listOf("عربي"), listOf(90), emptyList()))
    }

    @Test fun allApplicableSubjectsMustReachFifty() {
        assertEquals(
            GradeRules.ResultStatus.PASS,
            GradeRules.classifyResult(true, listOf("عربي", "رياضيات"), listOf(50, 70), emptyList())
        )
        assertEquals(
            GradeRules.ResultStatus.FAIL,
            GradeRules.classifyResult(true, listOf("عربي", "رياضيات"), listOf(50, 49), emptyList())
        )
    }

    @Test fun blankSubjectHeaderIsNotCounted() {
        assertEquals(
            GradeRules.ResultStatus.PASS,
            GradeRules.classifyResult(true, listOf("عربي", ""), listOf(60, null), emptyList())
        )
    }

    @Test fun explicitAbsenceCellOverridesMarks() {
        assertEquals(
            GradeRules.ResultStatus.ABSENT,
            GradeRules.classifyResult(true, listOf("عربي"), listOf(99), listOf("غ"))
        )
    }


    @Test fun absenceMarkerInSecondCellMeansFailEvenWhenSumIsAtLeastFifty() {
        assertEquals(
            GradeRules.SubjectStatus.FAIL,
            GradeRules.classifySubject(listOf(20, "غ", 20, 20))
        )
    }

    @Test fun absenceMarkerInFourthCellMeansAbsentInThatSubject() {
        assertEquals(
            GradeRules.SubjectStatus.ABSENT,
            GradeRules.classifySubject(listOf(20, 20, 20, "غ"))
        )
    }

    @Test fun secondCellAbsenceDoesNotMeanAbsentInSubject() {
        assertEquals(
            GradeRules.SubjectStatus.FAIL,
            GradeRules.classifySubject(listOf(10, "غ", 10, 30))
        )
    }

    @Test fun fourthCellAbsenceTakesPriorityIfBothMarkersExist() {
        assertEquals(
            GradeRules.SubjectStatus.ABSENT,
            GradeRules.classifySubject(listOf(10, "غ", 10, "غ"))
        )
    }

    @Test fun fourBlankCellsMeanNoApplicableSubject() {
        assertEquals(
            GradeRules.SubjectStatus.NOT_APPLICABLE,
            GradeRules.classifySubject(listOf(null, "", null, ""))
        )
    }


    @Test fun sheetFUsesTheSameAbsenceAndSecondCellFailureRules() {
        val normal = List(8) { listOf<Any?>(20, 20, 20, 20) }
        val secondMarker = normal.toMutableList().also { it[6] = listOf(20, "غ", 20, 20) }
        val fourthMarker = normal.toMutableList().also { it[7] = listOf(20, 20, 20, "غ") }
        assertEquals(GradeRules.ResultStatus.FAIL, GradeRules.classifySheetFResult(true, secondMarker))
        assertEquals(GradeRules.ResultStatus.ABSENT, GradeRules.classifySheetFResult(true, fourthMarker))
        assertEquals(GradeRules.ResultStatus.ABSENT,
            GradeRules.classifySheetFResult(true, secondMarker.mapIndexed { i, row ->
                if (i == 0) listOf<Any?>(20, 20, 20, "غ") else row
            }))
        assertNull(GradeRules.classifySheetFResult(false, normal))
    }

    @Test fun sheetSUsesTheSameRulesForTwelveAndNineSubjectLayouts() {
        for (count in listOf(12, 9)) {
            val normal = List(count) { listOf<Any?>(20, 20, 20, 20) }
            for (i in 0 until count) {
                val second = normal.toMutableList().also { it[i] = listOf(20, "غ", 20, 20) }
                assertEquals("S/$count second marker at ${i + 1}", GradeRules.ResultStatus.FAIL,
                    GradeRules.classifySheetSResult(true, second, count))
                val fourth = normal.toMutableList().also { it[i] = listOf(20, 20, 20, "غ" ) }
                assertEquals("S/$count fourth marker at ${i + 1}", GradeRules.ResultStatus.ABSENT,
                    GradeRules.classifySheetSResult(true, fourth, count))
            }
            for (secondIndex in 0 until count) for (fourthIndex in 0 until count) {
                val both = normal.toMutableList()
                both[secondIndex] = listOf(20, "غ", 20, 20)
                both[fourthIndex] = listOf(20, 20, 20, "غ")
                assertEquals("S/$count precedence second=$secondIndex fourth=$fourthIndex",
                    GradeRules.ResultStatus.ABSENT,
                    GradeRules.classifySheetSResult(true, both, count))
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun sheetFResultRejectsWrongSubjectCount() {
        GradeRules.classifySheetFResult(true, List(7) { listOf<Any?>(20, 20, 20, 20) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun sheetSResultRejectsUnsupportedSubjectCount() {
        GradeRules.classifySheetSResult(true, List(8) { listOf<Any?>(20, 20, 20, 20) }, 8)
    }

    @Test fun resultLabelsRespectGender() {
        assertEquals("ناجح", GradeRules.resultLabel(GradeRules.ResultStatus.PASS, false))
        assertEquals("ناجحة", GradeRules.resultLabel(GradeRules.ResultStatus.PASS, true))
        assertEquals("راسبة", GradeRules.resultLabel(GradeRules.ResultStatus.FAIL, true))
        assertEquals("غائبة", GradeRules.resultLabel(GradeRules.ResultStatus.ABSENT, true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun resultRequiresMatchingHeaderAndMarkCounts() {
        GradeRules.classifyResult(true, listOf("عربي"), listOf(80, 70), emptyList())
    }



    @Test fun secondColumnAbsenceMakesOverallResultFailEvenIfAllMarksPass() {
        assertEquals(
            GradeRules.ResultStatus.FAIL,
            GradeRules.classifyResult(
                true, listOf("عربي", "رياضيات"), listOf(90, 100), emptyList(), listOf("غ")
            )
        )
    }

    @Test fun fourthColumnAbsenceMakesOverallResultAbsentEvenIfAllMarksPass() {
        assertEquals(
            GradeRules.ResultStatus.ABSENT,
            GradeRules.classifyResult(
                true, listOf("عربي", "رياضيات"), listOf(90, 100), listOf("غ")
            )
        )
    }

    @Test fun fourthColumnAbsenceTakesPriorityOverSecondColumnFailure() {
        assertEquals(
            GradeRules.ResultStatus.ABSENT,
            GradeRules.classifyResult(
                true, listOf("عربي"), listOf(90), listOf("غ"), listOf("غ")
            )
        )
    }

    @Test fun zeroConfiguredSubjectsFollowTheCountComparisonRule() {
        assertEquals(
            GradeRules.ResultStatus.PASS,
            GradeRules.classifyResult(true, emptyList(), emptyList(), emptyList())
        )
    }


    @Test fun overallResultFailsIfSecondCellHasAbsenceMarkerInAnySubject() {
        assertEquals(
            GradeRules.ResultStatus.FAIL,
            GradeRules.classifyOverallFromSubjects(
                true,
                listOf(listOf(20, "غ", 20, 20), listOf(20, 20, 20, 20))
            )
        )
    }

    @Test fun overallResultIsAbsentIfFourthCellHasAbsenceMarkerInAnySubject() {
        assertEquals(
            GradeRules.ResultStatus.ABSENT,
            GradeRules.classifyOverallFromSubjects(
                true,
                listOf(listOf(20, "غ", 20, 20), listOf(20, 20, 20, "غ"))
            )
        )
    }

    @Test fun overallResultFailsIfAnySubjectFailsNormally() {
        assertEquals(
            GradeRules.ResultStatus.FAIL,
            GradeRules.classifyOverallFromSubjects(
                true,
                listOf(listOf(20, 20, 20, 20), listOf(10, 10, 10, 10))
            )
        )
    }

    @Test fun overallResultPassesWhenAllApplicableSubjectsPass() {
        assertEquals(
            GradeRules.ResultStatus.PASS,
            GradeRules.classifyOverallFromSubjects(
                true,
                listOf(listOf(15, 15, 10, 10), listOf(20, 20, 20, 20), listOf(null, "", null, ""))
            )
        )
    }

    @Test fun fiveColumnLayoutExtractsSecondAndFourthCellsFromEverySubject() {
        // كل مجموعة: درجة1، درجة2، درجة3، درجة4، مجموع المادة.
        val row = listOf<Any?>(
            10, "غ", 20, 20, 50,
            10, 10, 10, "غ", 30,
            20, 20, 20, 20, 80
        )
        val subjects = GradeRules.extractSubjectsFromFiveColumnBlocks(row, 3)
        assertEquals(listOf(10, "غ", 20, 20), subjects[0])
        assertEquals(listOf(10, 10, 10, "غ"), subjects[1])
        assertEquals(GradeRules.ResultStatus.ABSENT,
            GradeRules.classifyOverallFromSubjects(true, subjects))
    }

    @Test fun secondCellInLastSubjectIsNotSkippedByFlatRowMapping() {
        val row = List<Any?>(10) { 0 }.toMutableList<Any?>()
        row[5] = 25
        row[6] = "غ" // الخلية الثانية للمادة الثانية
        val subjects = GradeRules.extractSubjectsFromFiveColumnBlocks(row, 2)
        assertEquals(GradeRules.ResultStatus.FAIL,
            GradeRules.classifyOverallFromSubjects(true, subjects))
    }

    @Test(expected = IllegalArgumentException::class)
    fun fiveColumnLayoutRejectsTruncatedRows() {
        GradeRules.extractSubjectsFromFiveColumnBlocks(listOf(1, 2, 3, 4), 1)
    }

    @Test fun twelveSubjectAbsenceColumnsIncludePreviouslyOmittedPositions() {
        val (second, fourth) = GradeRules.secondAndFourthAbsenceColumns("D", 12)
        assertEquals(listOf("E", "J", "O", "T", "Y", "AD", "AI", "AN", "AS", "AX", "BC", "BH"), second)
        assertEquals(listOf("G", "L", "Q", "V", "AA", "AF", "AK", "AP", "AU", "AZ", "BE", "BJ"), fourth)
    }

    @Test fun nineSubjectAbsenceColumnsMatchScienceLayout() {
        val (second, fourth) = GradeRules.secondAndFourthAbsenceColumns("D", 9)
        assertEquals(listOf("E", "J", "O", "T", "Y", "AD", "AI", "AN", "AS"), second)
        assertEquals(listOf("G", "L", "Q", "V", "AA", "AF", "AK", "AP", "AU"), fourth)
    }

    @Test fun columnMapperSupportsColumnsBeyondZ() {
        val mapping = GradeRules.mapFiveColumnSubjectBlocks("AX", 2)
        assertEquals("AX", mapping[0].first)
        assertEquals("BA", mapping[0].fourth)
        assertEquals("BG", mapping[1].total)
    }

    @Test fun overallResultIsNullWhenStudentNameIsMissing() {
        assertNull(
            GradeRules.classifyOverallFromSubjects(
                false,
                listOf(listOf(20, 20, 20, 20))
            )
        )
    }
}
