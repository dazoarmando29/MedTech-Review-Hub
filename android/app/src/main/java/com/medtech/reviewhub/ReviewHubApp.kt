package com.medtech.reviewhub

import android.app.Application
import com.medtech.reviewhub.di.AppContainer

class ReviewHubApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        instance = this
    }

    companion object {
        /** Used by SyncWorker and AppBlockerService (manual DI). */
        @Volatile
        var instance: ReviewHubApp? = null
            private set

        val container: AppContainer?
            get() = instance?.container
    }
}
