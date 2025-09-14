package com.example.mdm_client

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdmin", "Device admin enabled")
        // تفعيل صلاحيات SMS عند تفعيل Device Admin
        enableSmsPermissions(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdmin", "Device admin disabled")
    }


    //sms
    private fun enableSmsPermissions(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

            // تفعيل صلاحيات SMS
            dpm.setPermissionPolicy(adminComponent, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)

            // تفعيل صلاحيات معينة
            dpm.addUserRestriction(adminComponent, "no_sms")
            dpm.clearUserRestriction(adminComponent, "no_sms")

            Log.d("DEVICE_ADMIN", "SMS permissions enabled")
        } catch (e: Exception) {
            Log.e("DEVICE_ADMIN", "Error enabling SMS permissions: ${e.message}")
        }
    }
}