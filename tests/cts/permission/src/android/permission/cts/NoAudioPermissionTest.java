/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.filters.SmallTest;

/**
 * Verify the audio related operations require specific permissions.
 */
public class NoAudioPermissionTest extends AndroidTestCase {
    private static final String TAG = NoAudioPermissionTest.class.getSimpleName();
    private AudioManager mAudioManager;
    private static final int MODE_COUNT = 3;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        assertNotNull(mAudioManager);
    }

    private boolean hasMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    /**
     * Verify that AudioManager.setMicrophoneMute, AudioManager.setMode requires permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#MODIFY_AUDIO_SETTINGS}.
     */
    @SmallTest
    public void testSetMicrophoneMute() {
        boolean muteState = mAudioManager.isMicrophoneMute();
        int originalMode = mAudioManager.getMode();
        // If there is no permission of MODIFY_AUDIO_SETTINGS, setMicrophoneMute does nothing.
        if (muteState) {
            Log.w(TAG, "Mic seems muted by hardware! Please unmute and rerrun the test.");
        } else {
            mAudioManager.setMicrophoneMute(!muteState);
            assertEquals(muteState, mAudioManager.isMicrophoneMute());
        }

        // If there is no permission of MODIFY_AUDIO_SETTINGS, setMode does nothing.
        assertTrue(AudioManager.MODE_NORMAL != AudioManager.MODE_RINGTONE);

        mAudioManager.setMode(AudioManager.MODE_NORMAL);
        assertEquals(originalMode, mAudioManager.getMode());

        mAudioManager.setMode(AudioManager.MODE_RINGTONE);
        assertEquals(originalMode, mAudioManager.getMode());
    }

    /**
     * Verify that AudioManager routing methods require permissions.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#MODIFY_AUDIO_SETTINGS}.
     */
    @SuppressWarnings("deprecation")
    @SmallTest
    public void testRouting() {

        // If there is no permission of MODIFY_AUDIO_SETTINGS, setSpeakerphoneOn does nothing.
        boolean prevState = mAudioManager.isSpeakerphoneOn();
        mAudioManager.setSpeakerphoneOn(!prevState);
        assertEquals(prevState, mAudioManager.isSpeakerphoneOn());

        // If there is no permission of MODIFY_AUDIO_SETTINGS, setBluetoothScoOn does nothing.
        prevState = mAudioManager.isBluetoothScoOn();
        mAudioManager.setBluetoothScoOn(!prevState);
        assertEquals(prevState, mAudioManager.isBluetoothScoOn());
    }

    /**
     * Verify that {@link android.media.AudioRecord.Builder#build} and
     * {@link android.media.AudioRecord#AudioRecord} require permission
     * {@link android.Manifest.permission#RECORD_AUDIO}.
     */
    @SmallTest
    public void testRecordPermission() {
        if (!hasMicrophone()) return;

        // test builder
        assertThrows(java.lang.UnsupportedOperationException.class, () -> {
            final AudioRecord record = new AudioRecord.Builder().build();
            record.release();
        });

        // test constructor
        final int sampleRate = 8000;
        final int halfSecondInBytes = sampleRate;
        AudioRecord record = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, halfSecondInBytes);
        final int state = record.getState();
        record.release();
        assertEquals(AudioRecord.STATE_UNINITIALIZED, state);
    }
}
