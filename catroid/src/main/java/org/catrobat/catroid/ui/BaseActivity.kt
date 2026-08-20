/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2026 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catrobat.catroid.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.analytics.HitBuilders.ScreenViewBuilder
import org.catrobat.catroid.CatroidApplication
import org.catrobat.catroid.R
import org.catrobat.catroid.cast.CastManager
import org.catrobat.catroid.ui.runtimepermissions.PermissionHandlingActivity
import org.catrobat.catroid.ui.runtimepermissions.PermissionRequestActivityExtension
import org.catrobat.catroid.ui.runtimepermissions.RequiresPermissionTask
import org.catrobat.catroid.ui.settingsfragments.AccessibilityProfile
import org.catrobat.catroid.ui.settingsfragments.SettingsFragment

internal const val RECOVERED_FROM_CRASH = "RECOVERED_FROM_CRASH"

abstract class BaseActivity : AppCompatActivity(), PermissionHandlingActivity {
    lateinit var optionsMenu: Menu
    private val permissionRequestActivityExtension = PermissionRequestActivityExtension()
    protected var savedInstanceStateExpected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsFragment.setToChosenLanguage(this)
        applyAccessibilityStyles()

        Thread.setDefaultUncaughtExceptionHandler(BaseExceptionHandler(this))
        checkIfCrashRecoveryAndFinishActivity(this)
        checkIfProcessRecreatedAndFinishActivity(savedInstanceState)

        if (SettingsFragment.isCastSharedPreferenceEnabled(this)) {
            CastManager.getInstance().initializeCast(this)
        }
    }

    override fun onStart() {
        super.onStart()
        findViewById<Toolbar?>(R.id.toolbar)?.let {
            EdgeToEdge.applyTopPadding(it)
        }
        findViewById<View?>(R.id.bottom_bar)?.let {
            EdgeToEdge.applyBottomMargin(it)
        }
        findViewById<View?>(R.id.recycler_view)?.let {
            EdgeToEdge.applyBottomPadding(it)
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }
        optionsMenu = menu
        return super.onCreateOptionsMenu(menu)
    }

    private fun checkIfProcessRecreatedAndFinishActivity(savedInstanceState: Bundle?) {
        if (savedInstanceStateExpected || savedInstanceState == null || this is ProjectListActivity) {
            savedInstanceStateExpected = true
        } else {
            val activityName = javaClass.simpleName
            Log.e(
                activityName,
                "$activityName does not support recovery from process recreation, finishing activity."
            )
            finish()
        }
    }

    private fun applyAccessibilityStyles() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val profile = AccessibilityProfile.fromCurrentPreferences(sharedPreferences)
        profile.applyAccessibilityStyles(theme)
    }

    override fun onResume() {
        super.onResume()
        SettingsFragment.setToChosenLanguage(this)
        if (SettingsFragment.isCastSharedPreferenceEnabled(this)) {
            CastManager.getInstance().initializeCast(this)
        }

        invalidateOptionsMenu()
        googleAnalyticsTrackScreenResume()
    }

    protected fun googleAnalyticsTrackScreenResume() {
        val googleTracker = (application as CatroidApplication).defaultTracker
        googleTracker.setScreenName(this.javaClass.name)
        googleTracker.send(ScreenViewBuilder().build())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun checkIfCrashRecoveryAndFinishActivity(activity: Activity) {
        if (isRecoveringFromCrash) {
            if (activity is ProjectListActivity) {
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putBoolean(RECOVERED_FROM_CRASH, false)
                    .apply()
            } else {
                activity.finish()
            }
        }
    }

    private val isRecoveringFromCrash: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(RECOVERED_FROM_CRASH, false)

    override fun addToRequiresPermissionTaskList(task: RequiresPermissionTask) {
        permissionRequestActivityExtension.addToRequiresPermissionTaskList(task)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        permissionRequestActivityExtension.onRequestPermissionsResult(
            this,
            requestCode,
            permissions,
            grantResults
        )
    }
}
