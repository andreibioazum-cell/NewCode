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

package org.catrobat.catroid.koin

import android.app.Application
import com.google.android.gms.common.GoogleApiAvailability
import com.huawei.hms.api.HuaweiApiAvailability
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.work.WorkManager
import org.catrobat.catroid.ProjectManager
import org.catrobat.catroid.stage.HmsSpeechRecognitionHolder
import org.catrobat.catroid.stage.SpeechRecognitionHolder
import org.catrobat.catroid.stage.SpeechRecognitionHolderFactory
import org.catrobat.catroid.ui.controller.BackpackListManager
import org.catrobat.catroid.ui.recyclerview.repository.MqttPasswordRepository
import org.catrobat.catroid.ui.recyclerview.repository.DefaultMqttPasswordRepository
import org.catrobat.catroid.ui.settingsfragments.SettingsFragment.MQTT_ENCRYPTED_PREFS
import org.catrobat.catroid.utils.MobileServiceAvailability
import org.catrobat.catroid.utils.NetworkConnectionMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.module.Module
import org.koin.dsl.module

val componentsModules = module(createdAtStart = true, override = false) {
    factory { WorkManager.getInstance(androidContext()) }
    single { ProjectManager(androidContext()) }
    single { NetworkConnectionMonitor(androidContext()) }
    factory { HuaweiApiAvailability.getInstance() }
    factory { GoogleApiAvailability.getInstance() }
    factory { MobileServiceAvailability(get(), get()) }

    single { BackpackListManager.getInstance() }
}

val repositoryModules = module {
    single<MqttPasswordRepository> {
        DefaultMqttPasswordRepository(
            EncryptedSharedPreferences.create(
                MQTT_ENCRYPTED_PREFS,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                androidContext(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        )
    }
}

val speechModules = module {
    single { SpeechRecognitionHolder() }
    single { HmsSpeechRecognitionHolder() }
    single {
        SpeechRecognitionHolderFactory(
            get<SpeechRecognitionHolder>(),
            get<HmsSpeechRecognitionHolder>(),
            get()
        )
    }
}

val myModules = listOf(
    componentsModules, repositoryModules, speechModules
)

fun start(application: Application, modules: List<Module>) {
    startKoin {
        androidContext(application.applicationContext)
        androidLogger(Level.ERROR)
        modules(modules)
    }
}
