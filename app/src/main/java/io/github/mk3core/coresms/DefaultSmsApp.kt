package io.github.mk3core.coresms

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Holding the default SMS app role, and the runtime permissions that go with it. */
object DefaultSmsApp {

    fun isDefault(context: Context): Boolean {
        val roles = context.getSystemService(RoleManager::class.java)
        return roles.isRoleAvailable(RoleManager.ROLE_SMS) && roles.isRoleHeld(RoleManager.ROLE_SMS)
    }

    /** The system dialog that asks to make CoreSMS the default, or null if the phone has no SMS role. */
    fun requestIntent(context: Context): Intent? {
        val roles = context.getSystemService(RoleManager::class.java)
        return if (roles.isRoleAvailable(RoleManager.ROLE_SMS)) {
            roles.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            null
        }
    }

    private val permissions = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_MMS)
        add(Manifest.permission.RECEIVE_WAP_PUSH)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_PHONE_NUMBERS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun missingPermissions(context: Context): List<String> = permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}
