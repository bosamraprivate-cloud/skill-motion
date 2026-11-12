package com.aitherapist.dls.ui.sample

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Source of sample items. Stands in for a real repository to demonstrate the AD-13 pattern. */
interface SampleRepository {
    fun items(): Flow<List<String>>
}

@Singleton
class FakeSampleRepository
@Inject
constructor() : SampleRepository {
    override fun items(): Flow<List<String>> = flow {
        delay(FAKE_LATENCY_MS)
        emit(listOf("Item 1", "Item 2", "Item 3"))
    }

    private companion object {
        const val FAKE_LATENCY_MS = 400L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SampleModule {
    @Binds
    abstract fun bindSampleRepository(impl: FakeSampleRepository): SampleRepository
}
