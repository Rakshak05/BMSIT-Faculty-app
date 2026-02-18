package com.bmsit.faculty

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TestActivity", "TestActivity onCreate called")
        
        val textView = TextView(this)
        textView.text = "Test Activity Loaded Successfully"
        setContentView(textView)
        
        Log.d("TestActivity", "TestActivity UI set successfully")
    }
}