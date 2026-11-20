package com.example

import android.app.Application
import com.example.data.NetworkMonitor
import com.example.data.PreferencesStore
import com.example.data.WalkieBackendClient
import com.example.data.WalkieFirestoreRepository
import com.example.voice.WalkieSessionManager
import com.google.firebase.firestore.FirebaseFirestore

class MyWalkieApp : Application() {
    lateinit var firestoreRepository: WalkieFirestoreRepository
        private set

    lateinit var preferencesStore: PreferencesStore
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var backendClient: WalkieBackendClient
        private set

    lateinit var sessionManager: WalkieSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        val databaseId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        firestoreRepository = WalkieFirestoreRepository(firestore)
        preferencesStore = PreferencesStore(this)
        networkMonitor = NetworkMonitor(this)
        backendClient = WalkieBackendClient()
        sessionManager = WalkieSessionManager(
            appContext = this,
            backendClient = backendClient,
            firestoreRepository = firestoreRepository,
            preferencesStore = preferencesStore
        )
    }
}
