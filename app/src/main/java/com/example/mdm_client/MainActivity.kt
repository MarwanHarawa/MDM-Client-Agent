package com.example.mdm_client

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName
    private val db = Firebase.firestore
    private lateinit var tvStatus: TextView
    private lateinit var tvToken: TextView
    private lateinit var btnActivateAdmin: Button
    private var fcmToken: String? = null

    // Location variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = 30000
        fastestInterval = 15000
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    private lateinit var locationCallback: LocationCallback

    // Battery
    private var batteryLevel: Int = -1
    private lateinit var batteryReceiver: BroadcastReceiver

    // Permissions
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.QUERY_ALL_PACKAGES
    )
    private val PERMISSIONS_REQUEST_CODE = 99

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        tvStatus = findViewById(R.id.tvStatus)
        tvToken = findViewById(R.id.tvToken)
        btnActivateAdmin = findViewById(R.id.btnActivateAdmin)

        btnActivateAdmin.setOnClickListener { activateDeviceAdmin() }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (checkPermissions()) {
            initialize()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initialize()
        }
    }

    private fun initialize() {
        updateUI()

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fcmToken = task.result
                tvToken.text = "FCM Token: ${fcmToken?.take(20)}..."

                val deviceName = Build.MODEL ?: "Unknown"
                val osVersion = Build.VERSION.RELEASE ?: "Unknown"

                saveDeviceDataToFirestore(fcmToken!!, deviceName, osVersion)

                getInstalledApps()
                createLocationCallback()
                startLocationUpdates()
                registerBatteryReceiver()
            } else {
                Log.e("FCM", "Token fetch failed: ${task.exception}")
            }
        }
    }

    private fun saveDeviceDataToFirestore(token: String, deviceName: String, osVersion: String) {
        val data = hashMapOf(
            "token" to token,
            "deviceName" to deviceName,
            "osVersion" to osVersion,
            "isOnline" to true,
            "lastOnline" to Timestamp.now(),
            "batteryLevel" to batteryLevel,
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "sdkVersion" to Build.VERSION.SDK_INT
        )

        db.collection("devices")
            .whereEqualTo("token", token)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    db.collection("devices").add(data)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Device added: ${it.id}")
                        }
                } else {
                    for (document in documents) {
                        db.collection("devices").document(document.id).update(data as Map<String, Any>)
                    }
                }
            }
    }

    private fun getInstalledApps() {
        fcmToken?.let { token ->
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val installedApps = ArrayList<HashMap<String, String>>()

            for (app in apps) {
                try {
                    val packageInfo = pm.getPackageInfo(app.packageName, 0)
                    val appInfo = HashMap<String, String>()
                    appInfo["packageName"] = app.packageName
                    appInfo["appName"] = app.loadLabel(pm).toString()
                    appInfo["version"] = packageInfo.versionName ?: ""
                    appInfo["installDate"] = Date(packageInfo.firstInstallTime).toString()
                    installedApps.add(appInfo)
                } catch (e: Exception) {
                    Log.e("Apps", "Error: ${e.message}")
                }
            }

            db.collection("devices")
                .whereEqualTo("token", token)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        db.collection("devices").document(document.id)
                            .update("installedApps", installedApps)
                    }
                }
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    updateLocationInFirestore(it)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun updateLocationInFirestore(location: Location) {
        fcmToken?.let { token ->
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            db.collection("devices")
                .whereEqualTo("token", token)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        db.collection("devices").document(document.id)
                            .update(
                                "location", geoPoint,
                                "lastLocationUpdate", Timestamp.now()
                            )
                    }
                }
        }
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

                fcmToken?.let { token ->
                    db.collection("devices")
                        .whereEqualTo("token", token)
                        .get()
                        .addOnSuccessListener { documents ->
                            for (document in documents) {
                                db.collection("devices").document(document.id)
                                    .update("batteryLevel", batteryLevel)
                            }
                        }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e("Battery", "Receiver error: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        fcmToken?.let { token ->
            db.collection("devices")
                .whereEqualTo("token", token)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        db.collection("devices").document(document.id)
                            .update(
                                "isOnline", true,
                                "lastOnline", Timestamp.now()
                            )
                    }
                }
        }
    }

    override fun onPause() {
        super.onPause()
        fcmToken?.let { token ->
            db.collection("devices")
                .whereEqualTo("token", token)
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        db.collection("devices").document(document.id)
                            .update("isOnline", false)
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterBatteryReceiver()
        stopLocationUpdates()
    }

    private fun updateUI() {
        if (devicePolicyManager.isAdminActive(compName)) {
            tvStatus.text = "Device Admin Status: Active"
            btnActivateAdmin.text = "Device Admin Already Active"
            btnActivateAdmin.isEnabled = false
        } else {
            tvStatus.text = "Device Admin Status: Not Active"
            btnActivateAdmin.text = "Activate Device Admin"
            btnActivateAdmin.isEnabled = true
        }
    }

    private fun activateDeviceAdmin() {
        if (!devicePolicyManager.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable to use MDM features")
            startActivity(intent)
        }
    }
}




