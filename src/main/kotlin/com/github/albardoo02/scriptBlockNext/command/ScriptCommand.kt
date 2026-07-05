package com.github.albardoo02.scriptBlockNext.command

import com.github.albardoo02.scriptBlockNext.hook.MythicMobsManager
import com.github.albardoo02.scriptBlockNext.hook.LuckPermsManager
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import com.github.albardoo02.scriptBlockNext.manager.SelectorManager
import com.github.albardoo02.scriptBlockNext.manager.sendMsg
import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.hook.DiscordSRVManager
import com.github.albardoo02.scriptBlockNext.hook.VaultManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.SimplePluginManager
import java.lang.reflect.Field
import kotlin.math.max
import kotlin.math.min

class ScriptCommand: CommandExecutor, TabCompleter {

    private val commandMap: CommandMap? by lazy {
        try {
            val field: Field = SimplePluginManager::class.java.getDeclaredField("commandMap")
            field.isAccessible = true
            field.get(Bukkit.getPluginManager()) as CommandMap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            if (sender is Player) sender.sendMsg("cmd_usage_main")
            return true
        }

        val firstArg = args[0].lowercase()

        if (firstArg == "reload") {
            if (!sender.hasPermission("scriptblocknext.command.reload")) {
                sender.sendMsg("error_no_permission")
                return true
            }
            ScriptManager.loadScripts()
            sender.sendMsg("cmd_reloaded")
            return true
        }

        if (sender !is Player) {
            sender.sendMsg("error_only_player")
            return true
        }

        if (firstArg == "tool") {
            if (!sender.hasPermission("scriptblocknext.command.tool")) {
                sender.sendMsg("error_no_permission")
                return true
            }
            val selector = ItemStack(Material.BLAZE_ROD)
            val meta = selector.itemMeta
            meta?.setDisplayName("§bSBN Block Selector")
            meta?.lore = listOf("§7左クリックで Pos1 を設定", "§7右クリックで Pos2 を設定", "§7/sbn selector remove で一括削除")
            selector.itemMeta = meta
            sender.inventory.addItem(selector)
            sender.sendMsg("tool_given")
            return true
        }

        if (firstArg == "selector") {
            if (!sender.hasPermission("scriptblocknext.command.selector")) {
                sender.sendMsg("error_no_permission")
                return true
            }
            if (args.size < 2) {
                sender.sendMsg("cmd_usage_selector")
                return true
            }
            val subCmd = args[1].lowercase()

            if (subCmd == "remove") {
                val selection = SelectorManager.getSelection(sender)
                if (selection == null) {
                    sender.sendMsg("error_selection_incomplete")
                    return true
                }

                val p1 = selection.first
                val p2 = selection.second
                val world = p1.world
                var count = 0

                val minX = min(p1.blockX, p2.blockX)
                val minY = min(p1.blockY, p2.blockY)
                val minZ = min(p1.blockZ, p2.blockZ)
                val maxX = max(p1.blockX, p2.blockX)
                val maxY = max(p1.blockY, p2.blockY)
                val maxZ = max(p1.blockZ, p2.blockZ)

                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            val loc = org.bukkit.Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                            if (ScriptManager.scripts.values.any { it.containsKey(com.github.albardoo02.scriptBlockNext.data.BlockLocation(world.name, x, y, z)) }) {
                                ScriptManager.removeAllScripts(loc)
                                count++
                            }
                        }
                    }
                }
                sender.sendMsg("selector_removed", "count" to count.toString())
                return true
            }
            if (subCmd == "paste") {
                val selection = SelectorManager.getSelection(sender)
                if (selection == null) {
                    sender.sendMsg("error_selection_incomplete")
                    return true
                }
                val clipData = SelectorManager.clipboard[sender.uniqueId]
                if (clipData == null) {
                    sender.sendMsg("error_empty_clipboard")
                    return true
                }
                val pasteOnAir = args.getOrNull(2).toBoolean()
                val overwrite = args.getOrNull(3)?.toBoolean() ?: true

                val p1 = selection.first
                val p2 = selection.second
                val world = p1.world
                var count = 0

                val minX = min(p1.blockX, p2.blockX)
                val minY = min(p1.blockY, p2.blockY)
                val minZ = min(p1.blockZ, p2.blockZ)
                val maxX = max(p1.blockX, p2.blockX)
                val maxY = max(p1.blockY, p2.blockY)
                val maxZ = max(p1.blockZ, p2.blockZ)

                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            val loc = org.bukkit.Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                            val block = loc.block
                            if (!pasteOnAir && block.type == org.bukkit.Material.AIR) continue
                            val existing = ScriptManager.getScript(loc, clipData.type)
                            if (!overwrite && existing != null) continue
                            ScriptManager.addScript(loc, clipData)
                            count++
                        }
                    }
                }
                sender.sendMsg("success_pasted", "count" to count.toString(), "type" to clipData.type)
                return true
            }
            return true
        }

        if (args.size < 2) {
            sender.sendMsg("cmd_usage_action")
            return true
        }

        val type = args[0].lowercase()
        val action = args[1].lowercase()

        if (type !in listOf("interact", "break", "walk", "hit")) {
            sender.sendMsg("error_invalid_type")
            return true
        }

        if (!sender.hasPermission("scriptblocknext.command.$type")) {
            sender.sendMsg("error_no_permission")
            return true
        }

        when (action) {
            "view", "info" -> {
                val targetBlock = sender.getTargetBlockExact(5)
                if (targetBlock == null) {
                    sender.sendMsg("error_look_at_block")
                    return true
                }
                val data = ScriptManager.getScript(targetBlock.location, type)
                if (data == null) {
                    sender.sendMsg("error_no_script_info", "type" to type)
                } else {
                    sender.sendMsg("info_header")
                    sender.sendMsg("info_type", "type" to data.type)
                    val creatorName = data.creator?.let { uuid ->
                        Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
                    } ?: "Unknown"
                    sender.sendMsg("info_creator", "creator" to creatorName)
                    sender.sendMsg("info_commands_header")
                    data.commands.forEachIndexed { i, c ->
                        sender.sendMsg("info_command_line", "index" to (i + 1).toString(), "command" to c)
                    }
                    sender.sendMsg("info_footer")
                }
            }
            "copy" -> {
                val targetBlock = sender.getTargetBlockExact(5)
                if (targetBlock == null) {
                    sender.sendMsg("error_look_at_block")
                    return true
                }
                val data = ScriptManager.getScript(targetBlock.location, type)
                if (data == null) {
                    sender.sendMsg("error_no_script_info", "type" to type)
                } else {
                    SelectorManager.clipboard[sender.uniqueId] = data
                    sender.sendMsg("success_copied", "type" to type)
                }
            }
            "create", "add" -> {
                if (args.size < 3) {
                    sender.sendMsg("cmd_usage_args", "type" to type, "action" to action)
                    return true
                }
                val cmdString = args.drop(2).joinToString(" ")
                val regex = Regex("\\[(.*?)\\]")
                val matches = regex.findAll(cmdString)
                val commands = matches.map { it.groupValues[1] }.toList().ifEmpty { listOf(cmdString) }

                if (action == "create") {
                    ScriptManager.creationMode[sender.uniqueId] = type to commands
                    ScriptManager.addMode.remove(sender.uniqueId)
                    ScriptManager.removalMode.remove(sender.uniqueId)
                    sender.sendMsg("mode_creation", "type" to type, "commands" to commands.joinToString(", "))
                } else {
                    ScriptManager.addMode[sender.uniqueId] = type to commands
                    ScriptManager.creationMode.remove(sender.uniqueId)
                    ScriptManager.removalMode.remove(sender.uniqueId)
                    sender.sendMsg("mode_add", "type" to type, "commands" to commands.joinToString(", "))
                }
            }
            "remove" -> {
                ScriptManager.removalMode[sender.uniqueId] = type
                ScriptManager.creationMode.remove(sender.uniqueId)
                ScriptManager.addMode.remove(sender.uniqueId)
                sender.sendMsg("mode_removal", "type" to type)
            }
            "show" -> {
                val world = sender.world
                val scriptsInWorld = ScriptManager.scripts[type]?.filter { it.key.world == world.name }
                if (scriptsInWorld.isNullOrEmpty()) {
                    sender.sendMsg("error_no_script_info", "type" to type)
                    return true
                }
                sender.sendMsg("info_show_start", "type" to type, "count" to scriptsInWorld.size.toString())
                var ticks = 0
                val taskId = Bukkit.getScheduler().runTaskTimer(ScriptBlockNext.instance, Runnable {
                    if (ticks > 200) return@Runnable // 10 seconds
                    for ((locData, _) in scriptsInWorld) {
                        val loc = org.bukkit.Location(world, locData.x + 0.5, locData.y + 0.5, locData.z + 0.5)
                        world.spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, loc, 3, 0.3, 0.3, 0.3, 0.0)
                    }
                    ticks += 10
                }, 0L, 10L).taskId
                Bukkit.getScheduler().runTaskLater(ScriptBlockNext.instance, Runnable {
                    Bukkit.getScheduler().cancelTask(taskId)
                }, 205L)
            }
            else -> sender.sendMsg("error_unknown_action", "action" to action)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (sender !is Player) return emptyList()

        if (args.size == 1) {
            val firstArgs = mutableListOf<String>()
            if (sender.hasPermission("scriptblocknext.command.interact")) firstArgs.add("interact")
            if (sender.hasPermission("scriptblocknext.command.break")) firstArgs.add("break")
            if (sender.hasPermission("scriptblocknext.command.walk")) firstArgs.add("walk")
            if (sender.hasPermission("scriptblocknext.command.hit")) firstArgs.add("hit")
            if (sender.hasPermission("scriptblocknext.command.reload")) firstArgs.add("reload")
            if (sender.hasPermission("scriptblocknext.command.tool")) firstArgs.add("tool")
            if (sender.hasPermission("scriptblocknext.command.selector")) firstArgs.add("selector")
            if (sender.hasPermission("scriptblocknext.command.datamigr")) firstArgs.add("datamigr")
            return firstArgs.filter { it.startsWith(args[0], ignoreCase = true) }
        }

        val first = args[0].lowercase()

        if (args.size == 2 && first == "selector") {
            if (!sender.hasPermission("scriptblocknext.command.selector")) return emptyList()
            return listOf("remove", "paste").filter { it.startsWith(args[1], ignoreCase = true) }
        }

        if (first == "selector" && args.size >= 3) {
            if (args[1].lowercase() == "paste") {
                return listOf("true", "false").filter { it.startsWith(args.last(), ignoreCase = true) }
            }
            return emptyList()
        }

        if (args.size == 2 && first in listOf("interact", "break", "walk", "hit")) {
            if (!sender.hasPermission("scriptblocknext.command.$first")) return emptyList()
            return listOf("create", "add", "remove", "view", "info", "copy", "show").filter { it.startsWith(args[1], ignoreCase = true) }
        }

        if (args.size >= 3 && first in listOf("interact", "break", "walk", "hit")) {
            if (!sender.hasPermission("scriptblocknext.command.$first")) return emptyList()

            val currentArg = args.last()
            val fullInput = args.drop(2).joinToString(" ")

            val commandTags = listOf("[@command ", "[@console ", "[@bypass ", "[@bypassPerm:", "[@bypassGroup:")
            val activeTag = commandTags.find { tag ->
                val lastIdx = fullInput.lastIndexOf(tag)
                lastIdx != -1 && !fullInput.substring(lastIdx).contains("]")
            }

            if (activeTag != null) {
                val cmdContent = fullInput.substring(fullInput.lastIndexOf(activeTag) + activeTag.length)
                val cmdArgs = cmdContent.split(" ").toMutableList()
                if (cmdArgs.size <= 1) {
                    val search = if (cmdArgs.isEmpty()) "" else cmdArgs[0]
                    return commandMap?.knownCommands?.keys?.filter { it.startsWith(search, ignoreCase = true) && !it.contains(":") }
                } else {
                    val label = cmdArgs[0].removePrefix("/")
                    val targetCmd = commandMap?.getCommand(label)
                    if (targetCmd != null) {
                        val subArgs = cmdArgs.drop(1).toTypedArray()
                        return try {
                            targetCmd.tabComplete(sender, label, subArgs)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }

            var baseOptions = listOf(
                "[@action:", "[@blockType:", "[@delay:", "[@cooldown:",
                "[@oldCooldown:", $$"[$item:", "[@command ", "[@msg ", "@message ", "[@player ",
                "[@server ", "[@console ", "[@bypass ", "[@sound:", "[@title:", "[@actionBar:",
                "[@velocity:", "[@checkpoint]", "[@return]", "[@noFall:", "[@potion:",
                "[@if ", "[@hand:", "[@bypassPerm:", "[@perm:", "[@amount:", "[@globalAmount:",
                "[@cancelEvent:true]", "[@cancelEvent:false]"
            )

            if (VaultManager.isHooked) {
                baseOptions = baseOptions + listOf($$"[$cost:")
            }
            if (DiscordSRVManager.isHooked) {
                baseOptions = baseOptions + listOf("[@dRole:", "[@dChannel:", "[@dRoleAdd:", "[@dRoleRemove:")
            }
            if (LuckPermsManager.isHooked) {
                baseOptions = baseOptions + listOf("[@group:", "[@groupAdd:", "[@groupRemove:", "[@bypassGroup:")
            }
            if (MythicMobsManager.isHooked) {
                baseOptions = baseOptions + listOf("[@mmid:")
            }

            val options = baseOptions

            val lastBracket = currentArg.lastIndexOf('[')
            return if (lastBracket != -1) {
                val prefix = currentArg.substring(0, lastBracket)
                val checkArg = currentArg.substring(lastBracket)
                if (MythicMobsManager.isHooked) {
                    val mythicPrefixes = listOf(
                        "[@mmid:", "[!@mmid:"
                    )
                    val activePrefix = mythicPrefixes.find { checkArg.startsWith(it, ignoreCase = true) }

                    if (activePrefix != null) {
                        val typedIdAndAmount = checkArg.substring(activePrefix.length)
                        if (!typedIdAndAmount.contains(":")) {
                            val availableIds = MythicMobsManager.getAvailableItemIds()
                            return availableIds.filter { it.startsWith(typedIdAndAmount, ignoreCase = true) }
                                .map { prefix + activePrefix + it }
                        }
                    }
                }
                options.filter { it.startsWith(checkArg, ignoreCase = true) }.map { prefix + it }
            } else {
                options.filter { it.startsWith("[$currentArg", ignoreCase = true) }
            }
        }
        return emptyList()
    }
}