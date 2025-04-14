package com.example.wma

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import java.io.File
import java.util.*

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var usersCountText: TextView
    private lateinit var reportButton: Button
    private lateinit var datePickerButton: Button
    private lateinit var userListLayout: LinearLayout
    private lateinit var logoutButton: Button
    private lateinit var sendEmailButton: Button
    private lateinit var reportStatusText: TextView
    private lateinit var database: DatabaseReference
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val adminUid = auth.currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        // UI references
        usersCountText = findViewById(R.id.usersCountText)
        reportButton = findViewById(R.id.reportButton)
        datePickerButton = findViewById(R.id.datePickerButton)
        userListLayout = findViewById(R.id.userListLayout)
        logoutButton = findViewById(R.id.logoutButton)
        sendEmailButton = findViewById(R.id.sendEmailButton)
        reportStatusText = findViewById(R.id.reportStatusText)

        database = FirebaseDatabase.getInstance().reference

        // Request permission to save files
        checkAndRequestStoragePermission()

        // Load the existing users' info
        loadUsers()

        // Show date picker (4-digit year displayed in button)
        datePickerButton.setOnClickListener { showDatePicker() }

        // Generate PDF report for the date in button
        reportButton.setOnClickListener { generateReport() }

        // Send the PDF that was created
        sendEmailButton.setOnClickListener { sendEmailWithPDF() }

        // Logout logic
        logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadUsers() {
        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userCount = snapshot.childrenCount
                usersCountText.text = "Users: $userCount"

                userListLayout.removeAllViews()
                snapshot.children.forEach { userSnapshot ->
                    val userName = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                    val userView = TextView(this@AdminDashboardActivity).apply {
                        text = userName
                        textSize = 16f
                        setPadding(8, 8, 8, 8)
                    }
                    userListLayout.addView(userView)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminDashboardActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                // This sets a full 4-digit year in your button
                val date = "$day/${month + 1}/$year"
                // Update the button text so we see the full year
                datePickerButton.text = date
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Generate PDF for the date displayed on datePickerButton.
     */
    private fun generateReport() {
        // If user hasn't chosen a date, skip
        val dateFromButton = datePickerButton.text.toString()
        if (dateFromButton.isBlank() || !dateFromButton.contains("/")) {
            Toast.makeText(this, "Please pick a valid date first", Toast.LENGTH_SHORT).show()
            return
        }

        // Now fetch from DB
        database.child("users").get().addOnSuccessListener { snapshot ->
            val reportData = mutableListOf<Map<String, Any>>()

            snapshot.children.forEach { userSnapshot ->
                val userName = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"

                // Each child is presumably a record with "date"
                userSnapshot.children.forEach { dataSnapshot ->
                    try {
                        val data = dataSnapshot.value as? Map<String, Any>
                        if (data != null && (data["date"] == dateFromButton)) {
                            val modData = mutableMapOf<String, Any>("user" to userName)

                            // If products exist
                            if (data.containsKey("products")) {
                                val products = data["products"] as? List<Map<String, Any>> ?: emptyList()
                                modData["products"] = products
                            }

                            modData.putAll(data)
                            reportData.add(modData)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Generate PDF if data found
            if (reportData.isNotEmpty()) {
                val pdfFile = generatePDF(dateFromButton, reportData)
                sendEmailButton.visibility = Button.VISIBLE
                reportStatusText.text = "Report Generated: ${pdfFile.name}"
                reportStatusText.visibility = TextView.VISIBLE
            } else {
                Toast.makeText(this, "No data available for $dateFromButton", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error Fetching Data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generatePDF(date: String, data: List<Map<String, Any>>): File {
        val sanitizedDate = date.replace("/", "-")  // e.g. "31/1/2025" -> "31-1-2025"

        val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Reports")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "Report_$sanitizedDate.pdf")

        val pdfWriter = PdfWriter(file)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)

        document.add(Paragraph("Report for $date")) // Show slash-based date inside PDF

        data.forEach { entry ->
            document.add(Paragraph("User: ${entry["user"]}"))

            if (entry.containsKey("products")) {
                val products = entry["products"] as List<Map<String, Any>>
                products.forEachIndexed { index, product ->
                    val productName = product["product"] ?: "N/A"
                    val price = product["price"] ?: "N/A"
                    val reason = product["reason"] ?: "N/A"
                    document.add(Paragraph("Product ${index + 1}: $productName, Price: $price, Reason: $reason"))
                }
            }

            entry.forEach { (key, value) ->
                if (key != "user" && key != "products") {
                    document.add(Paragraph("$key: $value"))
                }
            }
            document.add(Paragraph("\n"))
        }

        document.close()
        return file
    }

    private fun sendEmailWithPDF() {
        val dateFromButton = datePickerButton.text.toString()
        if (dateFromButton.isBlank() || !dateFromButton.contains("/")) {
            Toast.makeText(this, "No PDF found. Pick a valid date first.", Toast.LENGTH_SHORT).show()
            return
        }

        // Must match generatePDF's sanitized name
        val sanitizedDate = dateFromButton.replace("/", "-")
        val pdfFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Reports/Report_$sanitizedDate.pdf")

        if (!pdfFile.exists()) {
            Toast.makeText(this, "No PDF found. Generate a report first.", Toast.LENGTH_SHORT).show()
            return
        }

        val fileUri = FileProvider.getUriForFile(this, "$packageName.provider", pdfFile)
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Report for $dateFromButton")
            putExtra(Intent.EXTRA_TEXT, "Find the report for $dateFromButton attached.")
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(emailIntent, "Send email using:"))
    }

    private fun checkAndRequestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
    }
}
