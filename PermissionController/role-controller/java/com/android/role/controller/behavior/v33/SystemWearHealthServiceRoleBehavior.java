/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.role.controller.behavior.v33;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;

/** The role behavior for System Wear Health Service. */
public class SystemWearHealthServiceRoleBehavior implements RoleBehavior {
    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        // Role is only available on Watches.
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
