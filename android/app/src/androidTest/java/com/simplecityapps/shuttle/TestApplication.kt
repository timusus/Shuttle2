package com.simplecityapps.shuttle

import android.app.Application
import com.simplecityapps.shuttle.di.AppGraphOwner
import com.simplecityapps.shuttle.di.TestAppGraph
import dev.zacsweers.metro.createGraphFactory

/**
 * The instrumented tests' application. Like a bare test application, it runs none of [ShuttleApplication]'s
 * start-up; each test calls [newGraph] so nothing, the in-memory database included, carries over between tests.
 */
class TestApplication :
    Application(),
    AppGraphOwner {
    private var graph: TestAppGraph? = null

    override val appGraph: TestAppGraph
        get() = graph ?: newGraph()

    fun newGraph(): TestAppGraph = createGraphFactory<TestAppGraph.Factory>().create(this).also { graph = it }
}
