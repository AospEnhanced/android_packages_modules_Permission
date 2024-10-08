/*
 * Copyright (C) 2017 The Android Open Source Project
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


package android.permission.cts;

import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;

import static org.junit.Assert.assertThrows;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.platform.test.annotations.AppModeFull;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

import org.junit.function.ThrowingRunnable;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Verify that Wallpaper-related operations enforce the correct permissions.
 */
@AppModeFull(reason = "instant apps cannot access the WallpaperManager")
public class NoWallpaperPermissionsTest extends AndroidTestCase {
    private WallpaperManager mWM;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWM = (WallpaperManager) mContext.getSystemService(Context.WALLPAPER_SERVICE);
    }

    /**
     * Verify that the setResource(...) methods enforce the SET_WALLPAPER permission
     */
    @SmallTest
    public void testSetResource() throws IOException {
        if (wallpaperNotSupported()) {
            return;
        }

        try {
            mWM.setResource(R.drawable.robot);
            fail("WallpaperManager.setResource(id) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setResource(R.drawable.robot, FLAG_LOCK);
            fail("WallpaperManager.setResource(id, which) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }
    }

    /**
     * Verify that the setBitmap(...) methods enforce the SET_WALLPAPER permission
     */
    @SmallTest
    public void testSetBitmap() throws IOException  {
        if (wallpaperNotSupported()) {
            return;
        }

        Bitmap b = Bitmap.createBitmap(160, 120, Bitmap.Config.RGB_565);

        try {
            mWM.setBitmap(b);
            fail("setBitmap(b) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setBitmap(b, null, false);
            fail("setBitmap(b, crop, allowBackup) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setBitmap(b, null, false, FLAG_SYSTEM);
            fail("setBitmap(b, crop, allowBackup, which) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }
    }

    /**
     * Verify that the setStream(...) methods enforce the SET_WALLPAPER permission
     */
    @SmallTest
    public void testSetStream() throws IOException  {
        if (wallpaperNotSupported()) {
            return;
        }

        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[32]);

        try {
            mWM.setStream(stream);
            fail("setStream(stream) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setStream(stream, null, false);
            fail("setStream(stream, crop, allowBackup) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.setStream(stream, null, false, FLAG_LOCK);
            fail("setStream(stream, crop, allowBackup, which) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }
    }

    /**
     * Verify that the clearWallpaper(...) methods enforce the SET_WALLPAPER permission
     */
    @SmallTest
    public void testClearWallpaper() throws IOException  {
        if (wallpaperNotSupported()) {
            return;
        }

        try {
            mWM.clear();
            fail("clear() did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }

        try {
            mWM.clear(FLAG_SYSTEM);
            fail("clear(which) did not enforce SET_WALLPAPER");
        } catch (SecurityException expected) { /* expected */ }
    }

    /**
     * Verify that reading the current wallpaper enforce the READ_WALLPAPER_INTERNAL permission.
     * The methods concerned are:
     * getDrawable, peekDrawable, getFastDrawable, peekFastDrawable, getWallpaperFile.
     *
     * These methods should throw a SecurityException, even if MANAGE_EXTERNAL_STORAGE is granted.
     */
    public void testReadWallpaper() {
        if (wallpaperNotSupported()) {
            return;
        }
        String message = "with no permissions, getDrawable should throw a SecurityException";
        assertSecurityException(mWM::getDrawable, message);
        assertSecurityException(() -> mWM.getDrawable(FLAG_SYSTEM), message);
        assertSecurityException(() -> mWM.getDrawable(FLAG_LOCK), message);

        message = "with no permissions, peekDrawable should throw a SecurityException";
        assertSecurityException(mWM::peekDrawable, message);
        assertSecurityException(() -> mWM.peekDrawable(FLAG_SYSTEM), message);
        assertSecurityException(() -> mWM.peekDrawable(FLAG_LOCK), message);

        message = "with no permissions, getFastDrawable should throw a SecurityException";
        assertSecurityException(mWM::getFastDrawable, message);
        assertSecurityException(() -> mWM.getFastDrawable(FLAG_SYSTEM), message);
        assertSecurityException(() -> mWM.getFastDrawable(FLAG_LOCK), message);

        message = "with no permissions, peekFastDrawable should throw a SecurityException";
        assertSecurityException(mWM::peekFastDrawable, message);
        assertSecurityException(() -> mWM.peekFastDrawable(FLAG_SYSTEM), message);
        assertSecurityException(() -> mWM.peekFastDrawable(FLAG_LOCK), message);

        message = "with no permissions, getWallpaperFile should throw a SecurityException";
        assertSecurityException(() -> mWM.getWallpaperFile(FLAG_SYSTEM), message);
        assertSecurityException(() -> mWM.getWallpaperFile(FLAG_LOCK), message);
    }

    // ---------- Utility methods ----------

    private boolean wallpaperNotSupported() {
        return !(mWM.isWallpaperSupported() && mWM.isSetWallpaperAllowed());
    }

    private void assertSecurityException(ThrowingRunnable runnable, String errorMessage) {
        assertThrows(errorMessage, SecurityException.class, runnable);
    }
}
