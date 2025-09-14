//
//package com.example.mdm_client
//
//import android.annotation.SuppressLint
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.admin.DevicePolicyManager
//import android.content.*
//import android.media.AudioAttributes
//import android.media.MediaPlayer
//import android.net.Uri
//import android.os.Build
//import android.os.PowerManager
//import android.provider.Settings
//import android.provider.Telephony
//import android.telephony.SmsMessage
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import androidx.core.app.NotificationManagerCompat
//import java.lang.reflect.Method
//
//class SmsReceiver : BroadcastReceiver() {
//
//    private lateinit var dpm: DevicePolicyManager
//    private lateinit var adminComponent: ComponentName
//    private lateinit var context: Context
//
//    override fun onReceive(context: Context, intent: Intent) {
//        this.context = context
//        dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
//        adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
//
//        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
//            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
//            val fullMessageBody = messages.joinToString("") { it.messageBody }
//            val sender = messages.first().originatingAddress
//
//            Log.d("SMS_RECEIVER", "Received SMS from $sender: $fullMessageBody")
//
//            // تحليل الأمر والبيانات من الرسالة
//            // التنسيق المتوقع: "command:data" أو "command"
//            val parts = fullMessageBody.split(":", limit = 2)
//            val command = parts[0].trim().lowercase()
//            val data = if (parts.size > 1) parts[1].trim() else null
//
//            executeCommand(command, data)
//        }
//    }
//
//    private fun executeCommand(command: String, data: String?) {
//        if (!dpm.isAdminActive(adminComponent)) {
//            logError("التطبيق ليس مسؤول الجهاز. لا يمكن تنفيذ الأوامر.")
//            return
//        }
//
//        val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
//
//        when (command) {
//            // --- أوامر الأمان (Security) ---
//            "lock" -> lockDevice()
//            "wipe_device" -> if (isDeviceOwner) wipeDevice() else logPermissionError(command)
//            "camera_disable" -> setCameraDisabled(true)
//            "camera_enable" -> setCameraDisabled(false)
//            "factory_reset_protection_disable" -> if (isDeviceOwner) setUserRestriction("no_factory_reset", true) else logPermissionError(command)
//            "factory_reset_protection_enable" -> if (isDeviceOwner) setUserRestriction("no_factory_reset", false) else logPermissionError(command)
//
//            // --- أوامر النظام (System) ---
//            "reboot_device" -> if (isDeviceOwner) rebootDevice() else logPermissionError(command)
//            "update_system" -> if (isDeviceOwner) installSystemUpdate() else logPermissionError(command)
//
//            // --- أوامر التطبيقات (Apps) ---
//            "install_app" -> {
//                if (isDeviceOwner && data != null) installApp(data) else logPermissionError(command, "ورابط APK مطلوب")
//            }
//            "uninstall_app" -> {
//                if (isDeviceOwner && data != null) uninstallApp(data) else logPermissionError(command, "واسم الحزمة مطلوب")
//            }
//
//            // --- أوامر التحكم في التطبيقات ---
//            "install_apps_disable" -> if (isDeviceOwner) setInstallAppsRestriction(true) else logPermissionError(command)
//            "install_apps_enable" -> if (isDeviceOwner) setInstallAppsRestriction(false) else logPermissionError(command)
//            "uninstall_apps_disable" -> if (isDeviceOwner) setUninstallAppsRestriction(true) else logPermissionError(command)
//            "uninstall_apps_enable" -> if (isDeviceOwner) setUninstallAppsRestriction(false) else logPermissionError(command)
//
//            // --- أوامر التحكم في متجر Play ---
//            "play_store_disable" -> if (isDeviceOwner) setPlayStoreEnabled(false) else logPermissionError(command)
//            "play_store_enable" -> if (isDeviceOwner) setPlayStoreEnabled(true) else logPermissionError(command)
//
//            // --- أوامر الشبكة (Network) ---
//            "bluetooth_disable" -> if (isDeviceOwner) setUserRestriction("no_bluetooth", true) else logPermissionError(command)
//            "bluetooth_enable" -> if (isDeviceOwner) setUserRestriction("no_bluetooth", false) else logPermissionError(command)
//            "usb_data_disable" -> if (isDeviceOwner) setUserRestriction("no_usb_file_transfer", true) else logPermissionError(command)
//            "usb_data_enable" -> if (isDeviceOwner) setUserRestriction("no_usb_file_transfer", false) else logPermissionError(command)
//
//            // --- أوامر الموقع (Location) ---
//            "gps_disable" -> setLocationEnabled(false)
//            "gps_enable" -> setLocationEnabled(true)
//            "locate_device" -> locateDevice()
//            "play_sound" -> playSound()
//            "enable_sms_commands" -> enableSmsReceiving()
//
//            else -> Log.w("SMS_COMMAND", "Unknown or unsupported command: $command")
//        }
//    }
//
//    // --- دوال تنفيذ الأوامر (منسوخة ومعدلة من MyFirebaseMessagingService) ---
//
//    private fun lockDevice() {
//        dpm.lockNow()
//        showLocalNotification("تم التنفيذ", "🔒 تم قفل الجهاز بنجاح.")
//    }
//
//    private fun wipeDevice() {
//        dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE)
//    }
//
//    private fun setCameraDisabled(disabled: Boolean) {
//        dpm.setCameraDisabled(adminComponent, disabled)
//        showLocalNotification("تم التنفيذ", if (disabled) "📷 تم تعطيل الكاميرا." else "📷 تم تفعيل الكاميرا.")
//    }
//
//    private fun rebootDevice() {
//        dpm.reboot(adminComponent)
//    }
//
//    private fun installSystemUpdate() {
//        logError("أمر تحديث النظام لم يتم تنفيذه بعد.")
//    }
//
//    private fun installApp(apkUrl: String) {
//        logError("أمر تثبيت التطبيق لم يتم تنفيذه بعد.")
//    }
//
//    private fun uninstallApp(packageName: String) {
//        try {
//            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
//                data = Uri.parse("package:$packageName")
//                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            }
//            context.startActivity(intent)
//            showLocalNotification("إزالة تطبيق", "🗑️ تم فتح واجهة إزالة التطبيق: $packageName")
//        } catch (e: Exception) {
//            Log.e("UNINSTALL_FALLBACK_ERROR", "فشل الطريقة البديلة: ${e.message}")
//            showLocalNotification("خطأ", "فشل في إزالة التطبيق: ${e.message}")
//        }
//    }
//
//    private fun setUserRestriction(restriction: String, disallowed: Boolean) {
//        try {
//            if (disallowed) {
//                dpm.addUserRestriction(adminComponent, restriction)
//            } else {
//                dpm.clearUserRestriction(adminComponent, restriction)
//            }
//            val status = if (disallowed) "مفعل" else "معطل"
//            showLocalNotification("تم تطبيق القيد", "تم تحديث سياسة $restriction إلى $status")
//        } catch (e: SecurityException) {
//            logPermissionError("setUserRestriction for $restriction")
//        }
//    }
//
//    private fun setLocationEnabled(enabled: Boolean) {
//        try {
//            val locationRestriction = "no_config_location"
//            if (enabled) {
//                dpm.clearUserRestriction(adminComponent, locationRestriction)
//                showLocalNotification("تم التنفيذ", "📍 تم السماح بالتحكم في خدمات الموقع.")
//            } else {
//                dpm.addUserRestriction(adminComponent, locationRestriction)
//                showLocalNotification("تم التنفيذ", "📍 تم منع المستخدم من تغيير إعدادات الموقع.")
//            }
//        } catch (e: SecurityException) {
//            logPermissionError("setLocationEnabled")
//        }
//    }
//
//    private fun locateDevice() {
//        logError("أمر تحديد الموقع لم يتم تنفيذه بعد.")
//    }
//
//    private fun playSound() {
//        try {
//            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
//            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MDM:PlaySoundLock")
//            wakeLock.acquire(10000)
//
//            val mediaPlayer = MediaPlayer().apply {
//                setAudioAttributes(
//                    AudioAttributes.Builder()
//                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//                        .setUsage(AudioAttributes.USAGE_ALARM)
//                        .build()
//                )
//                setDataSource(context, Settings.System.DEFAULT_ALARM_ALERT_URI)
//                isLooping = false
//                prepare()
//                start()
//                setOnCompletionListener {
//                    it.release()
//                    wakeLock.release()
//                }
//                setOnErrorListener { mp, _, _ ->
//                    mp.release()
//                    wakeLock.release()
//                    true
//                }
//            }
//            showLocalNotification("تم التنفيذ", "🔊 جاري تشغيل الصوت.")
//        } catch (e: Exception) {
//            Log.e("PLAY_SOUND_ERROR", "فشل تشغيل الصوت: ${e.message}")
//            showLocalNotification("خطأ", "فشل تشغيل الصوت: ${e.message}")
//        }
//    }
//
//    private fun setInstallAppsRestriction(disallowed: Boolean) {
//        setUserRestriction("no_install_apps", disallowed)
//    }
//
//    private fun setUninstallAppsRestriction(disallowed: Boolean) {
//        setUserRestriction("no_uninstall_apps", disallowed)
//    }
//
//    private fun setPlayStoreEnabled(enabled: Boolean) {
//        try {
//            dpm.setApplicationHidden(adminComponent, "com.android.vending", !enabled)
//            val status = if (enabled) "مفعل" else "معطل"
//            showLocalNotification("تم التنفيذ", "🛒 متجر Play أصبح $status.")
//        } catch (e: Exception) {
//            logPermissionError("setPlayStoreEnabled")
//        }
//    }
//
//    private fun enableSmsReceiving() {
//        try {
//            dpm.clearUserRestriction(adminComponent, "no_sms")
//            Log.d("SMS_CONTROL", "SMS receiving enabled")
//            showLocalNotification("تم التنفيذ", "تم تفعيل استقبال الأوامر عبر SMS.")
//        } catch (e: Exception) {
//            Log.e("SMS_CONTROL", "Error enabling SMS receiving: ${e.message}")
//        }
//    }
//
//    // --- دوال مساعدة ---
//
//    private fun logPermissionError(command: String?, extraInfo: String = "") {
//        val message = "صلاحيات مالك الجهاز (Device Owner) مطلوبة لتنفيذ الأمر: $command. $extraInfo"
//        Log.e("PERMISSION_ERROR", message)
//        showLocalNotification("فشل الأمر", message)
//    }
//
//    private fun logError(message: String) {
//        Log.e("EXECUTION_ERROR", message)
//        showLocalNotification("خطأ", message)
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun showLocalNotification(title: String, message: String) {
//        val channelId = "admin_commands_channel"
//        val notificationId = (System.currentTimeMillis() % 10000).toInt()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val name = "أوامر المسؤول"
//            val descriptionText = "إشعارات تنفيذ الأوامر الإدارية"
//            val importance = NotificationManager.IMPORTANCE_HIGH
//            val channel = NotificationChannel(channelId, name, importance).apply {
//                description = descriptionText
//            }
//            val notificationManager: NotificationManager =
//                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            notificationManager.createNotificationChannel(channel)
//        }
//
//        val notificationBuilder = NotificationCompat.Builder(context, channelId)
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .setContentTitle(title)
//            .setContentText(message)
//            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setAutoCancel(true)
//
//        val notificationManager = NotificationManagerCompat.from(context)
//        if (notificationManager.areNotificationsEnabled()) {
//            notificationManager.notify(notificationId, notificationBuilder.build())
//        } else {
//            Log.e("NOTIFICATION_ERROR", "Notification permission not granted.")
//        }
//    }
//}


package com.example.mdm_client

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.util.Base64 // **جديد:** لاستخدام Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import javax.crypto.Cipher // **جديد:** لاستخدام التشفير
import javax.crypto.spec.IvParameterSpec // **جديد:** لاستخدام التشفير
import javax.crypto.spec.SecretKeySpec // **جديد:** لاستخدام التشفير

/**
 * جديد: كلاس متخصص في فك تشفير أوامر SMS باستخدام AES.
 * يجب أن يتطابق المفتاح والمتجه مع تلك الموجودة في تطبيق الأدمن.
 */
object SmsCipher {
    // !!! مهم: يجب أن يكون هذا المفتاح والمتجه متطابقين تماماً مع تطبيق الأدمن
    private val key = "my32lengthsupersecretno123456789".toByteArray() // مفتاح 32 بايت (256 بت)
    private val iv = "my16lengthivno12".toByteArray() // متجه 16 بايت (128 بت)
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    fun decrypt(encryptedString: String): String? {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKeySpec = SecretKeySpec(key, ALGORITHM)
            val ivParameterSpec = IvParameterSpec(iv)

            // فك تشفير الرسالة من Base64 إلى بايتات
            val encryptedBytes = Base64.decode(encryptedString, Base64.DEFAULT)

            // تهيئة Cipher لفك التشفير
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

            // فك تشفير البايتات والحصول على النص الأصلي
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            // إرجاع null في حالة حدوث أي خطأ أثناء فك التشفير
            Log.e("SmsCipher", "فشل فك التشفير: ${e.message}")
            null
        }
    }
}


class SmsReceiver : BroadcastReceiver() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var context: Context

    override fun onReceive(context: Context, intent: Intent) {
        this.context = context
        dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                return // لا يوجد رسائل، قم بالخروج
            }

            // استخراج المعلومات من أول رسالة (عادةً ما تكون كافية)
            val firstMessage = messages.first()
            val fullMessageBody = messages.joinToString("") { it.messageBody }
            val sender = firstMessage.originatingAddress
            val timestamp = firstMessage.timestampMillis // الحصول على الطابع الزمني للرسالة

            // **تعديل جديد: توليد معرّف فريد للرسالة**
            val messageId = ProcessedSmsManager.generateMessageId(sender, fullMessageBody, timestamp)

            // **تعديل جديد: التحقق مما إذا تمت معالجة الرسالة من قبل**
            if (ProcessedSmsManager.isProcessed(context, messageId)) {
                Log.d("SMS_RECEIVER", "تم تجاهل الرسالة المكررة (ID: $messageId)")
                return // اخرج من الدالة فوراً إذا تمت معالجتها
            }

            Log.d("SMS_RECEIVER", "تم استقبال رسالة SMS جديدة من $sender")

            val decryptedMessage = SmsCipher.decrypt(fullMessageBody)

            if (decryptedMessage == null) {
                Log.w("SMS_RECEIVER", "الرسالة ليست أمراً مشفراً صالحاً. تم التجاهل.")
                return
            }

            Log.d("SMS_RECEIVER", "الرسالة بعد فك التشفير: $decryptedMessage")

            if (!decryptedMessage.startsWith("mdm_command:")) {
                Log.w("SMS_RECEIVER", "الرسالة لا تحتوي على البادئة 'mdm_command:'. تم التجاهل.")
                return
            }

            val commandString = decryptedMessage.substringAfter("mdm_command:")
            val parts = commandString.split(":", limit = 2)
            val command = parts[0].trim().lowercase()
            val data = if (parts.size > 1) parts[1].trim() else null

            // **تعديل جديد: تسجيل الرسالة كـ "معالجة" قبل تنفيذ الأمر**
            // هذا مهم جداً لمنع التنفيذ المتكرر في حال تعطل التطبيق أثناء التنفيذ
            ProcessedSmsManager.markAsProcessed(context, messageId)
            Log.d("SMS_RECEIVER", "تم تسجيل الرسالة كمعالجة (ID: $messageId)")

            executeCommand(command, data)
        }
    }

    private fun executeCommand(command: String, data: String?) {
        if (!dpm.isAdminActive(adminComponent)) {
            logError("التطبيق ليس مسؤول الجهاز. لا يمكن تنفيذ الأوامر.")
            return
        }

        val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)

        when (command) {
            // --- أوامر الأمان (Security) ---
            "lock" -> lockDevice()
            "wipe_device" -> if (isDeviceOwner) wipeDevice() else logPermissionError(command)
            "camera_disable" -> setCameraDisabled(true)
            "camera_enable" -> setCameraDisabled(false)
            "factory_reset_protection_disable" -> if (isDeviceOwner) setUserRestriction("no_factory_reset", true) else logPermissionError(command)
            "factory_reset_protection_enable" -> if (isDeviceOwner) setUserRestriction("no_factory_reset", false) else logPermissionError(command)

            // --- أوامر النظام (System) ---
            "reboot_device" -> if (isDeviceOwner) rebootDevice() else logPermissionError(command)
            "update_system" -> if (isDeviceOwner) installSystemUpdate() else logPermissionError(command)

            // --- أوامر التطبيقات (Apps) ---
            "install_app" -> {
                if (isDeviceOwner && data != null) installApp(data) else logPermissionError(command, "ورابط APK مطلوب")
            }
            "uninstall_app" -> {
                if (isDeviceOwner && data != null) uninstallApp(data) else logPermissionError(command, "واسم الحزمة مطلوب")
            }

            // --- أوامر التحكم في التطبيقات ---
            "install_apps_disable" -> if (isDeviceOwner) setUserRestriction("no_install_apps", true) else logPermissionError(command)
            "install_apps_enable" -> if (isDeviceOwner) setUserRestriction("no_install_apps", false) else logPermissionError(command)
            "uninstall_apps_disable" -> if (isDeviceOwner) setUserRestriction("no_uninstall_apps", true) else logPermissionError(command)
            "uninstall_apps_enable" -> if (isDeviceOwner) setUserRestriction("no_uninstall_apps", false) else logPermissionError(command)

            // --- أوامر التحكم في متجر Play ---
            "play_store_disable" -> if (isDeviceOwner) setPlayStoreEnabled(false) else logPermissionError(command)
            "play_store_enable" -> if (isDeviceOwner) setPlayStoreEnabled(true) else logPermissionError(command)

            // --- أوامر الشبكة (Network) ---
            "bluetooth_disable" -> if (isDeviceOwner) setUserRestriction("no_bluetooth", true) else logPermissionError(command)
            "bluetooth_enable" -> if (isDeviceOwner) setUserRestriction("no_bluetooth", false) else logPermissionError(command)
            "usb_data_disable" -> if (isDeviceOwner) setUserRestriction("no_usb_file_transfer", true) else logPermissionError(command)
            "usb_data_enable" -> if (isDeviceOwner) setUserRestriction("no_usb_file_transfer", false) else logPermissionError(command)

            // --- أوامر الموقع (Location) ---
            "gps_disable" -> setLocationEnabled(false)
            "gps_enable" -> setLocationEnabled(true)
            "locate_device" -> locateDevice()
            "play_sound" -> playSound()
            "enable_sms_commands" -> enableSmsReceiving()

            else -> Log.w("SMS_COMMAND", "أمر غير معروف أو غير مدعوم: $command")
        }
    }

    // ... (بقية دوال تنفيذ الأوامر تبقى كما هي بدون تغيير) ...
    // --- دوال تنفيذ الأوامر (منسوخة ومعدلة من MyFirebaseMessagingService) ---

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

    private fun installSystemUpdate() {
        logError("أمر تحديث النظام لم يتم تنفيذه بعد.")
    }

    private fun installApp(apkUrl: String) {
        logError("أمر تثبيت التطبيق لم يتم تنفيذه بعد.")
    }

    private fun uninstallApp(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
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
            val locationRestriction = "no_config_location"
            if (enabled) {
                dpm.clearUserRestriction(adminComponent, locationRestriction)
                showLocalNotification("تم التنفيذ", "📍 تم السماح بالتحكم في خدمات الموقع.")
            } else {
                dpm.addUserRestriction(adminComponent, locationRestriction)
                showLocalNotification("تم التنفيذ", "📍 تم منع المستخدم من تغيير إعدادات الموقع.")
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
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MDM:PlaySoundLock")
            wakeLock.acquire(10000)

            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(context, Settings.System.DEFAULT_ALARM_ALERT_URI)
                isLooping = false
                prepare()
                start()
                setOnCompletionListener {
                    it.release()
                    wakeLock.release()
                }
                setOnErrorListener { mp, _, _ ->
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

    private fun setPlayStoreEnabled(enabled: Boolean) {
        try {
            dpm.setApplicationHidden(adminComponent, "com.android.vending", !enabled)
            val status = if (enabled) "مفعل" else "معطل"
            showLocalNotification("تم التنفيذ", "🛒 متجر Play أصبح $status.")
        } catch (e: Exception) {
            logPermissionError("setPlayStoreEnabled")
        }
    }

    private fun enableSmsReceiving() {
        try {
            dpm.clearUserRestriction(adminComponent, "no_sms")
            Log.d("SMS_CONTROL", "SMS receiving enabled")
            showLocalNotification("تم التنفيذ", "تم تفعيل استقبال الأوامر عبر SMS.")
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
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(notificationId, notificationBuilder.build())
        } else {
            Log.e("NOTIFICATION_ERROR", "Notification permission not granted.")
        }
    }
}



/**
 * كلاس مساعد لإدارة معرّفات الرسائل التي تمت معالجتها باستخدام SharedPreferences.
 * هذا يمنع إعادة تنفيذ الأوامر بعد إعادة تشغيل الجهاز.
 */
object ProcessedSmsManager {
    private const val PREFS_NAME = "processed_sms_prefs"
    private const val PROCESSED_IDS_KEY = "processed_sms_ids"
    private const val MAX_IDS = 100 // حد أقصى لتخزين 100 معرّف لمنع امتلاء الذاكرة

    // دالة لتوليد معرّف فريد للرسالة
    fun generateMessageId(sender: String?, body: String?, timestamp: Long): String {
        // نستخدم Base64 لضمان أن المعرّف هو سلسلة نصية صالحة
        val rawId = "$sender:$body:$timestamp"
        return Base64.encodeToString(rawId.toByteArray(), Base64.NO_WRAP)
    }

    // دالة للتحقق مما إذا تمت معالجة الرسالة من قبل
    fun isProcessed(context: Context, messageId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val processedIds = prefs.getStringSet(PROCESSED_IDS_KEY, emptySet()) ?: emptySet()
        return processedIds.contains(messageId)
    }

    // دالة لإضافة معرّف رسالة إلى قائمة المعالجة
    fun markAsProcessed(context: Context, messageId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldIds = prefs.getStringSet(PROCESSED_IDS_KEY, mutableSetOf()) ?: mutableSetOf()

        // نستخدم نسخة جديدة قابلة للتعديل
        val newIds = HashSet(oldIds)
        newIds.add(messageId)

        // إذا تجاوزنا الحد الأقصى، نزيل أقدم المعرّفات (هذه طريقة بسيطة)
        // لتطبيق أكثر تعقيداً، يمكن ربط كل معرّف بتاريخ انتهاء صلاحية
        if (newIds.size > MAX_IDS) {
            // نحولها إلى قائمة ونزيل أول عنصر (الأقدم)
            val sortedList = newIds.toMutableList()
            sortedList.removeAt(0)
            // نعيد تحويلها إلى Set
            prefs.edit().putStringSet(PROCESSED_IDS_KEY, sortedList.toSet()).apply()
        } else {
            prefs.edit().putStringSet(PROCESSED_IDS_KEY, newIds).apply()
        }
    }
}

