package com.example.wma

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SummaryActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var logoutButton: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        pieChart = findViewById(R.id.pieChart)
        logoutButton = findViewById(R.id.logoutButton)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        val userId = intent.getStringExtra("USER_ID") ?: return

        fetchUserSummary(userId)

        logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun fetchUserSummary(userId: String) {
        database.child("users").child(userId).get().addOnSuccessListener { snapshot ->
            val pieEntries = mutableListOf<PieEntry>()

            snapshot.children.forEach { record ->
                val products = record.child("products").children
                products.forEach { productSnapshot ->
                    val productName = productSnapshot.child("product").value.toString()
                    val price = productSnapshot.child("price").value.toString().toFloatOrNull() ?: 0f
                    pieEntries.add(PieEntry(price, productName))
                }
            }

            if (pieEntries.isNotEmpty()) {
                val dataSet = PieDataSet(pieEntries, "Spending Summary")
                dataSet.colors = listOf(
                    Color.parseColor("#e57373"),  // Light Red
                    Color.parseColor("#f06292"),  // Pink
                    Color.parseColor("#e74c3c"),  // Bright Red
                    Color.parseColor("#ff8a80"),  // Soft Red
                    Color.parseColor("#ff4081")   // Deep Pink
                )
                dataSet.sliceSpace = 3f
                dataSet.selectionShift = 5f

                val data = PieData(dataSet)
                data.setValueTextSize(12f)
                data.setValueTextColor(Color.WHITE)

                pieChart.data = data
                pieChart.description.isEnabled = false
                pieChart.setUsePercentValues(true)
                pieChart.isDrawHoleEnabled = true
                pieChart.setHoleColor(Color.TRANSPARENT)
                pieChart.setTransparentCircleAlpha(110)
                pieChart.setEntryLabelColor(Color.BLACK)
                pieChart.setEntryLabelTextSize(12f)

                pieChart.invalidate()
            } else {
                pieChart.clear()
            }
        }.addOnFailureListener {
            pieChart.clear()
        }
    }
}
