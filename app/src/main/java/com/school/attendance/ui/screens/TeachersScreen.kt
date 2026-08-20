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
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Switch
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
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Repository
import com.school.attendance.data.Teacher
import com.school.attendance.util.CsvExport
import com.school.attendance.util.CsvImport
import com.school.attendance.util.PhotoUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeachersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val teachers = repo.teachers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val buses = repo.buses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(t: Teacher) = viewModelScope.launch { repo.upsertTeacher(t) }
    fun delete(t: Teacher) = viewModelScope.launch { repo.deleteTeacher(t) }

    suspend fun importRows(rows: List<List<String>>): ImportResult {
        var added = 0
        rows.drop(1).forEach { r ->
            val name = r.getOrElse(0) { "" }.trim()
            if (name.isBlank()) return@forEach
            repo.upsertTeacher(Teacher(
                name = name, phone = r.getOrElse(1) { "" }.trim(), designation = r.getOrElse(2) { "" }.trim(),
                isTeachingStaff = !r.getOrElse(3) { "Yes" }.trim().equals("No", true),
                isAdmin = r.getOrElse(4) { "No" }.trim().equals("Yes", true),
                monthlySalary = r.getOrElse(5) { "" }.trim().toDoubleOrNull() ?: 0.0
            ))
            added++
        }
        return ImportResult(added, 0, emptyList())
    }
}

private val TEACHER_CSV_HEADER = listOf("Name", "Phone", "Designation", "TeachingStaff(Yes/No)", "Admin(Yes/No)", "MonthlySalary")
private val TEACHER_CSV_SAMPLE = listOf(
    listOf("Priya Nair", "9876543210", "Class Teacher", "Yes", "No", "25000"),
    listOf("Anil Menon", "9876543211", "PE Teacher", "Yes", "No", "22000"),
    listOf("Ravi Das", "9876543212", "Office Clerk", "No", "No", "15000")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachersScreen(onBack: () -> Unit, vm: TeachersViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val teachers by vm.teachers.collectAsState()
    var edit by remember { mutableStateOf<Teacher?>(null) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch { importResult = vm.importRows(CsvImport.readUri(context, uri)) }
    }

    val buses by vm.buses.collectAsState()
    edit?.let { t -> TeacherDialog(t, buses, { vm.save(it); edit = null }, { edit = null }, { vm.delete(it); edit = null }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teachers & Staff") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { CsvExport.export(context, "staff-sample.csv", TEACHER_CSV_HEADER, TEACHER_CSV_SAMPLE) }) { Icon(Icons.Filled.Download, "Download sample CSV") }
                    IconButton(onClick = { importLauncher.launch("text/*") }) { Icon(Icons.Filled.Upload, "Import from CSV") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { edit = Teacher(name = "") }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            importResult?.let { r ->
                Text("Import: added ${r.added}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(12.dp))
                HorizontalDivider()
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(teachers, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = { Text(t.name + if (t.isAdmin) " (Admin)" else "") },
                        supportingContent = {
                            Text(listOfNotNull(
                                t.designation.ifBlank { null }, t.phone.ifBlank { null },
                                if (!t.isTeachingStaff) "Non-teaching" else null
                            ).joinToString("  ·  "))
                        },
                        modifier = Modifier.fillMaxWidth().clickable { edit = t }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeacherDialog(initial: Teacher, buses: List<com.school.attendance.data.Bus>, onSave: (Teacher) -> Unit, onDismiss: () -> Unit, onDelete: (Teacher) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var designation by remember { mutableStateOf(initial.designation) }
    var designationMenu by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(initial.pin) }
    var salary by remember { mutableStateOf(if (initial.monthlySalary > 0) initial.monthlySalary.toString() else "") }
    var isAdmin by remember { mutableStateOf(initial.isAdmin) }
    var isTeaching by remember { mutableStateOf(initial.isTeachingStaff) }
    var canSelfMark by remember { mutableStateOf(initial.canSelfMarkAttendance) }
    var canViewBus by remember { mutableStateOf(initial.canViewBusLocation) }
    var busId by remember { mutableStateOf(initial.busId) }
    var busMenu by remember { mutableStateOf(false) }
    val isDriver = designation.equals("Driver", ignoreCase = true)
    var photoPath by remember { mutableStateOf(initial.photoPath) }
    var aadhar by remember { mutableStateOf(initial.aadharNumber) }
    var bloodGroup by remember { mutableStateOf(initial.bloodGroup) }
    var religion by remember { mutableStateOf(initial.religion) }
    var secondMobile by remember { mutableStateOf(initial.secondMobile) }
    var email by remember { mutableStateOf(initial.email) }
    var permanentAddress by remember { mutableStateOf(initial.permanentAddress) }
    var showMore by remember { mutableStateOf(false) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) PhotoUtil.importCompressed(context, uri, "teacher_${initial.id}_${System.currentTimeMillis()}")?.let { photoPath = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New teacher / staff" else "Edit teacher / staff") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() } }, label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = designation, onValueChange = {}, readOnly = true, label = { Text("Designation") },
                        trailingIcon = { IconButton(onClick = { designationMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = designationMenu, onDismissRequest = { designationMenu = false }) {
                        prefs.designations.forEach { d -> DropdownMenuItem(text = { Text(d) }, onClick = { designation = d; designationMenu = false }) }
                    }
                }
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Login PIN (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = salary, onValueChange = { salary = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Monthly salary (for payroll)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Teaching staff", Modifier.weight(1f))
                    Switch(checked = isTeaching, onCheckedChange = { isTeaching = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Admin access", Modifier.weight(1f))
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Can self-mark attendance (geo-fenced)", Modifier.weight(1f))
                    Checkbox(checked = canSelfMark, onCheckedChange = { canSelfMark = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Can view bus location", Modifier.weight(1f))
                    Checkbox(checked = canViewBus, onCheckedChange = { canViewBus = it })
                }
                if (isDriver) {
                    Box(Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(value = buses.firstOrNull { it.id == busId }?.busNumber ?: "", onValueChange = {}, readOnly = true, label = { Text("Assigned bus") },
                            trailingIcon = { IconButton(onClick = { busMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = busMenu, onDismissRequest = { busMenu = false }) {
                            buses.forEach { b -> DropdownMenuItem(text = { Text(b.busNumber) }, onClick = { busId = b.id; busMenu = false }) }
                        }
                    }
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
                    OutlinedTextField(value = aadhar, onValueChange = { aadhar = it.filter { c -> c.isDigit() }.take(12) }, label = { Text("Aadhaar number") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = bloodGroup, onValueChange = { bloodGroup = it }, label = { Text("Blood group") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = religion, onValueChange = { religion = it }, label = { Text("Religion") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = secondMobile, onValueChange = { secondMobile = it.filter { c -> c.isDigit() } }, label = { Text("Second mobile (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedTextField(value = permanentAddress, onValueChange = { permanentAddress = it }, label = { Text("Permanent address") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    prefs.addDesignation(designation)
                    onSave(initial.copy(
                        name = name.trim(), phone = phone.trim(), designation = designation.trim(), pin = pin.trim(),
                        monthlySalary = salary.toDoubleOrNull() ?: 0.0, isAdmin = isAdmin, isTeachingStaff = isTeaching,
                        canSelfMarkAttendance = canSelfMark, canViewBusLocation = canViewBus, busId = if (isDriver) busId else 0L,
                        photoPath = photoPath, aadharNumber = aadhar.trim(), bloodGroup = bloodGroup.trim(), religion = religion.trim(),
                        secondMobile = secondMobile.trim(), email = email.trim(), permanentAddress = permanentAddress.trim()
                    ))
                }
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
