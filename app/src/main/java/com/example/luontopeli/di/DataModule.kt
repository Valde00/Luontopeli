package com.example.luontopeli.di

import android.content.Context
import androidx.room.Room
import com.example.luontopeli.data.local.AppDatabase
import com.example.luontopeli.data.local.dao.NatureSpotDao
import com.example.luontopeli.data.local.dao.WalkSessionDao
import com.example.luontopeli.data.remote.firebase.AuthManager
import com.example.luontopeli.data.remote.firebase.FirestoreManager
import com.example.luontopeli.data.remote.firebase.StorageManager
import com.example.luontopeli.data.repository.NatureSpotRepository
import com.example.luontopeli.ml.PlantClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return AppDatabase.getDatabase(appContext)
    }

    @Provides
    fun provideWalkSessionDao(db: AppDatabase): WalkSessionDao = db.walkSessionDao()

    @Provides
    fun provideNatureSpotDao(db: AppDatabase): NatureSpotDao = db.natureSpotDao()

    @Provides
    @Singleton
    fun provideAuthManager(): AuthManager = AuthManager()

    @Provides
    @Singleton
    fun provideFirestoreManager(): FirestoreManager = FirestoreManager()

    @Provides
    @Singleton
    fun provideStorageManager(): StorageManager = StorageManager()

    @Provides
    @Singleton
    fun provideNatureSpotRepository(
        dao: NatureSpotDao,
        firestoreManager: FirestoreManager,
        storageManager: StorageManager,
        authManager: AuthManager
    ): NatureSpotRepository {
        return NatureSpotRepository(
            dao = dao,
            firestoreManager = firestoreManager,
            storageManager = storageManager,
            authManager = authManager
        )
    }

    @Provides
    @Singleton
    fun providePlantClassifier(): PlantClassifier = PlantClassifier()
}
