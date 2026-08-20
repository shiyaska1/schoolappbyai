package com.school.attendance.ui.screens

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Division
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import com.school.attendance.util.CsvExport
import com.school.attendance.util.CsvImport
import com.school.attendance.util.PhotoUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportResult(val added: Int, val skipped: Int, val skippedReasons: List<String>)

class StudentsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val students = repo.students.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val courses = repo.courses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val buses = repo.buses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(s: Student) = viewModelScope.launch {
        val withCreds = if (s.username.isBlank()) s.copy(
            username = "${s.name.filter { it.isLetter() }.take(6).lowercase()}${(1000..9999).random()}",
            password = (100000..999999).random().toString()
        ) else s
        repo.upsertStudent(withCreds)
    }
    fun delete(s: Student) = viewModelScope.launch { repo.deleteStudent(s) }

    suspend fun importRows(rows: List<List<String>>): ImportResult {
        val divs = divisions.value
        var added = 0
        val skipped = mutableListOf<String>()
        rows.drop(1).forEach { r ->
            val name = r.getOrElse(0) { "" }.trim()
            if (name.isBlank()) return@forEach
            val divisionName = r.getOrElse(2) { "" }.trim()
            val division = divs.firstOrNull { it.name.equals(divisionName, true) }
            if (division == null) { skipped += "$name (unknown division \"$divisionName\")"; return@forEach }
            repo.upsertStudent(Student(
                name = name, rollNumber = r.getOrElse(1) { "" }.trim(), divisionId = division.id,
                gender = r.getOrElse(3) { "" }.trim(), guardianName = r.getOrElse(4) { "" }.trim(),
                guardianPhone = r.getOrElse(5) { "" }.trim(), guardianWhatsapp = r.getOrElse(6) { "" }.trim(),
                address = r.getOrElse(7) { "" }.trim()
            ))
            added++
        }
        return ImportResult(added, skipped.size, skipped)
    }
}

private val STUDENT_CSV_HEADER = listOf("Name", "RollNumber", "Division", "Gender", "GuardianName", "GuardianPhone", "GuardianWhatsapp", "Address")
private val STUDENT_CSV_SAMPLE = listOf(
    listOf("Aisha Rahman", "12", "A", "Female", "Mohammed Rahman", "9812345670", "919812345670", "12 MG Road"),
    listOf("Rohit Kumar", "13", "A", "Male", "Suresh Kumar", "9812345671", "919812345671", "45 Park Street"),
    listOf("Sara Thomas", "5", "B", "Female", "Thomas John", "9812345672", "", "8 Church Lane")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(onBack: () -> Unit, vm: StudentsViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val students by vm.students.collectAsState()
    val divisions by vm.divisions.collectAsState()
    val courses by vm.courses.collectAsState()
    val buses by vm.buses.collectAsState()
    var edit by remember { mutableStateOf<Student?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    val divisionNames = remember(divisions) { divisions.associate { it.id to it.name } }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { importResult = vm.importRows(CsvImport.readUri(context, uri)) }
    }

    edit?.let { s -> StudentDialog(s, divisions, courses, buses, { vm.save(it); edit = null }, { edit = null }, { vm.delete(it); edit = null }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Students") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { CsvExport.export(context, "students-sample.csv", STUDENT_CSV_HEADER, STUDENT_CSV_SAMPLE) }) { Icon(Icons.Filled.Download, "Download sample CSV") }
                    IconButton(onClick = { importLauncher.launch("text/*") }) { Icon(Icons.Filled.Upload, "Import from CSV") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { edit = Student(name = "") }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            importResult?.let { r ->
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Import: added ${r.added}, skipped ${r.skipped}.", style = MaterialTheme.typography.bodyMedium)
                    if (r.skippedReasons.isNotEmpty()) Text(r.skippedReasons.joinToString("; "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(students, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(listOfNotNull(s.rollNumber.ifBlank { null }, divisionNames[s.divisionId]).joinToString("  ·  ")) },
                        modifier = Modifier.fillMaxWidth().clickable { edit = s }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDialog(initial: Student, divisions: List<Division>, courses: List<com.school.attendance.data.Course>, buses: List<com.school.attendance.data.Bus>, onSave: (Student) -> Unit, onDismiss: () -> Unit, onDelete: (Student) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var roll by remember { mutableStateOf(initial.rollNumber) }
    var courseId by remember { mutableStateOf(divisions.firstOrNull { it.id == initial.divisionId }?.courseId ?: 0L) }
    var courseMenu by remember { mutableStateOf(false) }
    var divisionId by remember { mutableStateOf(initial.divisionId) }
    var divMenu by remember { mutableStateOf(false) }
    var busId by remember { mutableStateOf(initial.busId) }
    var busMenu by remember { mutableStateOf(false) }
    var guardianName by remember { mutableStateOf(initial.guardianName) }
    var guardianPhone by remember { mutableStateOf(initial.guardianPhone) }
    var guardianWhatsapp by remember { mutableStateOf(initial.guardianWhatsapp) }
    var gender by remember { mutableStateOf(initial.gender) }
    var genderMenu by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(initial.address) }
    var photoPath by remember { mutableStateOf(initial.photoPath) }
    var aadhar by remember { mutableStateOf(initial.aadharNumber) }
    var bloodGroup by remember { mutableStateOf(initial.bloodGroup) }
    var religion by remember { mutableStateOf(initial.religion) }
    var secondMobile by remember { mutableStateOf(initial.secondMobile) }
    var email by remember { mutableStateOf(initial.email) }
    var permanentAddress by remember { mutableStateOf(initial.permanentAddress) }
    var admissionNumber by remember { mutableStateOf(initial.admissionNumber) }
    var showMore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val divisionsInCourse = if (courseId == 0L) divisions else divisions.filter { it.courseId == courseId }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) PhotoUtil.importCompressed(context, uri, "student_${initial.id}_${System.currentTimeMillis()}")?.let { photoPath = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New student" else "Edit student") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = roll, onValueChange = { roll = it }, label = { Text("Roll number") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = courses.firstOrNull { it.id == courseId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Course") },
                        trailingIcon = { IconButton(onClick = { courseMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                        courses.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { courseId = c.id; divisionId = 0L; courseMenu = false }) }
                    }
                }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = divisions.firstOrNull { it.id == divisionId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Division") }, enabled = courseId != 0L,
                        trailingIcon = { IconButton(onClick = { divMenu = true }, enabled = courseId != 0L) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                        divisionsInCourse.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; divMenu = false }) }
                    }
                }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = gender, onValueChange = {}, readOnly = true, label = { Text("Gender") },
                        trailingIcon = { IconButton(onClick = { genderMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = genderMenu, onDismissRequest = { genderMenu = false }) {
                        listOf("Male", "Female", "Other").forEach { g -> DropdownMenuItem(text = { Text(g) }, onClick = { gender = g; genderMenu = false }) }
                    }
                }
                OutlinedTextField(value = guardianName, onValueChange = { guardianName = it }, label = { Text("Guardian name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = guardianPhone, onValueChange = { guardianPhone = it.filter { c -> c.isDigit() } }, label = { Text("Guardian phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = guardianWhatsapp, onValueChange = { guardianWhatsapp = it.filter { c -> c.isDigit() } }, label = { Text("Guardian WhatsApp (with country code)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = buses.firstOrNull { it.id == busId }?.busNumber ?: "None", onValueChange = {}, readOnly = true, label = { Text("School bus (optional)") },
                        trailingIcon = { IconButton(onClick = { busMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = busMenu, onDismissRequest = { busMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { busId = 0L; busMenu = false })
                        buses.forEach { b -> DropdownMenuItem(text = { Text(b.busNumber) }, onClick = { busId = b.id; busMenu = false }) }
                    }
                }
                if (initial.username.isNotBlank()) {
                    Text("Login: ${initial.username} / ${initial.password}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }

                TextButton(onClick = { showMore = !showMore }, modifier = Modifier.padding(top = 8.dp)) { Text(if (showMore) "Hide more details" else "More details (photo, Aadhaar, blood group...)") }
                if (showMore) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (photoPath.isNotBlank()) {
                            val bmp = remember(photoPath) { android.graphics.BitmapFactory.decodeFile(photoPath)?.asImageBitmap() }
                            bmp?.let { Image(it, "Photo", modifier = Modifier.size(56.dp)) }
                        }
                        TextButton(onClick = { photoLauncher.launch("image/*") }) { Text(if (photoPath.isBlank()) "Add photo" else "Change photo") }
                    }
                    OutlinedTextField(value = admissionNumber, onValueChange = { admissionNumber = it }, label = { Text("Admission number") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = aadhar, onValueChange = { aadhar = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Aadhaar number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = bloodGroup, onValueChange = { bloodGroup = it }, label = { Text("Blood group") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = religion, onValueChange = { religion = it }, label = { Text("Religion") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = secondMobile, onValueChange = { secondMobile = it.filter { c -> c.isDigit() } }, label = { Text("Second mobile (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = permanentAddress, onValueChange = { permanentAddress = it }, label = { Text("Permanent address (if different)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(initial.copy(
                    name = name.trim(), rollNumber = roll.trim(), divisionId = divisionId, gender = gender.trim(),
                    guardianName = guardianName.trim(), guardianPhone = guardianPhone.trim(), guardianWhatsapp = guardianWhatsapp.trim(),
                    address = address.trim(), busId = busId, photoPath = photoPath,
                    aadharNumber = aadhar.trim(), bloodGroup = bloodGroup.trim(), religion = religion.trim(),
                    secondMobile = secondMobile.trim(), email = email.trim(), permanentAddress = permanentAddress.trim(),
                    admissionNumber = admissionNumber.trim()
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial.id != 0L) TextButton(onClick = { onDelete(initial) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
