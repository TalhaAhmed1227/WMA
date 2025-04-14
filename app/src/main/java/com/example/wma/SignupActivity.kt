package com.example.wma

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()

        val nameField = findViewById<EditText>(R.id.nameField)
        val emailField = findViewById<EditText>(R.id.emailField)
        val passwordField = findViewById<EditText>(R.id.passwordField)
        val adminCheckBox = findViewById<CheckBox>(R.id.adminCheckBox)
        val signUpButton = findViewById<Button>(R.id.signUpButton)

        signUpButton.setOnClickListener {
            val name = nameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            val isAdmin = adminCheckBox.isChecked

            if (password.length >= 6 && password.any { it.isDigit() }) {
                auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {
                    if (it.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                        saveUserToDatabase(userId, name, email, isAdmin)
                        Toast.makeText(this, "Sign Up Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Sign Up Failed: ${it.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Password must be at least 6 characters and contain a number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserToDatabase(userId: String, name: String, email: String, isAdmin: Boolean) {
        val database = FirebaseDatabase.getInstance().reference

        val user = mapOf(
            "name" to name,
            "email" to email
        )

        // Save user under "users"
        database.child("users").child(userId).setValue(user)

        // If user is an admin, also save under "admins"
        if (isAdmin) {
            database.child("admins").child(userId).setValue(true)
        }
    }
}
