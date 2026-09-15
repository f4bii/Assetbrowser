package de.fabihbbbt.assetbrowser.client.gui.index

import net.minecraft.client.Minecraft
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ReloadableResourceManager
import java.util.concurrent.CompletableFuture

object AssetIndexCache {
    private var cached: AssetIndex? = null
    private var listenerRegistered = false

    @Synchronized
    fun getOrBuild(minecraft: Minecraft): AssetIndex {
        ensureInvalidationHook(minecraft)
        val existing = cached
        if (existing != null) {
            return existing
        }
        val fresh = AssetIndex(minecraft.resourceManager)
        cached = fresh
        return fresh
    }

    private fun ensureInvalidationHook(minecraft: Minecraft) {
        if (listenerRegistered) {
            return
        }
        listenerRegistered = true
        (minecraft.resourceManager as ReloadableResourceManager).registerReloadListener(invalidationListener)
    }

    val invalidationListener: PreparableReloadListener =
        PreparableReloadListener { _, _, barrier, _ ->
            val stale =
                synchronized(AssetIndexCache) {
                    val previous = cached
                    cached = null
                    previous
                }
            val scanStopped: CompletableFuture<Void> = stale?.cancelScan() ?: CompletableFuture.completedFuture(null)
            scanStopped.thenCompose { barrier.wait(net.minecraft.util.Unit.INSTANCE) }.thenRun {}
        }
}
