package com.schoolcontrol.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import java.io.OutputStreamWriter
import com.schoolcontrol.core.StudentCsvCodec
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.InputType
import com.schoolcontrol.core.GradeRules

/**
 * Initial working UI scaffold. This is not yet a workbook importer/exporter.
 * It exercises the agreed grade/absence rules for a manually entered student row.
 */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_IMPORT = 701
        private const val REQUEST_EXPORT = 702
        private const val LOCAL_SESSION_FILE = "school_control_session.csv"
        private const val SESSION_PREFS = "school_control_session"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_HAS_IMPORTED_LIST = "has_imported_list"
    }
    private lateinit var stage: Spinner
    private lateinit var gender: Spinner
    private lateinit var nameInput: EditText
    private lateinit var subjectContainer: LinearLayout
    private lateinit var totalView: TextView
    private lateinit var resultView: TextView
    private val subjectInputs = mutableListOf<List<EditText>>()
    private val subjectTotalLabels = mutableListOf<TextView>()
    private var builtSubjectCount = 0
    private val importedRows = mutableListOf<StudentCsvCodec.StudentRow>()
    private var currentRecordIndex = -1
    private lateinit var recordPositionView: TextView
    private lateinit var previousRecordButton: Button
    private lateinit var nextRecordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        rebuildSubjects()
        restoreLocalSession()
    }

    override fun onPause() {
        // Keep the active record and imported list available after the app is closed.
        runCatching { persistLocalSession() }
        super.onPause()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(TextView(this).apply {
            text = "الكنترول المدرسي — نموذج حساب أولي"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, match())

        page.addView(label("الورقة / المرحلة"))
        stage = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("F — 8 مواد", "S — الصف العاشر / 12 مادة", "S — الحادي عشر العلمي / 9 مواد"))
        }
        page.addView(stage, match())
        stage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val count = when (position) { 1 -> 12; 2 -> 9; else -> 8 }
                if (count != builtSubjectCount) rebuildSubjects()
            }
        }

        page.addView(label("اسم الطالب"))
        nameInput = EditText(this).apply { hint = "اسم الطالب"; singleLine = true }
        page.addView(nameInput, match())

        page.addView(label("صيغة النتيجة"))
        gender = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("مذكر", "مؤنث"))
        }
        page.addView(gender, match())

        page.addView(TextView(this).apply {
            text = "أدخل درجات المادة الأربع. اكتب غ في الخلية الثانية للرسوب أو الرابعة للغياب."
            textSize = 15f
            setPadding(0, dp(12), 0, dp(8))
        }, match())

        subjectContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(subjectContainer, match())

        val fileActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fileActions.addView(Button(this).apply {
            text = "استيراد CSV"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/*"
                }
                startActivityForResult(intent, REQUEST_IMPORT)
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fileActions.addView(Button(this).apply {
            text = "تصدير CSV"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "student_grades.csv")
                }
                startActivityForResult(intent, REQUEST_EXPORT)
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(fileActions, match())
        page.addView(Button(this).apply {
            text = "حفظ البيانات على الهاتف"
            setOnClickListener {
                try {
                    persistLocalSession()
                    Toast.makeText(this@MainActivity, "تم حفظ البيانات محليًا على الهاتف", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "تعذر الحفظ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }, match())

        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        previousRecordButton = Button(this).apply {
            text = "السابق"
            setOnClickListener { moveRecord(-1) }
        }
        nextRecordButton = Button(this).apply {
            text = "التالي"
            setOnClickListener { moveRecord(1) }
        }
        recordPositionView = TextView(this).apply {
            text = "سجل يدوي"
            gravity = Gravity.CENTER
            textSize = 14f
        }
        navigation.addView(previousRecordButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        navigation.addView(recordPositionView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        navigation.addView(nextRecordButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(navigation, match())
        updateRecordNavigation()

        val calculate = Button(this).apply {
            text = "احسب النتيجة والإجمالي"
            setOnClickListener { calculate() }
        }
        page.addView(calculate, match())

        totalView = TextView(this).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        resultView = TextView(this).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
        page.addView(totalView, match())
        page.addView(resultView, match())
        stage.setSelection(0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQUEST_IMPORT -> importCsv(uri)
                REQUEST_EXPORT -> exportCsv(uri)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذرت معالجة الملف: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importCsv(uri: Uri) {
        val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalArgumentException("تعذر فتح الملف")
        val rows = StudentCsvCodec.decode(text)
        if (rows.isEmpty()) throw IllegalArgumentException("لا توجد سجلات في الملف")
        importedRows.clear()
        importedRows.addAll(rows)
        currentRecordIndex = 0
        loadRecordAtCurrentIndex()
        updateRecordNavigation()
        persistLocalSession()
        Toast.makeText(this, "تم استيراد ${rows.size} سجلًا. استخدم السابق/التالي للتنقل بينها.", Toast.LENGTH_LONG).show()
    }

    private fun applyImportedRow(row: StudentCsvCodec.StudentRow) {
        val targetPosition = when (row.stage) { "F8" -> 0; "S10" -> 1; "S11SCI" -> 2; else -> 0 }
        if (stage.selectedItemPosition != targetPosition) stage.setSelection(targetPosition)
        if (subjectInputs.size != row.subjects.size) rebuildSubjects()
        nameInput.setText(row.name)
        gender.setSelection(if (row.feminine) 1 else 0)
        subjectInputs.forEachIndexed { i, fields ->
            fields.forEachIndexed { c, field ->
                field.setText(row.subjects.getOrNull(i)?.getOrNull(c).orEmpty())
            }
        }
        calculate()
    }

    private fun exportCsv(uri: Uri) {
        val current = captureCurrentRow()
        val rowsToExport = if (currentRecordIndex in importedRows.indices) {
            importedRows[currentRecordIndex] = current
            importedRows.toList()
        } else listOf(current)
        val csv = StudentCsvCodec.encode(rowsToExport)
        contentResolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { it.write(csv) }
        } ?: throw IllegalArgumentException("تعذر إنشاء الملف")
        Toast.makeText(this, "تم تصدير ${rowsToExport.size} سجلًا بصيغة CSV", Toast.LENGTH_SHORT).show()
    }

    private fun captureCurrentRow(): StudentCsvCodec.StudentRow {
        val stageCode = when (stage.selectedItemPosition) { 0 -> "F8"; 1 -> "S10"; else -> "S11SCI" }
        return StudentCsvCodec.StudentRow(
            name = nameInput.text.toString(),
            feminine = gender.selectedItemPosition == 1,
            stage = stageCode,
            subjects = subjectInputs.map { fields -> fields.map { it.text.toString() } }
        )
    }

    private fun moveRecord(delta: Int) {
        if (importedRows.isEmpty()) return
        if (currentRecordIndex in importedRows.indices) importedRows[currentRecordIndex] = captureCurrentRow()
        val target = currentRecordIndex + delta
        if (target !in importedRows.indices) return
        currentRecordIndex = target
        loadRecordAtCurrentIndex()
        updateRecordNavigation()
        persistLocalSession()
    }

    private fun persistLocalSession() {
        if (!::nameInput.isInitialized || !::stage.isInitialized) return
        val current = captureCurrentRow()
        val rows = if (currentRecordIndex in importedRows.indices) {
            importedRows[currentRecordIndex] = current
            importedRows.toList()
        } else listOf(current)
        val csv = StudentCsvCodec.encode(rows)
        openFileOutput(LOCAL_SESSION_FILE, MODE_PRIVATE).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        getSharedPreferences(SESSION_PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_CURRENT_INDEX, if (currentRecordIndex in rows.indices) currentRecordIndex else 0)
            .putBoolean(KEY_HAS_IMPORTED_LIST, currentRecordIndex in importedRows.indices)
            .apply()
    }

    private fun restoreLocalSession() {
        val file = getFileStreamPath(LOCAL_SESSION_FILE)
        if (!file.exists() || file.length() == 0L) return
        try {
            val csv = openFileInput(LOCAL_SESSION_FILE).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rows = StudentCsvCodec.decode(csv)
            if (rows.isEmpty()) return
            val prefs = getSharedPreferences(SESSION_PREFS, MODE_PRIVATE)
            val wasImported = prefs.getBoolean(KEY_HAS_IMPORTED_LIST, false)
            importedRows.clear()
            if (wasImported) importedRows.addAll(rows)
            currentRecordIndex = if (wasImported) prefs.getInt(KEY_CURRENT_INDEX, 0).coerceIn(rows.indices) else -1
            if (wasImported) {
                loadRecordAtCurrentIndex()
            } else {
                applyImportedRow(rows.first())
            }
            updateRecordNavigation()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر استعادة الحفظ المحلي: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadRecordAtCurrentIndex() {
        val row = importedRows.getOrNull(currentRecordIndex) ?: return
        applyImportedRow(row)
    }

    private fun updateRecordNavigation() {
        if (!::recordPositionView.isInitialized) return
        val hasRecords = importedRows.isNotEmpty()
        recordPositionView.text = if (hasRecords) "${currentRecordIndex + 1} / ${importedRows.size}" else "سجل يدوي"
        previousRecordButton.isEnabled = hasRecords && currentRecordIndex > 0
        nextRecordButton.isEnabled = hasRecords && currentRecordIndex < importedRows.lastIndex
    }

    private fun rebuildSubjects() {
        if (!::subjectContainer.isInitialized) return
        val count = when (stage.selectedItemPosition) { 1 -> 12; 2 -> 9; else -> 8 }
        builtSubjectCount = count
        subjectInputs.clear()
        subjectTotalLabels.clear()
        subjectContainer.removeAllViews()
        repeat(count) { index ->
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            }
            panel.addView(TextView(this).apply {
                text = "المادة ${index + 1}"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            }, match())
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val cells = (1..4).map { cell ->
                EditText(this).apply {
                    hint = "خ$cell"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setSingleLine(true)
                }.also { row.addView(it, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    marginStart = dp(3); marginEnd = dp(3)
                }) }
            }
            panel.addView(row, match())
            val totalLabel = TextView(this).apply { text = "مجموع المادة: —"; textSize = 13f }
            panel.addView(totalLabel, match())
            subjectTotalLabels.add(totalLabel)
            subjectInputs.add(cells)
            subjectContainer.addView(panel, match())
        }
        totalView.text = ""
        resultView.text = ""
    }

    private fun calculate() {
        val rows = subjectInputs.map { fields -> fields.map { parseCell(it.text.toString()) } }
        val totals = rows.map { GradeRules.subjectTotal(it) }
        totals.forEachIndexed { index, total ->
            subjectTotalLabels[index].text = "مجموع المادة: ${total?.let(::formatNumber) ?: "غير مقررة / فارغة"}"
        }
        val overall = when (stage.selectedItemPosition) {
            0 -> GradeRules.sheetFFinalTotal(totals)
            1 -> GradeRules.grade10FinalTotal(totals)
            else -> GradeRules.grade11ScienceFinalTotal(totals)
        }
        val namePresent = nameInput.text.toString().isNotBlank()
        val result = when (stage.selectedItemPosition) {
            0 -> GradeRules.classifySheetFResult(namePresent, rows)
            1 -> GradeRules.classifySheetSResult(namePresent, rows, 12)
            else -> GradeRules.classifySheetSResult(namePresent, rows, 9)
        }
        totalView.text = "الإجمالي الكلي: ${overall?.let(::formatNumber) ?: "—"}"
        resultView.text = "النتيجة: ${GradeRules.resultLabel(result, gender.selectedItemPosition == 1) ?: "—"}"
    }

    private fun parseCell(text: String): Any? {
        val value = text.trim()
        if (value.isEmpty()) return null
        if (GradeRules.isAbsenceMarker(value)) return value
        return value.toDoubleOrNull() ?: value
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun match() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
