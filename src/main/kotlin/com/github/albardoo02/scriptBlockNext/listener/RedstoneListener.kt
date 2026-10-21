package com.github.albardoo02.scriptBlockNext.listener

import com.github.albardoo02.scriptBlockNext.data.RedstoneSetting
import com.github.albardoo02.scriptBlockNext.executor.ScriptExecutor
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockRedstoneEvent

/** レッドストーン信号(OFF→ON)で、設定済みスクリプトを選択したプレイヤーに対して実行する */
class RedstoneListener : Listener {

    private val faces = listOf(
        BlockFace.SELF, BlockFace.UP, BlockFace.DOWN,
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    )

    @EventHandler
    fun onRedstone(event: BlockRedstoneEvent) {
        if (event.oldCurrent > 0 || event.newCurrent <= 0) return
        for (face in faces) {
            val block = event.block.getRelative(face)
            for (type in ScriptManager.scripts.keys.toList()) {
                val data = ScriptManager.getScript(block.location, type) ?: continue
                val setting = data.redstone ?: continue
                for (target in select(block, setting)) {
                    ScriptExecutor.run(target, data, block.location)
                }
            }
        }
    }

    private fun select(block: Block, setting: RedstoneSetting): List<Player> {
        val selector = setting.selector.trim()
        val kind = selector.substring(0, minOf(2, selector.length)).lowercase()
        val args = selector.substringAfter("[", "").removeSuffix("]")
            .split(",").filter { it.contains("=") }
            .associate { it.substringBefore("=").trim().lowercase() to it.substringAfter("=").trim() }
        val maxDistance = (args["r"] ?: args["distance"])?.toDoubleOrNull()
        val center = block.location.add(0.5, 0.5, 0.5)

        var players = block.world.players.filter { p ->
            maxDistance == null || p.location.distanceSquared(center) <= maxDistance * maxDistance
        }
        setting.filterOp?.let { op -> players = players.filter { it.isOp == op } }
        setting.filterPerm?.let { perm -> players = players.filter { it.hasPermission(perm) } }

        players = when (kind) {
            "@p" -> players.sortedBy { it.location.distanceSquared(center) }.take(1)
            "@r" -> players.shuffled().take(1)
            else -> players.sortedBy { it.location.distanceSquared(center) }
        }
        val limit = setting.filterLimit
        return if (limit != null) players.take(limit) else players
    }
}
