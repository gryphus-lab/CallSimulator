package ch.gryphus.callsimulator

import android.Manifest
import android.content.ContentUris
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
import ch.gryphus.callsimulator.databinding.ActivityMainBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Currently selected caller photo (gallery or contact), or null for the default avatar. */
    private var callerPhotoUri: Uri? = null

    /** Arms/cancels a future simulated call via AlarmManager. */
    private val scheduler by lazy { CallScheduler(this) }

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

        binding.scheduleCallButton.setOnClickListener { pickScheduleDateTime() }
        binding.cancelScheduleButton.setOnClickListener { cancelSchedule() }
    }

    override fun onResume() {
        super.onResume()
        refreshScheduleStatus()
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
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
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ),
                null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return
                val contactId = cursor.getLong(
                    cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                )
                val rawName = cursor.getStringOrNull(ContactsContract.Contacts.DISPLAY_NAME)
                val rawPhoto = cursor.getStringOrNull(ContactsContract.Contacts.PHOTO_URI)
                val rawThumbnail =
                    cursor.getStringOrNull(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                val hasPhoneFlag = cursor.getIntOrNull(
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                )
                val primaryNumber =
                    if (CallerDetails.hasPhoneNumber(hasPhoneFlag)) queryPrimaryNumber(contactId)
                    else null

                // The full-size "Calling Card" photo (DISPLAY_PHOTO), if the
                // contact has one and it can actually be opened. Preferred over
                // the smaller PHOTO_URI for the full-screen caller ID.
                val displayPhoto = resolveDisplayPhotoUri(contactId)

                // All decisions live in the pure CallerDetails helper (unit-tested).
                val fields = CallerDetails.fieldsFromContact(
                    displayName = rawName,
                    hasPhoneFlag = hasPhoneFlag,
                    primaryNumber = primaryNumber,
                    rawPhotoUri = rawPhoto,
                    displayPhotoUri = displayPhoto,
                    thumbnailUri = rawThumbnail,
                )
                fields.name?.let { binding.callerNameEdit.setText(it) }
                fields.number?.let { binding.callerNumberEdit.setText(it) }
                setCallerPhoto(fields.photo?.let(Uri::parse))
            }
        } catch (e: SecurityException) {
            Toast.makeText(
                this, getString(R.string.contacts_permission_denied), Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * The contact's full-size "Calling Card" display photo URI
     * (Contacts/<id>/display_photo), or null if the contact has no full-size
     * photo. We probe that the stream opens so a bad/absent display photo falls
     * back to PHOTO_URI instead of showing a broken image full-screen.
     */
    private fun resolveDisplayPhotoUri(contactId: Long): String? {
        val uri = Uri.withAppendedPath(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            ContactsContract.Contacts.Photo.DISPLAY_PHOTO
        )
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { uri.toString() }
        } catch (e: java.io.IOException) {
            null
        } catch (e: SecurityException) {
            null
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
        CallerDetails.resolveName(
            binding.callerNameEdit.text?.toString(),
            getString(R.string.default_caller_name)
        )

    private fun resolvedNumber(): String =
        CallerDetails.resolveNumber(
            binding.callerNumberEdit.text?.toString(),
            getString(R.string.default_caller_number)
        )

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

    // ---- Schedule a future call -------------------------------------------

    /** Date picker -> time picker -> arm the alarm for the chosen moment. */
    private fun pickScheduleDateTime() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_date_title)
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { utcDateMillis ->
            val now = Calendar.getInstance()
            val timePicker = MaterialTimePicker.Builder()
                .setTitleText(R.string.pick_time_title)
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(now.get(Calendar.HOUR_OF_DAY))
                .setMinute(now.get(Calendar.MINUTE))
                .build()

            timePicker.addOnPositiveButtonClickListener {
                armScheduleAt(utcDateMillis, timePicker.hour, timePicker.minute)
            }
            timePicker.show(supportFragmentManager, "schedule_time")
        }
        datePicker.show(supportFragmentManager, "schedule_date")
    }

    /**
     * Combine the UTC-midnight date from the date picker with the local
     * hour/minute into an absolute local trigger time, then arm it.
     */
    private fun armScheduleAt(utcDateMillis: Long, hour: Int, minute: Int) {
        // MaterialDatePicker returns UTC midnight; read its Y/M/D in UTC, then
        // rebuild the instant in the device's local zone with the chosen time.
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcDateMillis
        }
        val local = Calendar.getInstance().apply {
            clear()
            set(
                utc.get(Calendar.YEAR),
                utc.get(Calendar.MONTH),
                utc.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                0
            )
        }
        val triggerAt = local.timeInMillis
        val now = System.currentTimeMillis()

        if (!CallSchedule.isInFuture(triggerAt, now)) {
            Toast.makeText(this, getString(R.string.schedule_in_past), Toast.LENGTH_LONG).show()
            return
        }

        val call = ScheduledCall(
            name = resolvedName(),
            number = resolvedNumber(),
            photo = callerPhotoUri?.toString(),
            triggerAtMillis = triggerAt,
        )
        scheduler.schedule(call)

        val lead = CallSchedule.formatLeadTime(triggerAt, now)
        Toast.makeText(this, getString(R.string.schedule_set, lead), Toast.LENGTH_LONG).show()
        refreshScheduleStatus()
    }

    private fun cancelSchedule() {
        scheduler.cancel()
        Toast.makeText(this, getString(R.string.schedule_canceled), Toast.LENGTH_SHORT).show()
        refreshScheduleStatus()
    }

    /** Reflect any pending schedule in the status line + cancel button. */
    private fun refreshScheduleStatus() {
        val pending = scheduler.pending()
        if (pending == null || !CallSchedule.isInFuture(pending.triggerAtMillis, System.currentTimeMillis())) {
            // Elapsed or none: clear stale state.
            if (pending != null) scheduler.cancel()
            binding.scheduleStatusText.text = getString(R.string.schedule_none)
            binding.cancelScheduleButton.visibility = View.GONE
            return
        }
        val whenText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pending.triggerAtMillis))
        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(pending.triggerAtMillis))
        binding.scheduleStatusText.text =
            getString(R.string.schedule_pending, dateText, whenText)
        binding.cancelScheduleButton.visibility = View.VISIBLE
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
