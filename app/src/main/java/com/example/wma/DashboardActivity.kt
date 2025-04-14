package com.example.wma

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.android.gms.tasks.Task
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var userNameText: TextView
    private lateinit var datePickerButton: Button
    private lateinit var timePickerButton: Button
    private lateinit var addTableButton: ImageButton
    private lateinit var submitButton: Button
    private lateinit var tableLayout: LinearLayout

    private val tables = mutableListOf<View>()
    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()

        userNameText = findViewById(R.id.userNameText)
        datePickerButton = findViewById(R.id.datePickerButton)
        timePickerButton = findViewById(R.id.timePickerButton)
        addTableButton = findViewById(R.id.addTableButton)
        submitButton = findViewById(R.id.submitButton)
        tableLayout = findViewById(R.id.tableLayout)

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val userEmail = currentUser.email ?: "User"
        val userName = userEmail.split("@")[0] // Extract username from email
        userNameText.text = "Welcome, $userName"

        // Date Picker
        datePickerButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                datePickerButton.text = "$day/${month + 1}/$year"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Time Picker
        timePickerButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                timePickerButton.text = String.format("%02d:%02d", hour, minute)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        // Add Table Row
        addTableButton.setOnClickListener {
            val tableView = layoutInflater.inflate(R.layout.table_row, null)
            tableLayout.addView(tableView)
            tables.add(tableView)
        }

        // Submit Data
        submitButton.setOnClickListener {
            val date = datePickerButton.text.toString()
            val time = timePickerButton.text.toString()

            if (date == "Select Date" || time == "Select Time") {
                Toast.makeText(this, "Please select a valid date and time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tableData = tables.map { table: View ->
                val product = table.findViewById<EditText>(R.id.productField).text.toString()
                val reason = table.findViewById<EditText>(R.id.reasonField).text.toString()
                val price = table.findViewById<EditText>(R.id.priceField).text.toString()
                mapOf("product" to product, "reason" to reason, "price" to price)
            }

            val data = mapOf(
                "user" to userName,
                "date" to date,
                "time" to time,
                "products" to tableData
            )

            database.child("users").child(currentUser.uid).push().setValue(data)
                .addOnCompleteListener { task: Task<Void> ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Data Submitted", Toast.LENGTH_SHORT).show()

                        // ✅ Move to SummaryActivity and pass user ID
                        val intent = Intent(this, SummaryActivity::class.java)
                        intent.putExtra("USER_ID", currentUser.uid)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to Submit Data: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
