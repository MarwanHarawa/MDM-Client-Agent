
package com.example.mdm_client

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {


//    fun hideApp(packageName: String) {
//        try {
//            val success = devicePolicyManager.setApplicationHidden(compName, packageName, true)
//            if (success) {
//                showLocalNotification("تم التنفيذ", "🕵️‍♂️ تم إخفاء التطبيق: $packageName")
//            } else {
//                showLocalNotification("فشل التنفيذ", "❌ فشل إخفاء التطبيق: $packageName")
//            }
//        } catch (e: Exception) {
//            showLocalNotification("خطأ", "❌ ${e.message}")
//        }
//    }


    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var compName: ComponentName

    override fun onCreate() {
        super.onCreate()
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, MyDeviceAdminReceiver::class.java)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.data.let { data ->
            val command = data["command"]
            Log.d("FCM", "Command received: $command")

            when (command) {
                "lock" -> {
                    lockDevice()
                    showLocalNotification("تم تنفيذ أمر", "🔒 تم قفل الجهاز")
                }
                "unlock" -> {
                    showLocalNotification("تم تنفيذ أمر", "🔒 تم فتح الجهاز")
                }
                "disable_camera" -> {
//                    try {
//                        devicePolicyManager.setCameraDisabled(compName, true)
//                        showLocalNotification("تم تنفيذ أمر", "📷 تم تعطيل الكاميرا")
//                    } catch (e: Exception) {
//                        Log.e("CameraDisableError", "فشل تعطيل الكاميرا: ${e.message}")
//                        showLocalNotification("فشل تنفيذ الأمر", "❌ ${e.message}")
//                    }

                    devicePolicyManager.setCameraDisabled(compName, true)
                    showLocalNotification("تم تنفيذ أمر", "📷 تم تعطيل الكاميرا")
                }
                "enable_camera" -> {
                    devicePolicyManager.setCameraDisabled(compName, false)
                    showLocalNotification("تم تنفيذ أمر", "📷 تم تفعيل الكاميرا")
                }
                "disable_playstore" -> {
                    disablePackage("com.android.vending", true)
                    showLocalNotification("تم تنفيذ أمر", "🛑 تم تعطيل متجر Google Play")
                }
                "enable_playstore" -> {
                    disablePackage("com.android.vending", false)
                    showLocalNotification("تم تنفيذ أمر", "✅ تم تفعيل متجر Google Play")
                }
                "disable_bluetooth" -> {
                    disableBluetooth(true)
                    showLocalNotification("تم تنفيذ أمر", "🔇 تم تعطيل البلوتوث")
                }
                "enable_bluetooth" -> {
                    disableBluetooth(false)
                    showLocalNotification("تم تنفيذ أمر", "🎧 تم تفعيل البلوتوث")
                }
                // يمكنك إضافة أوامر إضافية هنا
                "reboot_device" -> {
                    val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

                    try {
                        devicePolicyManager.reboot(componentName)
                        showLocalNotification("تم تنفيذ أمر", "🔄 يتم الآن إعادة تشغيل الجهاز")
                    } catch (e: SecurityException) {
                        showLocalNotification("فشل الأمر", "❌ التطبيق لا يملك صلاحيات Device Owner")
                    }
                }

//                "hide_app" -> {
//                    val packageName = data["package_name"]
//                    if (!packageName.isNullOrEmpty()) {
//                        hideApp(packageName)
//                    } else {
//                        showLocalNotification("خطأ", "❌ لم يتم تحديد اسم التطبيق")
//                    }
//                }


            }
        }
    }

    private fun lockDevice() {
        devicePolicyManager.lockNow()
    }

    private fun disableCamera(disable: Boolean) {
        devicePolicyManager.setCameraDisabled(compName, disable)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun disableBluetooth(disable: Boolean) {
        // يقيّد مشاركة جهات الاتصال عبر البلوتوث (محدود نوعًا ما)
        devicePolicyManager.setBluetoothContactSharingDisabled(compName, disable)
    }

    private fun disablePackage(packageName: String, hidden: Boolean) {
        devicePolicyManager.setApplicationHidden(compName, packageName, hidden)
    }

    @SuppressLint("MissingPermission")
    private fun showLocalNotification(title: String, message: String) {
        val channelId = "admin_commands_channel"
        val notificationId = (System.currentTimeMillis() % 10000).toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "أوامر المسؤول"
            val descriptionText = "إشعارات aتنفيذ الأوامر الإدارية"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, notificationBuilder.build())
        }
    }
}
