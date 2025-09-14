package com.example.mdm_client

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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


    // ✅ إضافة ثوابت للقيم المستخدمة لتحسين قابلية القراءة والصيانة
    companion object {
        private const val LOCATION_UPDATE_INTERVAL = 30000L // 30 ثانية
        private const val LOCATION_FASTEST_INTERVAL = 15000L // 15 ثانية
        private const val MIN_BATTERY_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 دقائق
        private const val PERMISSIONS_REQUEST_CODE = 99
        private const val TAG_FCM = "FCM"
        private const val TAG_FIRESTORE = "Firestore"
        private const val TAG_APPS = "Apps"
        private const val TAG_LOCATION = "Location"
        private const val TAG_BATTERY = "Battery"
        private const val TAG_LIFECYCLE = "Lifecycle"
        private const val MAX_FCM_RETRIES = 5 // ✅ الحد الأقصى لعدد محاولات الحصول على FCM Token
        private const val FCM_RETRY_DELAY_MS = 5000L // ✅ التأخير بين المحاولات بالمللي ثانية (5 ثواني)

        //sms
        private const val SMS_PERMISSION_REQUEST_CODE = 1002

    }
    //sms
    private fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) !=
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                ),
                SMS_PERMISSION_REQUEST_CODE
            )
        }
    }

    // متغيرات النظام الأساسية
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName
    private val db = Firebase.firestore

    // عناصر واجهة المستخدم
    private lateinit var tvStatus: TextView
    private lateinit var tvToken: TextView
    private lateinit var btnActivateAdmin: Button

    // متغيرات Firebase و Firestore
    private var fcmToken: String? = null
    private var deviceDocumentId: String? = null // ✅ متغير لتخزين معرف مستند الجهاز في Firestore لتجنب الاستعلامات المتكررة

    // متغيرات تتبع الموقع
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationRequest: LocationRequest = LocationRequest.create().apply {
        interval = LOCATION_UPDATE_INTERVAL
        fastestInterval = LOCATION_FASTEST_INTERVAL
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    private lateinit var locationCallback: LocationCallback

    // متغيرات البطارية
    private var batteryLevel: Int = -1
    private var lastBatteryUpdateTime: Long = 0
    private lateinit var batteryReceiver: BroadcastReceiver

    // ✅ دالة جديدة للحصول على مستوى البطارية الأولي فوراً
    private fun getInitialBatteryLevel() {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // استخدام registerReceiver مع null receiver للحصول على آخر sticky intent
        val batteryStatus: Intent? = applicationContext.registerReceiver(null, iFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        // حساب النسبة المئوية وتحديث المتغير
        batteryLevel = if (level != -1 && scale != -1 && scale != 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
        Log.d(TAG_BATTERY, "Initial Battery Level fetched: $batteryLevel%")
    }

    // الصلاحيات المطلوبة
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.QUERY_ALL_PACKAGES // ✅ يتطلب مراجعة خاصة من Google Play Store
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //sms
        requestSmsPermission()


        // تهيئة مكونات النظام
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // تهيئة عناصر واجهة المستخدم
        initializeUI()

        // تهيئة خدمات الموقع
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // فحص الصلاحيات وبدء التهيئة
        if (checkPermissions()) {
            initialize()
        } else {
            requestPermissions()
        }
    }

    /**
     * تهيئة عناصر واجهة المستخدم
     */
    private fun initializeUI() {
        tvStatus = findViewById(R.id.tvStatus)
        tvToken = findViewById(R.id.tvToken)
        btnActivateAdmin = findViewById(R.id.btnActivateAdmin)
        btnActivateAdmin.setOnClickListener { activateDeviceAdmin() }
    }

    /**
     * فحص الصلاحيات المطلوبة
     * @return true إذا كانت جميع الصلاحيات ممنوحة
     */
    private fun checkPermissions(): Boolean {
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    /**
     * طلب الصلاحيات من المستخدم
     */
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initialize()
        } else {
            Log.e(TAG_LIFECYCLE, "Required permissions not granted")
        }
    }

    /**
     * تهيئة التطبيق الرئيسية
     */
    private fun initialize() {
        updateUI()
        getInitialBatteryLevel() // ✅ استدعاء الدالة الجديدة هنا للحصول على قيمة البطارية الأولية
        getFCMTokenWithRetry(0)
    }

    /**
     * محاولة الحصول على FCM Token مع آلية إعادة المحاولة
     * @param retryCount عدد المحاولات التي تمت حتى الآن
     */
    private fun getFCMTokenWithRetry(retryCount: Int) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fcmToken = task.result
                Log.d(TAG_FCM, "FCM Token retrieved successfully: $fcmToken")
                runOnUiThread {
                    tvToken.text = "FCM Token: ${fcmToken?.take(20)}..."
                }

                val deviceName = Build.MODEL ?: "Unknown"
                val osVersion = Build.VERSION.RELEASE ?: "Unknown"

                // بدء العمليات الأساسية
                saveDeviceDataToFirestore(fcmToken!!, deviceName, osVersion)
                createLocationCallback()
                startLocationUpdates()
                registerBatteryReceiver()

            } else {
                Log.e(TAG_FCM, "Token fetch failed: ${task.exception?.message}", task.exception)
                if (retryCount < MAX_FCM_RETRIES) {
                    Log.w(TAG_FCM, "Retrying FCM token fetch... Attempt ${retryCount + 1} of $MAX_FCM_RETRIES")
                    Handler(Looper.getMainLooper()).postDelayed({
                        getFCMTokenWithRetry(retryCount + 1)
                    }, FCM_RETRY_DELAY_MS)
                } else {
                    Log.e(TAG_FCM, "Max retries reached for FCM token fetch. Giving up.")
                    runOnUiThread {
                        tvToken.text = "FCM Token: Not Available (Max Retries)"
                    }
                }
            }
        }
    }

    /**
     * حفظ بيانات الجهاز في Firestore مع التحقق من الاتصال
     */
    private fun saveDeviceDataToFirestore(token: String, deviceName: String, osVersion: String) {
        if (!isNetworkAvailable()) {
            Log.e(TAG_FIRESTORE, "No network available, cannot save device data.")
            return
        }

        val data = hashMapOf(
            "token" to token,
            "deviceName" to deviceName,
            "osVersion" to osVersion,
            "isOnline" to true,
            "lastOnline" to Timestamp.now(),
            "batteryLevel" to batteryLevel, // ✅ سيتم استخدام القيمة الأولية الصحيحة هنا
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "sdkVersion" to Build.VERSION.SDK_INT,
            "phoneNumber" to ""
        )

        db.collection("devices")
            .whereEqualTo("token", token)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // إضافة جهاز جديد
                    db.collection("devices").add(data)
                        .addOnSuccessListener { docRef ->
                            Log.d(TAG_FIRESTORE, "Device added: ${docRef.id}")
                            deviceDocumentId = docRef.id // ✅ تخزين معرف المستند محليًا
                            getInstalledApps() // ✅ استدعاء getInstalledApps بعد تعيين deviceDocumentId
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG_FIRESTORE, "Error adding device: ", e)
                        }
                } else {
                    // تحديث الجهاز الموجود
                    for (document in documents) {
                        deviceDocumentId = document.id // ✅ تخزين معرف المستند محليًا
                        db.collection("devices").document(document.id).update(data as Map<String, Any>)
                            .addOnSuccessListener {
                                Log.d(TAG_FIRESTORE, "Device updated: ${document.id}")
                                getInstalledApps() // ✅ استدعاء getInstalledApps بعد تعيين deviceDocumentId
                            }
                            .addOnFailureListener { e -> Log.e(TAG_FIRESTORE, "Error updating device: ", e) }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_FIRESTORE, "Error querying devices: ", e)
            }
    }

    /**
     * الحصول على قائمة التطبيقات المثبتة وتحديثها في Firestore
     */
    private fun getInstalledApps() {
        if (!isNetworkAvailable()) {
            Log.e(TAG_APPS, "No network available, cannot update installed apps.")
            return
        }

        deviceDocumentId?.let { docId ->
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            Log.d(TAG_APPS, "Total applications found: ${apps.size}")
            val installedApps = ArrayList<HashMap<String, String>>()

            for (app in apps) {
                try {
                    val packageInfo = pm.getPackageInfo(app.packageName, 0)
                    val appInfo = HashMap<String, String>()
                    appInfo["packageName"] = app.packageName
                    appInfo["appName"] = app.loadLabel(pm).toString()
                    appInfo["version"] = packageInfo.versionName ?: ""
                    appInfo["installDate"] = Date(packageInfo.firstInstallTime).toString()

                    // ✅ تحديد نوع التطبيق (نظام أو مثبت يدوياً)
                    val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    appInfo["appType"] = if (isSystemApp) "system_app" else "user_app"
                    installedApps.add(appInfo)
                } catch (e: Exception) {
                    Log.e(TAG_APPS, "Error getting app info for ${app.packageName}: ${e.message}")
                }
            }
            Log.d(TAG_APPS, "Installed apps list size: ${installedApps.size}")
            // ✅ تحديث قائمة التطبيقات مباشرة باستخدام معرف المستند
            db.collection("devices").document(docId)
                .update("installedApps", installedApps)
                .addOnSuccessListener { Log.d(TAG_FIRESTORE, "Installed apps updated for device: $docId") }
                .addOnFailureListener { e -> Log.e(TAG_FIRESTORE, "Error updating installed apps: ", e) }
        } ?: run {
            Log.e(TAG_APPS, "deviceDocumentId is null, cannot update installed apps.")
        }
    }

    /**
     * إنشاء callback لتتبع الموقع
     */
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    updateLocationInFirestore(location)
                }
            }
        }
    }

    /**
     * بدء تحديثات الموقع
     */
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG_LOCATION, "Location permissions not granted")
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * تحديث الموقع في Firestore
     */
    private fun updateLocationInFirestore(location: Location) {
        deviceDocumentId?.let { docId ->
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            db.collection("devices").document(docId)
                .update(
                    "location", geoPoint,
                    "lastLocationUpdate", Timestamp.now()
                )
                .addOnSuccessListener { Log.d(TAG_FIRESTORE, "Location updated for device: $docId") }
                .addOnFailureListener { e -> Log.e(TAG_FIRESTORE, "Error updating location: ", e) }
        } ?: run {
            Log.e(TAG_LOCATION, "deviceDocumentId is null, cannot update location.")
        }
    }

    /**
     * تسجيل مستقبل تحديثات البطارية مع تحسين التكرار
     */
    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    Log.d(TAG_BATTERY, "Battery intent received: ${intent.action}")
                    val currentBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    Log.d(TAG_BATTERY, "Raw Battery Level from Intent: $currentBatteryLevel, Scale from Intent: $scale")
                    val batteryPct = if (scale > 0) (currentBatteryLevel * 100 / scale) else -1
                    Log.d(TAG_BATTERY, "Calculated Battery Percentage: $batteryPct")
                    val currentTime = System.currentTimeMillis()

                    // ✅ تحديث مستوى البطارية في Firestore فقط إذا تغير المستوى أو مر وقت كافٍ
                    if (batteryPct != batteryLevel ||
                        (currentTime - lastBatteryUpdateTime) > MIN_BATTERY_UPDATE_INTERVAL) {

                        batteryLevel = batteryPct
                        lastBatteryUpdateTime = currentTime

                        deviceDocumentId?.let { docId ->
                            db.collection("devices").document(docId)
                                .update("batteryLevel", batteryLevel)
                                .addOnSuccessListener { Log.d(TAG_FIRESTORE, "Battery level updated for device: $docId") }
                                .addOnFailureListener { e -> Log.e(TAG_FIRESTORE, "Error updating battery level: ", e) }
                        } ?: run {
                            Log.e(TAG_BATTERY, "deviceDocumentId is null, cannot update battery level.")
                        }
                    }
                } else {
                    Log.w(TAG_BATTERY, "Received unexpected intent action: ${intent.action}")
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    /**
     * إلغاء تسجيل مستقبل البطارية مع معالجة الأخطاء
     */
    private fun unregisterBatteryReceiver() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.e(TAG_BATTERY, "Receiver error: ${e.message}")
        }
    }

    /**
     * إيقاف تحديثات الموقع
     */
    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    /**
     * التحقق من توفر الاتصال بالإنترنت
     * @return true إذا كان الاتصال متوفراً
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            networkInfo.isConnected
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()

        // ✅ تحديث حالة الاتصال باستخدام معرف المستند المحفوظ
        deviceDocumentId?.let { docId ->
            val updates = hashMapOf<String, Any>(
                "isOnline" to true,
                "lastOnline" to Timestamp.now()
            )
            db.collection("devices").document(docId)
                .update(updates)
                .addOnSuccessListener { Log.d(TAG_FIRESTORE, "Device online status updated for device: $docId") }
                .addOnFailureListener { e -> Log.e(TAG_FIRESTORE, "Error updating online status: ", e) }
        } ?: run {
            Log.e(TAG_LIFECYCLE, "deviceDocumentId is null, cannot update online status.")
        }
    }

    override fun onPause() {
        super.onPause()

        // ✅ تحديث حالة عدم الاتصال
        deviceDocumentId?.let { docId ->
            db.collection("devices").document(docId)
                .update("isOnline", false)
                .addOnSuccessListener { Log.d(TAG_FIRESTORE, "Device offline status updated for device: $docId") }
                .addOnFailureListener { e -> Log.e(TAG_FIRESTORE, "Error updating offline status: ", e) }
        } ?: run {
            Log.e(TAG_LIFECYCLE, "deviceDocumentId is null, cannot update offline status.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // تنظيف الموارد
        unregisterBatteryReceiver()
        stopLocationUpdates()
    }

    /**
     * تحديث واجهة المستخدم بناءً على حالة Device Admin
     */
    private fun updateUI() {
        runOnUiThread {
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
    }

    /**
     * تفعيل Device Admin
     */
    private fun activateDeviceAdmin() {
        if (!devicePolicyManager.isAdminActive(compName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable to use MDM features")
            startActivity(intent)
        }
    }
}
