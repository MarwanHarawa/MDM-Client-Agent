package com.example.mdm_client

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.provider.Settings

import android.content.Intent
import android.net.Uri
import java.lang.reflect.Method


import java.lang.reflect.Field

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate() {
        super.onCreate()
        dpm =
//            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent =
//            ComponentName(this, MyDeviceAdminReceiver::class.java)
            ComponentName(this, MyDeviceAdminReceiver::class.java)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val command = remoteMessage.data["command"]
        Log.d("FCM_COMMAND", "Command received: $command")

        if (!dpm.isAdminActive(adminComponent)) {
            logError("التطبيق ليس مسؤول الجهاز. لا يمكن تنفيذ الأوامر.")
            return
        }

        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)

        when (command) {
            // --- أوامر الأمان (Security) ---
            "lock" -> lockDevice()
            "wipe_device" -> if (isDeviceOwner) wipeDevice() else logPermissionError(command)
            "camera_disable" -> setCameraDisabled(true)
            "camera_enable" -> setCameraDisabled(false)
            // استخدام القيمة النصية بدلاً من الثابت
            "factory_reset_protection_disable" -> if (isDeviceOwner) setUserRestriction("no_factory_reset", true) else logPermissionError(command)
            "factory_reset_protection_enable" -> if (isDeviceOwner) setUserRestriction("no_factory_reset", false) else logPermissionError(command)

            // --- أوامر النظام (System) ---
            "reboot_device" -> if (isDeviceOwner) rebootDevice() else logPermissionError(command)
//            "shutdown_device" -> if (isDeviceOwner) shutdownDevice() else logPermissionError(command)
            "update_system" -> if (isDeviceOwner) installSystemUpdate() else logPermissionError(command)

            // --- أوامر التطبيقات (Apps) ---
            "install_app" -> {
                val apkUrl = remoteMessage.data["apk_url"]
                if (isDeviceOwner && apkUrl != null) installApp(apkUrl) else logPermissionError(command, "ورابط APK مطلوب")
            }
            "uninstall_app" -> {
                val packageName = remoteMessage.data["package_name"]
                if (isDeviceOwner && packageName != null) uninstallApp(packageName) else logPermissionError(command, "واسم الحزمة مطلوب")
            }

            // --- أوامر التحكم في التطبيقات ---
            "install_apps_disable" -> if (isDeviceOwner) setInstallAppsRestriction(true) else logPermissionError(command)
            "install_apps_enable" -> if (isDeviceOwner) setInstallAppsRestriction(false) else logPermissionError(command)
            "uninstall_apps_disable" -> if (isDeviceOwner) setUninstallAppsRestriction(true) else logPermissionError(command)
            "uninstall_apps_enable" -> if (isDeviceOwner) setUninstallAppsRestriction(false) else logPermissionError(command)

            // --- أوامر التحكم في متجر Play ---
            "play_store_disable" -> if (isDeviceOwner) setPlayStoreEnabled(false) else logPermissionError(command)
            "play_store_enable" -> if (isDeviceOwner) setPlayStoreEnabled(true) else logPermissionError(command)

            // --- أوامر الشبكة (Network) ---
            // استخدام القيمة النصية بدلاً من الثابت
            "bluetooth_disable" -> if (isDeviceOwner) setUserRestriction("no_bluetooth", true) else logPermissionError(command)
            "bluetooth_enable" -> if (isDeviceOwner) setUserRestriction("no_bluetooth", false) else logPermissionError(command)
            // استخدام القيمة النصية بدلاً من الثابت
            "usb_data_disable" -> if (isDeviceOwner) setUserRestriction("no_usb_file_transfer", true) else logPermissionError(command)
            "usb_data_enable" -> if (isDeviceOwner) setUserRestriction("no_usb_file_transfer", false) else logPermissionError(command)

            // --- أوامر الموقع (Location) ---
            "gps_disable" ->
//                if (isDeviceOwner)
                    setLocationEnabled(false)
//                else logPermissionError(command)
            "gps_enable" ->
//                if (isDeviceOwner)
                    setLocationEnabled(true)
//                else logPermissionError(command)
            "locate_device" -> locateDevice()
            "play_sound" -> playSound()
            //sms
            "enable_sms_commands" -> enableSmsReceiving()


            else -> Log.w("FCM_COMMAND", "Unknown or unsupported command: $command")
        }
    }

    // --- دوال تنفيذ الأوامر ---

    private fun lockDevice() {
        dpm.lockNow()
        showLocalNotification("تم التنفيذ", "🔒 تم قفل الجهاز بنجاح.")
    }

    private fun wipeDevice() {
        dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE)
    }

    private fun setCameraDisabled(disabled: Boolean) {
        dpm.setCameraDisabled(adminComponent, disabled)
        showLocalNotification("تم التنفيذ", if (disabled) "📷 تم تعطيل الكاميرا." else "📷 تم تفعيل الكاميرا.")
    }

    private fun rebootDevice() {
        dpm.reboot(adminComponent)
    }

//    private fun shutdownDevice() {
//        try {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                dpm.shutdown(adminComponent)
//            } else {
//                logError("إيقاف التشغيل غير مدعوم على هذا الإصدار من أندرويد.")
//            }
//        } catch (e: SecurityException) {
//            logPermissionError("shutdown_device", "يتطلب توقيع النظام")
//        }
//    }

    private fun installSystemUpdate() {
        logError("أمر تحديث النظام لم يتم تنفيذه بعد.")
    }

    private fun installApp(apkUrl: String) {
        logError("أمر تثبيت التطبيق لم يتم تنفيذه بعد.")
    }

    private fun uninstallApp(packageName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // استخدام الانعكاس للوصول إلى uninstallSilently
                try {
                    // الحصول على دالة uninstallSilently
                    val uninstallMethod = DevicePolicyManager::class.java.getDeclaredMethod(
                        "uninstallSilently",
                        ComponentName::class.java,
                        String::class.java,
                        Int::class.java
                    )

                    // الحصول على ثابت DELETE_DEVICE_ENCRYPTION_KEY
                    val deleteEncryptionKeyField = DevicePolicyManager::class.java.getDeclaredField("DELETE_DEVICE_ENCRYPTION_KEY")
                    val deleteEncryptionKey = deleteEncryptionKeyField.getInt(null)

                    // استدعاء الدالة
                    uninstallMethod.invoke(dpm, adminComponent, packageName, deleteEncryptionKey)
                    showLocalNotification("إزالة تطبيق", "🗑️ تم إرسال طلب إزالة الحزمة: $packageName")
                } catch (e: NoSuchMethodException) {
                    Log.e("UNINSTALL_ERROR", "uninstallSilently غير متاحة: ${e.message}")
                    uninstallAppFallback(packageName)
                } catch (e: NoSuchFieldException) {
                    Log.e("UNINSTALL_ERROR", "DELETE_DEVICE_ENCRYPTION_KEY غير متاح: ${e.message}")
                    uninstallAppFallback(packageName)
                }
            } else {
                // للإصدارات الأقدم من Android 9
                uninstallAppLegacy(packageName)
            }
        } catch (e: SecurityException) {
            logPermissionError("uninstall_app")
        } catch (e: Exception) {
            Log.e("UNINSTALL_ERROR", "خطأ عام: ${e.message}")
            uninstallAppFallback(packageName)
        }
    }

    // طريقة للإصدارات الأقدم من Android 9
    private fun uninstallAppLegacy(packageName: String) {
        try {
            // استخدام الانعكاس للوصول إلى uninstallPackage
            val uninstallMethod = DevicePolicyManager::class.java.getDeclaredMethod(
                "uninstallPackage",
                ComponentName::class.java,
                String::class.java,
                Intent::class.java
            )
            uninstallMethod.invoke(dpm, adminComponent, packageName, null)
            showLocalNotification("إزالة تطبيق", "🗑️ تم إرسال طلب إزالة الحزمة: $packageName")
        } catch (e: Exception) {
            Log.e("UNINSTALL_LEGACY_ERROR", "فشل الطريقة القديمة: ${e.message}")
            uninstallAppFallback(packageName)
        }
    }

    // طريقة بديلة كحل أخير
    private fun uninstallAppFallback(packageName: String) {
        try {
            // فتح واجهة إزالة التطبيق القياسية
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            showLocalNotification("إزالة تطبيق", "🗑️ تم فتح واجهة إزالة التطبيق: $packageName")
        } catch (e: Exception) {
            Log.e("UNINSTALL_FALLBACK_ERROR", "فشل الطريقة البديلة: ${e.message}")
            showLocalNotification("خطأ", "فشل في إزالة التطبيق: ${e.message}")
        }
    }

    private fun setUserRestriction(restriction: String, disallowed: Boolean) {
        try {
            if (disallowed) {
                dpm.addUserRestriction(adminComponent, restriction)
            } else {
                dpm.clearUserRestriction(adminComponent, restriction)
            }
            val status = if (disallowed) "مفعل" else "معطل"
            showLocalNotification("تم تطبيق القيد", "تم تحديث سياسة $restriction إلى $status")
        } catch (e: SecurityException) {
            logPermissionError("setUserRestriction for $restriction")
        }
    }


private fun setLocationEnabled(enabled: Boolean) {
    try {
        // استخدام قيود المستخدم لمنع تغيير إعدادات الموقع
        val locationRestriction = "no_config_location"

        if (enabled) {
            // تفعيل الموقع: إزالة القيود وتفعيل الإعدادات
            dpm.clearUserRestriction(adminComponent, locationRestriction)
            dpm.setGlobalSetting(adminComponent, "location_global_kill_switch", "0")
            showLocalNotification("تم التنفيذ", "📍 تم تفعيل خدمات الموقع.")
        } else {
            // تعطيل الموقع: تطبيق القيود وتعطيل الإعدادات
            dpm.addUserRestriction(adminComponent, locationRestriction)
            dpm.setGlobalSetting(adminComponent, "location_global_kill_switch", "1")

            // تعطيل الموقع في الإعدادات السريعة أيضًا
            dpm.setSecureSetting(adminComponent, "location_mode", "0")

            showLocalNotification("تم التنفيذ", "📍 تم تعطيل خدمات الموقع ومنع المستخدم من تفعيلها.")
        }
    } catch (e: SecurityException) {
        logPermissionError("setLocationEnabled")
    }
}

    private fun locateDevice() {
        logError("أمر تحديد الموقع لم يتم تنفيذه بعد.")
    }


private fun playSound() {
    try {
        // استخدام WakeLock لضمان تشغيل الصوت حتى لو كان الشاشة مطفأة
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MDM:PlaySoundLock")
        wakeLock.acquire(10000) // 10 ثوانٍ كافية لتشغيل الصوت

        // تشغيل صوت الإنذار
        val mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM) // هذا النوع يعمل حتى في وضع الصامت
                    .build()
            )
            setDataSource(this@MyFirebaseMessagingService, Settings.System.DEFAULT_ALARM_ALERT_URI)
            isLooping = false
            prepare()
            start()

            setOnCompletionListener {
                it.release()
                wakeLock.release()
            }

            setOnErrorListener { mp, what, extra ->
                mp.release()
                wakeLock.release()
                true
            }
        }

        showLocalNotification("تم التنفيذ", "🔊 جاري تشغيل الصوت.")
    } catch (e: Exception) {
        Log.e("PLAY_SOUND_ERROR", "فشل تشغيل الصوت: ${e.message}")
        showLocalNotification("خطأ", "فشل تشغيل الصوت: ${e.message}")
    }
}

    private fun setInstallAppsRestriction(disallowed: Boolean) {
        try {
            val restriction = "no_install_apps"
            if (disallowed) {
                dpm.addUserRestriction(adminComponent, restriction)
                // إضافة قيود إضافية لتعزيز الأمان
                dpm.addUserRestriction(adminComponent, "no_install_unknown_sources")
                dpm.addUserRestriction(adminComponent, "no_install_unknown_sources_globally")
            } else {
                dpm.clearUserRestriction(adminComponent, restriction)
                dpm.clearUserRestriction(adminComponent, "no_install_unknown_sources")
                dpm.clearUserRestriction(adminComponent, "no_install_unknown_sources_globally")
            }

            val status = if (disallowed) "ممنوع" else "مسموح"
            showLocalNotification("تحكم في التطبيقات", "تثبيت التطبيقات أصبح $status")
        } catch (e: SecurityException) {
            logPermissionError("setInstallAppsRestriction")
        }
    }

    private fun setUninstallAppsRestriction(disallowed: Boolean) {
        try {
            val restriction = "no_uninstall_apps"
            if (disallowed) {
                dpm.addUserRestriction(adminComponent, restriction)
                // حماية التطبيقات المهمة من الإزالة
                protectSystemApps()
            } else {
                dpm.clearUserRestriction(adminComponent, restriction)
            }

            val status = if (disallowed) "ممنوع" else "مسموح"
            showLocalNotification("تحكم في التطبيقات", "حذف التطبيقات أصبح $status")
        } catch (e: SecurityException) {
            logPermissionError("setUninstallAppsRestriction")
        }
    }

    private fun protectSystemApps() {
        try {
            // حماية تطبيقات النظام الأساسية
            val systemApps = listOf(
                "com.android.settings",
                "com.android.phone",
                "com.android.systemui",
                "com.android.providers.settings",
                "com.android.vending", // متجر Play
                packageName // تطبيق المدير نفسه
            )

            for (app in systemApps) {
                try {
                    dpm.setUninstallBlocked(adminComponent, app, true)
                } catch (e: Exception) {
                    Log.w("PROTECT_APPS", "فشل حماية التطبيق: $app")
                }
            }
        } catch (e: Exception) {
            Log.e("PROTECT_APPS", "خطأ في حماية التطبيقات: ${e.message}")
        }


    }

    private fun setPlayStoreEnabled(enabled: Boolean) {
        try {
            val playStorePackage = "com.android.vending"

            if (enabled) {
                // تمكين متجر Play
                dpm.setApplicationHidden(adminComponent, playStorePackage, false)
                dpm.setUninstallBlocked(adminComponent, playStorePackage, false)

                // إزالة قيود التثبيت إذا كانت موجودة
                dpm.clearUserRestriction(adminComponent, "no_install_apps")
                dpm.clearUserRestriction(adminComponent, "no_install_unknown_sources")

                showLocalNotification("تم التنفيذ", "🛒 تم تفعيل متجر Play.")
            } else {
                // تعطيل متجر Play
                dpm.setApplicationHidden(adminComponent, playStorePackage, true)
                dpm.setUninstallBlocked(adminComponent, playStorePackage, true)

                // إضافة قيود إضافية لمنع التثبيت
                dpm.addUserRestriction(adminComponent, "no_install_apps")
                dpm.addUserRestriction(adminComponent, "no_install_unknown_sources")

                showLocalNotification("تم التنفيذ", "🛒 تم تعطيل متجر Play ومنع الوصول إليه.")
            }
        } catch (e: SecurityException) {
            logPermissionError("setPlayStoreEnabled")
        } catch (e: Exception) {
            Log.e("PLAY_STORE_ERROR", "خطأ في التحكم بمتجر Play: ${e.message}")
            showLocalNotification("خطأ", "فشل في التحكم بمتجر Play: ${e.message}")
        }
    }


    //sms
    private fun enableSmsReceiving() {
        try {
            // إزالة أي قيود على استقبال الرسائل
            dpm.clearUserRestriction(adminComponent, "no_sms")

            // تفعيل صلاحيات SMS
            dpm.setPermissionPolicy(adminComponent, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)

            Log.d("SMS_CONTROL", "SMS receiving enabled")
        } catch (e: Exception) {
            Log.e("SMS_CONTROL", "Error enabling SMS receiving: ${e.message}")
        }
    }

    // --- دوال مساعدة ---

    private fun logPermissionError(command: String?, extraInfo: String = "") {
        val message = "صلاحيات مالك الجهاز (Device Owner) مطلوبة لتنفيذ الأمر: $command. $extraInfo"
        Log.e("PERMISSION_ERROR", message)
        showLocalNotification("فشل الأمر", message)
    }

    private fun logError(message: String) {
        Log.e("EXECUTION_ERROR", message)
        showLocalNotification("خطأ", message)
    }


@SuppressLint("MissingPermission")
private fun showLocalNotification(title: String, message: String) {
    val channelId = "admin_commands_channel"
    val notificationId = (System.currentTimeMillis() % 10000).toInt()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "أوامر المسؤول"
        val descriptionText = "إشعارات تنفيذ الأوامر الإدارية"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    val notificationBuilder = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    // --- التعديل هنا ---
    val notificationManager = NotificationManagerCompat.from(this)
    if (notificationManager.areNotificationsEnabled()) {
        notificationManager.notify(notificationId, notificationBuilder.build())
    } else {
        Log.e("NOTIFICATION_ERROR", "Notification permission not granted.")
    }
}

}
