package com.github.albardoo02.scriptBlockNext.executor

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.hook.DiscordSRVManager
import com.github.albardoo02.scriptBlockNext.hook.LuckPermsManager
import com.github.albardoo02.scriptBlockNext.hook.MythicMobsManager
import com.github.albardoo02.scriptBlockNext.hook.PlaceholderManager
import com.github.albardoo02.scriptBlockNext.hook.VaultManager
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import com.github.albardoo02.scriptBlockNext.manager.sendMsg
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID

object ScriptExecutor {

    private val playerCooldowns = mutableMapOf<UUID, MutableMap<Location, Long>>()
    private val globalCooldowns = mutableMapOf<Location, Long>()
    private val playerAmounts = mutableMapOf<UUID, MutableMap<Location, Int>>()
    private val globalAmounts = mutableMapOf<Location, Int>()

    fun run(player: Player, scriptData: ScriptData, location: Location, actionType: String = "") {
        if (ScriptBlockNext.instance.config.getBoolean("output_script_log", false)) {
            val worldName = location.world?.name ?: "unknown"
            ScriptBlockNext.instance.logger.info(
                "Player ${player.name} executed ${scriptData.type} script at $worldName, ${location.blockX}, ${location.blockY}, ${location.blockZ}"
            )
        }

        var requiredMoney = 0.0
        val requiredItems = mutableListOf<ItemStack>()
        val requiredMythicItems = mutableMapOf<String, Int>()
        var cooldownTime = 0
        var oldCooldownTime = 0
        
        for (line in scriptData.commands) {
            val cmd = PlaceholderManager.replace(player, line)
            val cleanCmd = cmd.removePrefix("!")
            if (cleanCmd.startsWith("@cooldown:")) cooldownTime = cleanCmd.substringAfter("@cooldown:").toIntOrNull() ?: 0
            if (cleanCmd.startsWith("@oldCooldown:")) oldCooldownTime = cleanCmd.substringAfter("@oldCooldown:").toIntOrNull() ?: 0
        }

        val now = System.currentTimeMillis()

        if (cooldownTime > 0) {
            val lastTime = playerCooldowns[player.uniqueId]?.get(location) ?: 0L
            val remaining = (cooldownTime * 1000L - (now - lastTime)) / 1000L
            if (remaining > 0) {
                player.sendMsg("error_cooldown", "time" to remaining.toString())
                return
            }
        }
        if (oldCooldownTime > 0) {
            val lastTime = globalCooldowns[location] ?: 0L
            val remaining = (oldCooldownTime * 1000L - (now - lastTime)) / 1000L
            if (remaining > 0) {
                player.sendMsg("error_cooldown", "time" to remaining.toString())
                return
            }
        }

        if (cooldownTime > 0) playerCooldowns.getOrPut(player.uniqueId) { mutableMapOf() }[location] = now
        if (oldCooldownTime > 0) globalCooldowns[location] = now

        for (line in scriptData.commands) {
            val cmd = PlaceholderManager.replace(player, line)
            val isInverted = cmd.startsWith("!")
            val cleanCmd = if (isInverted) cmd.substring(1) else cmd

            if (cleanCmd.startsWith($$"$cost:")) {
                val cost = cleanCmd.substringAfter($$"$cost:").toDoubleOrNull() ?: 0.0
                if (isInverted) {
                    if (VaultManager.has(player, cost)) return
                } else {
                    requiredMoney += cost
                }
            } else if (cleanCmd.startsWith($$"$item:")) {
                val parts = cleanCmd.substringAfter($$"$item:").split(":")
                if (parts.size >= 2) {
                    val mat = Material.matchMaterial(parts[0].uppercase()) ?: continue
                    val amount = parts[1].toIntOrNull() ?: 1
                    val item = ItemStack(mat, amount)
                    if (isInverted) {
                        if (player.inventory.containsAtLeast(item, amount)) return
                    } else {
                        requiredItems.add(item)
                    }
                }
            } else if (cleanCmd.startsWith("@action:")) {
                val requiredAction = cleanCmd.substringAfter("@action:").lowercase()
                val isMatch = actionType.contains(requiredAction)
                if (if (isInverted) isMatch else !isMatch) return
            } else if (cleanCmd.startsWith("@blockType:")) {
                val mat = Material.matchMaterial(cleanCmd.substringAfter("@blockType:").uppercase())
                val isMatch = location.block.type == mat
                if (if (isInverted) isMatch else !isMatch) return
            } else if (cleanCmd.startsWith("@hand:")) {
                val parts = cleanCmd.substringAfter("@hand:").split(":")
                val mat = Material.matchMaterial(parts[0].uppercase())
                val isMatch = player.inventory.itemInMainHand.type == mat
                if (if (isInverted) isMatch else !isMatch) return
            } else if (cleanCmd.startsWith("@mmid:", ignoreCase = true)) {
                val prefix = "@mmid:"
                val parts = cleanCmd.substring(prefix.length).split(":")
                val mythicId = parts[0]
                val amount = parts.getOrNull(1)?.toIntOrNull() ?: 1
                if (isInverted) {
                    if (MythicMobsManager.countMythicItem(player, mythicId) >= amount) return
                } else {
                    requiredMythicItems[mythicId] = (requiredMythicItems[mythicId] ?: 0) + amount
                }
            } else if (cleanCmd.startsWith("@mmid:", ignoreCase = true)) {
                val prefix = "@mmid:"
                val parts = cleanCmd.substring(prefix.length).split(":")
                val mythicId = parts[0]
                val amount = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val hasEnough = MythicMobsManager.countMythicItem(player, mythicId) >= amount
                if (if (isInverted) hasEnough else !hasEnough) return
            } else if (cleanCmd.startsWith("@dRole:", ignoreCase = true)) {
                val roleId = cleanCmd.substringAfter(":")
                val hasRole = DiscordSRVManager.hasRole(player.uniqueId, roleId)
                if (if (isInverted) hasRole else !hasRole) return
            } else if (cleanCmd.startsWith("@dChannel:", ignoreCase = true)) {
                val channelId = cleanCmd.substringAfter(":")
                val isInChannel = DiscordSRVManager.getVoiceChannelId(player.uniqueId) == channelId
                if (if (isInverted) isInChannel else !isInChannel) return
            } else if (cleanCmd.startsWith("@perm:", ignoreCase = true)) {
                val perm = cleanCmd.substringAfter(":")
                val hasPerm = player.hasPermission(perm)
                if (if (isInverted) hasPerm else !hasPerm) return
            } else if (cleanCmd.startsWith("@group:", ignoreCase = true)) {
                val group = cleanCmd.substringAfter(":")
                val hasGroup = LuckPermsManager.hasGroup(player, group)
                if (if (isInverted) hasGroup else !hasGroup) return
            } else if (cleanCmd.startsWith("@amount:", ignoreCase = true)) {
                val amountLimit = cleanCmd.substringAfter(":").toIntOrNull() ?: 0
                val used = playerAmounts[player.uniqueId]?.get(location) ?: 0
                if (used >= amountLimit) {
                    player.sendMsg("error_amount_limit", "limit" to amountLimit.toString())
                    return
                }
            } else if (cleanCmd.startsWith("@globalAmount:", ignoreCase = true)) {
                val amountLimit = cleanCmd.substringAfter(":").toIntOrNull() ?: 0
                val used = globalAmounts[location] ?: 0
                if (used >= amountLimit) {
                    player.sendMsg("error_global_amount_limit", "limit" to amountLimit.toString())
                    return
                }
            } else if (cleanCmd.startsWith("@if ")) {
                val ifArgs = cleanCmd.substringAfter("@if ").split(" ", limit = 4)
                if (ifArgs.size >= 3) {
                    val val1 = ifArgs[0]
                    val operator = ifArgs[1]
                    val val2 = ifArgs[2]
                    val failMsg = if (ifArgs.size == 4) ifArgs[3] else null

                    val num1 = val1.toDoubleOrNull()
                    val num2 = val2.toDoubleOrNull()
                    var passed = if (num1 != null && num2 != null) {
                        when (operator) {
                            "==" -> num1 == num2; "!=" -> num1 != num2; ">" -> num1 > num2
                            ">=" -> num1 >= num2; "<" -> num1 < num2; "<=" -> num1 <= num2; else -> false
                        }
                    } else {
                        when (operator) { "==" -> val1 == val2; "!=" -> val1 != val2; else -> false }
                    }

                    if (isInverted) passed = !passed
                    if (!passed) {
                        if (failMsg != null) player.sendMessage(failMsg)
                        return
                    }
                }
            }
        }

        if (requiredMoney > 0.0) {
            if (!VaultManager.has(player, requiredMoney)) {
                player.sendMsg("error_no_money", "amount" to requiredMoney.toString())
                return
            }
            VaultManager.withdraw(player, requiredMoney)
        }

        for (item in requiredItems) {
            if (!player.inventory.containsAtLeast(item, item.amount)) {
                player.sendMsg("error_no_item", "item" to item.type.name, "amount" to item.amount.toString())
                return
            }
            player.inventory.removeItem(item)
        }
        for ((mythicId, amount) in requiredMythicItems) {
            if (MythicMobsManager.countMythicItem(player, mythicId) < amount) {
                player.sendMsg("error_no_item", "item" to mythicId, "amount" to amount.toString())
                return
            }
        }
        for ((mythicId, amount) in requiredMythicItems) {
            MythicMobsManager.takeMythicItem(player, mythicId, amount)
        }

        // Increment amounts
        for (line in scriptData.commands) {
            val cmd = PlaceholderManager.replace(player, line)
            val cleanCmd = cmd.removePrefix("!")
            if (cleanCmd.startsWith("@amount:", ignoreCase = true)) {
                val map = playerAmounts.getOrPut(player.uniqueId) { mutableMapOf() }
                map[location] = (map[location] ?: 0) + 1
            } else if (cleanCmd.startsWith("@globalAmount:", ignoreCase = true)) {
                globalAmounts[location] = (globalAmounts[location] ?: 0) + 1
            }
        }

        executeCommands(player, scriptData.commands, 0)
    }

    private fun executeCommands(player: Player, commands: List<String>, startIndex: Int) {
        if (!player.isOnline) return

        for (i in startIndex until commands.size) {
            val rawLine = commands[i]
            val cmd = PlaceholderManager.replace(player, rawLine)

            val isInverted = cmd.startsWith("!")
            val activeCmd = if (isInverted) cmd.substring(1) else cmd
            if (activeCmd.startsWith($$"$cost:") || activeCmd.startsWith($$"$item:") ||
                activeCmd.startsWith("@if ") || activeCmd.startsWith("@cooldown:") ||
                activeCmd.startsWith("@oldCooldown:") || activeCmd.startsWith("@action:") ||
                activeCmd.startsWith("@blockType:") ||
                activeCmd.startsWith("@hand:") ||
                activeCmd.startsWith("@perm:", ignoreCase = true) ||
                activeCmd.startsWith("@group:", ignoreCase = true) ||
                activeCmd.startsWith("@amount:", ignoreCase = true) ||
                activeCmd.startsWith("@globalAmount:", ignoreCase = true) ||
                activeCmd.startsWith("@cancelEvent:", ignoreCase = true) ||
                activeCmd.startsWith("@mmid:", ignoreCase = true) ||
                activeCmd.startsWith("@dRole:", ignoreCase = true) || activeCmd.startsWith("@dChannel:", ignoreCase = true)) continue
            if (activeCmd.startsWith("@delay:")) {
                val rawData = activeCmd.substringAfter("@delay:")
                val braceIdx = rawData.indexOf('{')
                val ticksStr = if (braceIdx != -1) rawData.take(braceIdx) else rawData
                val ticks = ticksStr.toLongOrNull() ?: 20L

                var wait = true
                if (braceIdx != -1 && rawData.endsWith("}")) {
                    val params = rawData.substring(braceIdx + 1, rawData.length - 1)
                    if (params.contains("wait=false")) wait = false
                }

                if (!wait) {
                } else {
                    Bukkit.getScheduler().runTaskLater(ScriptBlockNext.instance, Runnable {
                        executeCommands(player, commands, i + 1)
                    }, ticks)
                    return
                }
                continue
            }
            if (activeCmd.startsWith("@sound:")) {
                val rawData = activeCmd.substringAfter("@sound:")
                val braceIdx = rawData.indexOf('{')
                val soundStr = if (braceIdx != -1) rawData.substring(0, braceIdx) else rawData

                var broadcast = false
                if (braceIdx != -1 && rawData.endsWith("}")) {
                    val params = rawData.substring(braceIdx + 1, rawData.length - 1)
                    if (params.contains("broadcast=true")) broadcast = true
                }

                val soundParts = soundStr.split("-")
                val sound = runCatching { Sound.valueOf(soundParts.getOrNull(0)?.uppercase() ?: "") }.getOrNull()
                if (sound != null) {
                    val volume = soundParts.getOrNull(1)?.toFloatOrNull() ?: 1.0f
                    val pitch = soundParts.getOrNull(2)?.toFloatOrNull() ?: 1.0f
                    if (broadcast) {
                        player.world.playSound(player.location, sound, volume, pitch)
                    } else {
                        player.playSound(player.location, sound, volume, pitch)
                    }
                }
                continue
            }

            when {
                rawLine.startsWith("@player ") || rawLine.startsWith("@msg ") || rawLine.startsWith("@message ") -> player.sendMessage(activeCmd.substringAfter(" "))
                activeCmd.startsWith("@server ") || activeCmd.startsWith("@broadcast ") -> {
                    val msg = if (activeCmd.startsWith("@server ")) activeCmd.substringAfter("@server ") else activeCmd.substringAfter("@broadcast ")
                    Bukkit.broadcastMessage(msg)
                }
                activeCmd.startsWith("@command ") -> player.performCommand(activeCmd.substringAfter("@command ").trim().removePrefix("/"))
                activeCmd.startsWith("@console ") -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), activeCmd.substringAfter("@console ").trim().removePrefix("/"))
                activeCmd.startsWith("@bypass ") -> {
                    val isOp = player.isOp
                    try {
                        player.isOp = true
                        player.performCommand(activeCmd.substringAfter("@bypass ").trim().removePrefix("/"))
                    } finally {
                        player.isOp = isOp
                    }
                }
                activeCmd.startsWith("@velocity:") -> {
                    val args = activeCmd.substringAfter("@velocity:").split(",")
                    if (args.size == 3) player.velocity = Vector(args[0].toDoubleOrNull() ?: 0.0, args[1].toDoubleOrNull() ?: 0.0, args[2].toDoubleOrNull() ?: 0.0)
                }
                activeCmd.startsWith("@checkpoint") -> ScriptManager.checkpoints[player.uniqueId] = player.location
                activeCmd.startsWith("@return") -> player.teleport(ScriptManager.checkpoints[player.uniqueId] ?: player.location)
                activeCmd.startsWith("@noFall:") -> ScriptManager.noFallPlayers[player.uniqueId] = System.currentTimeMillis() + ((activeCmd.substringAfter("@noFall:").toIntOrNull() ?: 0) * 1000L)
                activeCmd.startsWith("@potion:") -> {
                    val args = activeCmd.substringAfter("@potion:").split(":")
                    if (args.size >= 3) {
                        val type = PotionEffectType.getByName(args[0].uppercase())
                        if (type != null) player.addPotionEffect(PotionEffect(type, (args[1].toIntOrNull() ?: 1) * 20, args[2].toIntOrNull() ?: 0))
                    }
                }
                activeCmd.startsWith("@actionBar:") -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(activeCmd.substringAfter("@actionBar:")))
                activeCmd.startsWith("@title:") -> {
                    val titleData = activeCmd.substringAfter("@title:").split("/")
                    player.sendTitle(titleData.getOrNull(0) ?: "", titleData.getOrNull(1) ?: "", 10, 70, 20)
                }
                activeCmd.startsWith("@bypassGroup:", ignoreCase = true) -> {
                    val data = activeCmd.substringAfter(":").trim()
                    val spaceIdx = data.indexOf(' ')
                    if (spaceIdx != -1) {
                        val groupInfo = data.substring(0, spaceIdx)
                        val commandToRun = data.substring(spaceIdx + 1).trim().removePrefix("/")
                        val group = groupInfo.substringAfter("/")
                        LuckPermsManager.addGroup(player, group)
                        try {
                            player.performCommand(commandToRun)
                        } finally {
                            LuckPermsManager.removeGroup(player, group)
                        }
                    }
                }
                activeCmd.startsWith("@bypassPerm:", ignoreCase = true) -> {
                    val data = activeCmd.substringAfter(":").trim()
                    val spaceIdx = data.indexOf(' ')
                    if (spaceIdx != -1) {
                        val perm = data.substring(0, spaceIdx)
                        val commandToRun = data.substring(spaceIdx + 1).trim().removePrefix("/")
                        val attachment = player.addAttachment(ScriptBlockNext.instance)
                        attachment.setPermission(perm, true)
                        try {
                            player.performCommand(commandToRun)
                        } finally {
                            player.removeAttachment(attachment)
                        }
                    }
                }
                activeCmd.startsWith("@groupAdd:", ignoreCase = true) -> {
                    val group = activeCmd.substringAfter(":").substringAfter("/")
                    LuckPermsManager.addGroup(player, group)
                }
                activeCmd.startsWith("@groupRemove:", ignoreCase = true) -> {
                    val group = activeCmd.substringAfter(":").substringAfter("/")
                    LuckPermsManager.removeGroup(player, group)
                }
                activeCmd.startsWith("@dRoleAdd:", ignoreCase = true) -> {
                    DiscordSRVManager.addRole(player.uniqueId, activeCmd.substringAfter(":"))
                }
                activeCmd.startsWith("@dRoleRemove:", ignoreCase = true) -> {
                    DiscordSRVManager.removeRole(player.uniqueId, activeCmd.substringAfter(":"))
                }
                else -> {
                    if (!activeCmd.startsWith("@") && !activeCmd.startsWith("$")) player.performCommand(activeCmd.trim().removePrefix("/"))
                }
            }
        }
    }
}