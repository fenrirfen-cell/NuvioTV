package com.nuvio.tv.core.di

import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import com.nuvio.tv.core.profile.SpanishOnlyStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class SpanishOnlyModule {
    /** Lets ProfileManager clear the setting when a profile is deleted. */
    @Binds
    @IntoSet
    abstract fun bindSpanishOnlyProfileStore(store: SpanishOnlyStore): ProfileScopedCredentialStore
}
