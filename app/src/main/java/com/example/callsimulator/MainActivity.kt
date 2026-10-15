package com.example.callsimulator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.callsimulator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Currently selected caller photo (gallery or contact), or null for the default avatar. */
    private var callerPhotoUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(
                this,
                "Some permissions were denied. Full-screen call UI may be limited.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Modern Android Photo Picker - no storage permission required.
    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Persist read access so the call activities can load it later.
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            setCallerPhoto(uri)
        }
    }

    // System contact picker. Reading the result's number/photo needs READ_CONTACTS.
    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) loadContact(contactUri)
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pickContactLauncher.launch(null)
        } else {
            Toast.makeText(
                this, getString(R.string.contacts_permission_denied), Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNeededPermissions()

        binding.pickPhotoButton.setOnClickListener {
            pickPhotoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.pickContactButton.setOnClickListener { pickContact() }

        binding.clearPhotoButton.setOnClickListener { setCallerPhoto(null) }

        binding.startIncomingButton.setOnClickListener { startIncomingCall() }
        binding.startOutgoingButton.setOnClickListener { startOutgoingCall() }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun pickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pickContactLauncher.launch(null)
        } else {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    /** Resolve a picked contact's display name, primary number, and photo URI. */
    private fun loadContact(contactUri: Uri) {
        try {
            contentResolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ),
                null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return
                val contactId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                )
                val name = cursor.getStringOrNull(ContactsContract.Contacts.DISPLAY_NAME)
                val photo = cursor.getStringOrNull(ContactsContract.Contacts.PHOTO_URI)
                val hasPhone = cursor.getIntOrNull(
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                ) ?: 0

                if (!name.isNullOrBlank()) binding.callerNameEdit.setText(name)
                if (hasPhone > 0) queryPrimaryNumber(contactId)?.let {
                    binding.callerNumberEdit.setText(it)
                }
                setCallerPhoto(photo?.let(Uri::parse))
            }
        } catch (e: SecurityException) {
            Toast.makeText(
                this, getString(R.string.contacts_permission_denied), Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun queryPrimaryNumber(contactId: Long): String? {
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                return c.getStringOrNull(ContactsContract.CommonDataKinds.Phone.NUMBER)
            }
        }
        return null
    }

    private fun setCallerPhoto(uri: Uri?) {
        callerPhotoUri = uri
        if (uri != null) {
            binding.callerPhotoPreview.setImageURI(uri)
            binding.clearPhotoButton.visibility = View.VISIBLE
        } else {
            binding.callerPhotoPreview.setImageResource(R.drawable.ic_person)
            binding.clearPhotoButton.visibility = View.GONE
        }
    }

    private fun resolvedName(): String =
        binding.callerNameEdit.text?.toString()?.trim().orEmpty()
            .ifEmpty { getString(R.string.default_caller_name) }

    private fun resolvedNumber(): String =
        binding.callerNumberEdit.text?.toString()?.trim().orEmpty()
            .ifEmpty { getString(R.string.default_caller_number) }

    private fun startIncomingCall() {
        val name = resolvedName()
        val number = resolvedNumber()
        val photo = callerPhotoUri?.toString()

        // Start foreground service for realistic notification + wake
        val serviceIntent = Intent(this, CallSimulationService::class.java).apply {
            action = CallSimulationService.ACTION_INCOMING
            putExtra(CallSimulationService.EXTRA_CALLER_NAME, name)
            putExtra(CallSimulationService.EXTRA_CALLER_NUMBER, number)
            putExtra(CallSimulationService.EXTRA_CALLER_PHOTO, photo)
        }
        ContextCompat.startForegroundService(this, serviceIntent)

        // Also launch full-screen activity immediately
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, name)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, number)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHOTO, photo)
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun startOutgoingCall() {
        val name = resolvedName()
        val number = resolvedNumber()
        val photo = callerPhotoUri?.toString()

        val intent = Intent(this, InCallActivity::class.java).apply {
            putExtra(InCallActivity.EXTRA_CALLER_NAME, name)
            putExtra(InCallActivity.EXTRA_CALLER_NUMBER, number)
            putExtra(InCallActivity.EXTRA_CALLER_PHOTO, photo)
            putExtra(InCallActivity.EXTRA_IS_OUTGOING, true)
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }
}

/** Null-safe cursor column readers. */
private fun android.database.Cursor.getStringOrNull(column: String): String? {
    val idx = getColumnIndex(column)
    return if (idx >= 0 && !isNull(idx)) getString(idx) else null
}

private fun android.database.Cursor.getIntOrNull(column: String): Int? {
    val idx = getColumnIndex(column)
    return if (idx >= 0 && !isNull(idx)) getInt(idx) else null
}
