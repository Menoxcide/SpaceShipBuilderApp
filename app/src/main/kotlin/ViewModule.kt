package com.example.spaceshipbuilderapp

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext

@Module
@InstallIn(ActivityComponent::class)
object ViewModule {

    @Provides
    fun provideBuildView(@ActivityContext context: Context): BuildView {
        return BuildView(context)
    }

    @Provides
    fun provideFlightView(@ActivityContext context: Context): FlightView {
        return FlightView(context)
    }
}