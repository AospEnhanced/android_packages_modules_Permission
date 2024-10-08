/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.permission.cts

import android.Manifest.permission.CAMERA
import android.Manifest.permission.RECORD_AUDIO
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiObjectNotFoundException
import com.android.compatibility.common.util.FeatureUtil
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Tests that the permissioncontroller behaves normally when an app defines a permission in the
 * android.permission-group.UNDEFINED group
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class UndefinedGroupPermissionTest {
    private var mInstrumentation: Instrumentation? = null
    private var mUiDevice: UiDevice? = null
    private var mContext: Context? = null
    private var mPm: PackageManager? = null
    private var mAllowButtonText: Pattern? = null
    private var mDenyButtonText: Pattern? = null

    @Before
    fun install() {
        SystemUtil.runShellCommand("pm uninstall $APP_PKG_NAME")
        SystemUtil.runShellCommandOrThrow(
            "pm install -r " + TEST_APP_DEFINES_UNDEFINED_PERMISSION_GROUP_ELEMENT_APK
        )
    }

    @Before
    fun setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation()
        mUiDevice = UiDevice.getInstance(mInstrumentation!!)
        mContext = mInstrumentation?.targetContext
        mPm = mContext?.packageManager
        val permissionControllerResources =
            mContext
                ?.createPackageContext(mContext?.packageManager?.permissionControllerPackageName, 0)
                ?.resources
        mAllowButtonText =
            Pattern.compile(
                Pattern.quote(
                    requireNotNull(
                        permissionControllerResources?.getString(
                            permissionControllerResources.getIdentifier(
                                "grant_dialog_button_allow",
                                "string",
                                "com.android.permissioncontroller"
                            )
                        )
                    )
                ),
                Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            )
        mDenyButtonText =
            Pattern.compile(
                Pattern.quote(
                    requireNotNull(
                        permissionControllerResources?.getString(
                            permissionControllerResources.getIdentifier(
                                "grant_dialog_button_deny",
                                "string",
                                "com.android.permissioncontroller"
                            )
                        )
                    )
                ),
                Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            )
    }

    @Before
    fun wakeUpScreenAndUnlock() {
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP")
        SystemUtil.runShellCommand("input keyevent KEYCODE_MENU")
    }

    @Test
    fun testOtherGroupPermissionsNotGranted_1() {
        testOtherGroupPermissionsNotGranted(CAMERA, RECORD_AUDIO)
    }

    @Test
    fun testOtherGroupPermissionsNotGranted_2() {
        testOtherGroupPermissionsNotGranted(TEST, RECORD_AUDIO)
    }

    @Test
    fun testOtherGroupPermissionsNotGranted_3() {
        testOtherGroupPermissionsNotGranted(CAMERA, TEST)
    }

    /** When the custom permission is granted nothing else gets granted as a byproduct. */
    @Test
    fun testCustomPermissionGrantedAlone() {
        Assert.assertEquals(
            PackageManager.PERMISSION_DENIED,
            mPm!!.checkPermission(CAMERA, APP_PKG_NAME)
        )
        Assert.assertEquals(
            PackageManager.PERMISSION_DENIED,
            mPm!!.checkPermission(RECORD_AUDIO, APP_PKG_NAME)
        )
        Assert.assertEquals(
            PackageManager.PERMISSION_DENIED,
            mPm!!.checkPermission(TEST, APP_PKG_NAME)
        )
        eventually {
            startRequestActivity(arrayOf(TEST))
            mUiDevice!!.waitForIdle()
            Thread.sleep(2000)
            findAllowButton().click()
        }
        eventually {
            Assert.assertEquals(
                PackageManager.PERMISSION_DENIED,
                mPm!!.checkPermission(CAMERA, APP_PKG_NAME)
            )
            Assert.assertEquals(
                PackageManager.PERMISSION_DENIED,
                mPm!!.checkPermission(RECORD_AUDIO, APP_PKG_NAME)
            )
            Assert.assertEquals(
                PackageManager.PERMISSION_GRANTED,
                mPm!!.checkPermission(TEST, APP_PKG_NAME)
            )
        }
    }

    @After
    fun uninstall() {
        SystemUtil.runShellCommand("pm uninstall $APP_PKG_NAME")
    }

    fun findAllowButton(): UiObject2 {
         return if (FeatureUtil.isAutomotive() || FeatureUtil.isWatch()) {
            waitFindObject(By.text(mAllowButtonText!!))
        } else {
            waitFindObject(
                By.res("com.android.permissioncontroller:id/permission_allow_button"),
                2000
            )
        }
    }

    /**
     * If app has one permission granted, then it can't grant itself another permission for free.
     */
    fun testOtherGroupPermissionsNotGranted(grantedPerm: String, targetPermission: String) {
        // Grant the permission in the background
        SystemUtil.runWithShellPermissionIdentity {
            mPm!!.grantRuntimePermission(APP_PKG_NAME, grantedPerm, Process.myUserHandle())
        }
        Assert.assertEquals(
            "$grantedPerm not granted.",
            PackageManager.PERMISSION_GRANTED,
            mPm!!.checkPermission(grantedPerm, APP_PKG_NAME)
        )

        // If the dialog shows, success. If not then either the UI is broken or the permission was
        // granted in the background.
        eventually {
            startRequestActivity(arrayOf(targetPermission))
            mUiDevice!!.waitForIdle()
            try {
                if (FeatureUtil.isAutomotive() || FeatureUtil.isWatch()) {
                    waitFindObject(By.text(mDenyButtonText!!))
                } else if (
                    mContext?.packageManager?.hasSystemFeature(PackageManager.FEATURE_WATCH) == true
                ) {
                    waitFindObject(By.res(ALLOW_BUTTON), 2000)
                } else {
                    waitFindObject(By.res(GRANT_DIALOG), 2000)
                }
            } catch (e: UiObjectNotFoundException) {
                Assert.assertEquals(
                    "grant dialog never showed.",
                    PackageManager.PERMISSION_GRANTED,
                    mPm!!.checkPermission(targetPermission, APP_PKG_NAME)
                )
            }
        }
        Assert.assertEquals(
            PackageManager.PERMISSION_DENIED,
            mPm!!.checkPermission(targetPermission, APP_PKG_NAME)
        )
    }

    private fun startRequestActivity(permissions: Array<String>) {
        mContext!!.startActivity(
            Intent()
                .setComponent(ComponentName(APP_PKG_NAME, "$APP_PKG_NAME.RequestPermissions"))
                .putExtra(EXTRA_PERMISSIONS, permissions)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        private const val TEST_APP_DEFINES_UNDEFINED_PERMISSION_GROUP_ELEMENT_APK =
            "/data/local/tmp/cts-permission/AppThatDefinesUndefinedPermissionGroupElement.apk"
        private const val APP_PKG_NAME = "android.permission.cts.appthatrequestpermission"
        private const val EXTRA_PERMISSIONS =
            "android.permission.cts.appthatrequestpermission.extra.PERMISSIONS"
        private const val GRANT_DIALOG = "com.android.permissioncontroller:id/grant_dialog"
        private const val ALLOW_BUTTON =
            "com.android.permissioncontroller:id/permission_allow_button"
        const val TEST = "android.permission.cts.appthatrequestpermission.TEST"
    }
}
