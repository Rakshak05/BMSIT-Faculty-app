package com.bmsit.faculty

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

/**
 * Debug activity to display certificate fingerprints for Firebase configuration
 * This activity is for debugging purposes only and should not be included in production builds
 */
class CertificateDebugActivity : AppCompatActivity() {
    
    private lateinit var sha1TextView: TextView
    private lateinit var sha256TextView: TextView
    private lateinit var copySha1Button: Button
    private lateinit var copySha256Button: Button
    private lateinit var refreshButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_certificate_debug)
        
        sha1TextView = findViewById(R.id.sha1TextView)
        sha256TextView = findViewById(R.id.sha256TextView)
        copySha1Button = findViewById(R.id.copySha1Button)
        copySha256Button = findViewById(R.id.copySha256Button)
        refreshButton = findViewById(R.id.refreshButton)
        
        displayCertificateInfo()
        
        copySha1Button.setOnClickListener {
            copyToClipboard("SHA-1", sha1TextView.text.toString())
        }
        
        copySha256Button.setOnClickListener {
            copyToClipboard("SHA-256", sha256TextView.text.toString())
        }
        
        refreshButton.setOnClickListener {
            displayCertificateInfo()
        }
    }
    
    private fun displayCertificateInfo() {
        val sha1 = getCertificateSHA1Fingerprint()
        val sha256 = getCertificateSHA256Fingerprint()
        
        sha1TextView.text = sha1
        sha256TextView.text = sha256
        
        Log.d("CertificateDebug", "SHA-1: $sha1")
        Log.d("CertificateDebug", "SHA-256: $sha256")
    }
    
    private fun getCertificateSHA1Fingerprint(): String {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md = MessageDigest.getInstance("SHA1")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val hexString = StringBuilder()
                for (byte in digest) {
                    val hex = Integer.toHexString(0xFF and byte.toInt())
                    if (hex.length == 1) {
                        hexString.append('0')
                    }
                    hexString.append(hex)
                }
                return hexString.toString()
            }
        } catch (e: Exception) {
            Log.e("CertificateDebug", "Error getting certificate SHA1 fingerprint", e)
        }
        return "Error retrieving SHA-1"
    }

    private fun getCertificateSHA256Fingerprint(): String {
        try {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            for (signature in info.signatures) {
                val md = MessageDigest.getInstance("SHA256")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val hexString = StringBuilder()
                for (byte in digest) {
                    val hex = Integer.toHexString(0xFF and byte.toInt())
                    if (hex.length == 1) {
                        hexString.append('0')
                    }
                    hexString.append(hex)
                }
                return hexString.toString()
            }
        } catch (e: Exception) {
            Log.e("CertificateDebug", "Error getting certificate SHA256 fingerprint", e)
        }
        return "Error retrieving SHA-256"
    }
    
    private fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("CertificateDebug", "Error copying to clipboard", e)
            Toast.makeText(this, "Failed to copy $label to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}