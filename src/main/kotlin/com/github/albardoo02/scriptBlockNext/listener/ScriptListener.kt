package com.github.albardoo02.scriptBlockNext.listener

import com.github.albardoo02.scriptBlockNext.executor.ScriptExecutor
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerMoveEvent
import java.util.UUID

class ScriptListener : Listener {

    private val walkCooldowns = mutableMapOf<UUID, MutableMap<Location, Long>>()

    @EventHandler
    fun onBreak(event: BlockBreakEvent) {
        val scriptData = ScriptManager.getScript(event.block.location, "break") ?: return
        val player = event.player
        if (ScriptExecutor.hasInvalid(scriptData)) event.isCancelled = true
        ScriptExecutor.run(player, scriptData, event.block.location, if (player.isSneaking) setOf("shift") else emptySet())
    }

    @EventHandler
    fun onWalk(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return
        val player = event.player
        val floorBlock = to.clone().subtract(0.0, 0.1, 0.0).block
        val insideBlock = to.block
        var targetLoc = floorBlock.location
        var scriptData = ScriptManager.getScript(targetLoc, "walk")
        if (scriptData == null) {
            targetLoc = insideBlock.location
            scriptData = ScriptManager.getScript(targetLoc, "walk")
        }
        if (scriptData == null) return
        val now = System.currentTimeMillis()
        val playerCooldowns = walkCooldowns.getOrPut(player.uniqueId) { mutableMapOf() }
        val lastExecuted = playerCooldowns[targetLoc] ?: 0L
        if (now - lastExecuted < 1000) return
        playerCooldowns[targetLoc] = now
        if (ScriptExecutor.hasInvalid(scriptData)) event.isCancelled = true
        ScriptExecutor.run(player, scriptData, targetLoc, if (player.isSneaking) setOf("shift") else emptySet())
    }

    @EventHandler
    fun onHit(event: ProjectileHitEvent) {
        val shooter = event.entity.shooter
        if (shooter !is Player) return
        val block = event.hitBlock ?: return
        val scriptData = ScriptManager.getScript(block.location, "hit") ?: return
        ScriptExecutor.run(shooter, scriptData, block.location)
    }

    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.FALL) return
        val player = event.entity as? Player ?: return

        val expiryTime = ScriptManager.noFallPlayers[player.uniqueId] ?: return
        if (System.currentTimeMillis() < expiryTime) {
            event.isCancelled = true
        } else {
            ScriptManager.noFallPlayers.remove(player.uniqueId)
        }
    }
}
