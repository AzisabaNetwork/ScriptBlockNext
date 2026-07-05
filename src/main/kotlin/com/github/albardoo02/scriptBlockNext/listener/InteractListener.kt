package com.github.albardoo02.scriptBlockNext.listener

import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.executor.ScriptExecutor
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import com.github.albardoo02.scriptBlockNext.manager.sendMsg
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot

class InteractListener: Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event. hand == EquipmentSlot.OFF_HAND) return
        val player = event.player
        val clickedBlock = event.clickedBlock ?: return
        val location = clickedBlock.location

        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type == org.bukkit.Material.BLAZE_ROD && itemInHand.itemMeta?.displayName == "§bSBN Block Selector") {
            event.isCancelled = true
            if (event.action == Action.LEFT_CLICK_BLOCK) {
                com.github.albardoo02.scriptBlockNext.manager.SelectorManager.setPos1(player, location)
            } else if (event.action == Action.RIGHT_CLICK_BLOCK) {
                com.github.albardoo02.scriptBlockNext.manager.SelectorManager.setPos2(player, location)
            }
            return
        }

        if (ScriptManager.creationMode.containsKey(player.uniqueId)) {
            val (type, commands) = ScriptManager.creationMode.remove(player.uniqueId) ?: return
            ScriptManager.addScript(location, ScriptData(commands, type, player.uniqueId))
            player.sendMsg("success_registered", "type" to type)
            event.isCancelled = true
            return
        }

        if (ScriptManager.addMode.containsKey(player.uniqueId)) {
            val (type, newCommands) = ScriptManager.addMode.remove(player.uniqueId) ?: return
            val existingData = ScriptManager.getScript(location, type)

            if (existingData != null) {
                val combinedCommands = existingData.commands + newCommands
                val newData = existingData.copy(commands = combinedCommands)
                ScriptManager.addScript(location, newData)
                player.sendMsg("success_added", "type" to type, "count" to combinedCommands.size.toString())
            } else {
                ScriptManager.addScript(location, ScriptData(newCommands, type, player.uniqueId))
                player.sendMsg("success_registered", "type" to type)
            }
            event.isCancelled = true
            return
        }

        val removeType = ScriptManager.removalMode.remove(player.uniqueId)
        if (removeType != null) {
            ScriptManager.removeScript(location, removeType)
            player.sendMsg("success_removed", "type" to removeType)
            event.isCancelled = true
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.LEFT_CLICK_BLOCK) return
        val scriptData = ScriptManager.getScript(location, "interact") ?: return

        if (player.isSneaking && (player.isOp || player.hasPermission("scriptblocknext.admin"))) return

        val hasCancelFalse = scriptData.commands.any {
            val cleanCmd = it.removePrefix("!")
            cleanCmd.startsWith("@cancelEvent:false", ignoreCase = true)
        }
        if (!hasCancelFalse) {
            event.isCancelled = true
        }
        
        ScriptExecutor.run(player, scriptData, location)
    }
}