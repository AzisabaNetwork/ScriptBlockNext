package com.github.albardoo02.scriptBlockNext.executor

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.BlockLocation
import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.hook.DiscordSRVManager
import com.github.albardoo02.scriptBlockNext.hook.LuckPermsManager
import com.github.albardoo02.scriptBlockNext.hook.MythicMobsManager
import com.github.albardoo02.scriptBlockNext.hook.PlaceholderManager
import com.github.albardoo02.scriptBlockNext.hook.VaultManager
import com.github.albardoo02.scriptBlockNext.manager.ExecutionCounter
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

    private const val MAX_EXECUTE_DEPTH = 3

    private data class ScriptKey(val type: String, val loc: BlockLocation)

    /** `@name:body` / `@name body` 形式の1行を分解したもの */
    private class Option(val inverted: Boolean, val name: String, val body: String)

    private class ItemSpec(val material: Material, val amount: Int, val name: String?, val lore: String?)

    private val headRegex = Regex("^(!?)[@$]([A-Za-z]+)(?:[: ](.*))?$", RegexOption.DOT_MATCHES_ALL)

    private val conditionNames = setOf(
        "cost", "item", "action", "blocktype", "hand", "mmid", "drole", "dchannel", "perm", "group",
        "amount", "globalamount", "playeramount", "if", "cooldown", "oldcooldown"
    )
    private val noopNames = conditionNames + setOf("cancelevent", "invalid")

    private val playerCooldowns = mutableMapOf<UUID, MutableMap<ScriptKey, Long>>()
    private val globalCooldowns = mutableMapOf<ScriptKey, Long>()
    private val playerAmounts = mutableMapOf<UUID, MutableMap<ScriptKey, Int>>()
    private val globalAmounts = mutableMapOf<ScriptKey, Int>()
    private val pendingDelays = mutableSetOf<Pair<UUID, ScriptKey>>()

    private fun parse(line: String): Option? {
        val m = headRegex.matchEntire(line.trim()) ?: return null
        return Option(m.groupValues[1] == "!", m.groupValues[2].lowercase(), m.groupValues[3])
    }

    private fun parseAll(player: Player, commands: List<String>): List<Pair<String, Option?>> =
        commands.map { line ->
            val replaced = PlaceholderManager.replace(player, line)
            replaced to parse(replaced)
        }

    /** スクリプトがイベントのキャンセルを要求しているか(@invalid) */
    fun hasInvalid(scriptData: ScriptData): Boolean =
        scriptData.commands.any { parse(it)?.name == "invalid" }

    /** `@cancelEvent:false` が指定されているか */
    fun isCancelEventDisabled(scriptData: ScriptData): Boolean =
        scriptData.commands.any {
            val o = parse(it)
            o != null && o.name == "cancelevent" && o.body.trim().equals("false", ignoreCase = true)
        }

    fun run(player: Player, scriptData: ScriptData, location: Location, actions: Set<String> = emptySet()): Boolean =
        run(player, scriptData, location, actions, 0)

    private fun run(player: Player, scriptData: ScriptData, location: Location, actions: Set<String>, depth: Int): Boolean {
        val plugin = ScriptBlockNext.instance
        val blockLoc = BlockLocation(location.world?.name ?: "", location.blockX, location.blockY, location.blockZ)
        val key = ScriptKey(scriptData.type, blockLoc)

        if (pendingDelays.contains(player.uniqueId to key)) return false

        if (plugin.config.getBoolean("output_script_log", false)) {
            plugin.logger.info(
                "Player ${player.name} executed ${scriptData.type} script at ${blockLoc.world}, ${blockLoc.x}, ${blockLoc.y}, ${blockLoc.z}"
            )
        }

        ExecutionCounter.current = scriptData.type to blockLoc
        val parsed = parseAll(player, scriptData.commands)
        var requiredMoney = 0.0
        val requiredItems = mutableListOf<ItemSpec>()
        val requiredMythicItems = mutableMapOf<String, Int>()
        var cooldownTime = 0
        var oldCooldownTime = 0
        var amountLimit: Int? = null
        var playerAmountLimit: Int? = null
        var globalAmountLimit: Int? = null

        for ((_, opt) in parsed) {
            if (opt == null || opt.name !in conditionNames) continue
            val body = opt.body.trim()
            val pass: Boolean = when (opt.name) {
                "cooldown" -> { cooldownTime = body.toIntOrNull() ?: 0; true }
                "oldcooldown" -> { oldCooldownTime = body.toIntOrNull() ?: 0; true }
                "amount" -> { amountLimit = body.toIntOrNull(); true }
                "playeramount" -> { playerAmountLimit = body.toIntOrNull(); true }
                "globalamount" -> { globalAmountLimit = body.toIntOrNull(); true }
                "cost" -> {
                    val cost = body.toDoubleOrNull() ?: 0.0
                    if (opt.inverted) !VaultManager.has(player, cost) else { requiredMoney += cost; true }
                }
                "item" -> {
                    val spec = parseItemSpec(body) ?: return false
                    if (opt.inverted) countItems(player, spec) < spec.amount else { requiredItems.add(spec); true }
                }
                "hand" -> {
                    val spec = parseItemSpec(body) ?: return false
                    val inHand = player.inventory.itemInMainHand
                    val match = matches(inHand, spec) && inHand.amount >= spec.amount
                    match != opt.inverted
                }
                "action" -> {
                    val required = body.lowercase().split(",").map { it.trim() }
                    val match = required.any { it in actions }
                    match != opt.inverted
                }
                "blocktype" -> {
                    val current = location.block.type
                    val match = body.split(",").any { Material.matchMaterial(it.trim().substringBefore(":").uppercase()) == current }
                    match != opt.inverted
                }
                "mmid" -> {
                    val parts = body.split(":")
                    val id = parts[0]
                    val amount = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    if (opt.inverted) MythicMobsManager.countMythicItem(player, id) < amount
                    else { requiredMythicItems[id] = (requiredMythicItems[id] ?: 0) + amount; true }
                }
                "drole" -> DiscordSRVManager.hasRole(player.uniqueId, body) != opt.inverted
                "dchannel" -> (DiscordSRVManager.getVoiceChannelId(player.uniqueId) == body) != opt.inverted
                "perm" -> player.hasPermission(stripWorld(body)) != opt.inverted
                "group" -> hasGroup(player, stripWorld(body)) != opt.inverted
                "if" -> evaluateIf(player, opt)
                else -> true
            }
            if (!pass) return false
        }

        val now = System.currentTimeMillis()
        if (cooldownTime > 0) {
            val remaining = (cooldownTime * 1000L - (now - (playerCooldowns[player.uniqueId]?.get(key) ?: 0L))) / 1000L
            if (remaining > 0) {
                player.sendMsg("error_cooldown", "time" to remaining.toString())
                return false
            }
        }
        if (oldCooldownTime > 0) {
            val remaining = (oldCooldownTime * 1000L - (now - (globalCooldowns[key] ?: 0L))) / 1000L
            if (remaining > 0) {
                player.sendMsg("error_cooldown", "time" to remaining.toString())
                return false
            }
        }
        playerAmountLimit?.let {
            if ((playerAmounts[player.uniqueId]?.get(key) ?: 0) >= it) {
                player.sendMsg("error_amount_limit", "limit" to it.toString())
                return false
            }
        }
        globalAmountLimit?.let {
            if ((globalAmounts[key] ?: 0) >= it) {
                player.sendMsg("error_global_amount_limit", "limit" to it.toString())
                return false
            }
        }

        // 消費物の確認(すべて足りる場合のみ消費する)
        if (requiredMoney > 0.0 && !VaultManager.has(player, requiredMoney)) {
            player.sendMsg("error_no_money", "amount" to requiredMoney.toString())
            return false
        }
        for (spec in requiredItems) {
            if (countItems(player, spec) < spec.amount) {
                player.sendMsg("error_no_item", "item" to spec.material.name, "amount" to spec.amount.toString())
                return false
            }
        }
        for ((id, amount) in requiredMythicItems) {
            if (MythicMobsManager.countMythicItem(player, id) < amount) {
                player.sendMsg("error_no_item", "item" to id, "amount" to amount.toString())
                return false
            }
        }
        if (requiredMoney > 0.0) VaultManager.withdraw(player, requiredMoney)
        for (spec in requiredItems) removeItems(player, spec)
        for ((id, amount) in requiredMythicItems) MythicMobsManager.takeMythicItem(player, id, amount)

        if (cooldownTime > 0) playerCooldowns.getOrPut(player.uniqueId) { mutableMapOf() }[key] = now
        if (oldCooldownTime > 0) globalCooldowns[key] = now
        if (playerAmountLimit != null) {
            val map = playerAmounts.getOrPut(player.uniqueId) { mutableMapOf() }
            map[key] = (map[key] ?: 0) + 1
        }
        if (globalAmountLimit != null) globalAmounts[key] = (globalAmounts[key] ?: 0) + 1
        if (amountLimit != null) consumeAmount(location, scriptData)

        ExecutionCounter.increment(player.uniqueId, scriptData.type, blockLoc)
        executeCommands(player, parsed, 0, key, location, depth)
        return true
    }

    /** `@amount:N` を1減らして保存し、0になったらスクリプトを削除する */
    private fun consumeAmount(location: Location, scriptData: ScriptData) {
        val amountRegex = Regex("^(!?@amount:)\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)
        val latest = ScriptManager.getScript(location, scriptData.type) ?: return
        var exhausted = false
        val updated = latest.commands.map { line ->
            val m = amountRegex.matchEntire(line.trim()) ?: return@map line
            val left = m.groupValues[2].toInt() - 1
            if (left <= 0) exhausted = true
            "${m.groupValues[1]}$left"
        }
        if (exhausted) ScriptManager.removeScript(location, scriptData.type)
        else ScriptManager.addScript(location, latest.copy(commands = updated))
    }

    private fun executeCommands(
        player: Player, options: List<Pair<String, Option?>>, startIndex: Int, key: ScriptKey, location: Location, depth: Int
    ) {
        if (!player.isOnline) return
        val plugin = ScriptBlockNext.instance

        for (i in startIndex until options.size) {
            val (line, opt) = options[i]
            if (opt == null) {
                // 先頭が @ / $ でない行は、プレイヤーが実行するコマンドとして扱う
                if (line.isNotBlank()) player.performCommand(cleanCommand(line))
                continue
            }
            val body = opt.body
            if (opt.name in noopNames) continue

            when (opt.name) {
                "delay" -> {
                    val (tickStr, stayStr) = body.split("/", limit = 2).let { it[0] to it.getOrNull(1) }
                    val ticks = tickStr.trim().toLongOrNull() ?: 20L
                    val stay = !(stayStr?.trim()?.trim('{', '}')?.equals("false", ignoreCase = true) ?: false)
                    val lock = player.uniqueId to key
                    if (stay) pendingDelays.add(lock)
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        pendingDelays.remove(lock)
                        executeCommands(player, options, i + 1, key, location, depth)
                    }, ticks)
                    return
                }
                "sound" -> playSound(player, body)
                "player", "msg", "message" -> player.sendMessage(PlaceholderManager.format(body))
                "server", "broadcast" -> Bukkit.broadcastMessage(PlaceholderManager.format(body))
                "say" -> player.performCommand("say " + PlaceholderManager.format(body))
                "command" -> player.performCommand(cleanCommand(body))
                "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cleanCommand(body))
                "bypass" -> {
                    val wasOp = player.isOp
                    try {
                        player.isOp = true
                        player.performCommand(cleanCommand(body))
                    } finally {
                        player.isOp = wasOp
                    }
                }
                "bypassperm" -> {
                    val (perm, cmd) = splitTarget(body) ?: continue
                    val attachment = player.addAttachment(plugin)
                    attachment.setPermission(stripWorld(perm), true)
                    try {
                        player.performCommand(cleanCommand(cmd))
                    } finally {
                        player.removeAttachment(attachment)
                    }
                }
                "bypassgroup" -> {
                    val (groupInfo, cmd) = splitTarget(body) ?: continue
                    val group = stripWorld(groupInfo)
                    val hadGroup = hasGroup(player, group)
                    if (!hadGroup) LuckPermsManager.addGroup(player, group)
                    try {
                        player.performCommand(cleanCommand(cmd))
                    } finally {
                        if (!hadGroup) LuckPermsManager.removeGroup(player, group)
                    }
                }
                "groupadd" -> LuckPermsManager.addGroup(player, stripWorld(PlaceholderManager.unescape(body.trim())))
                "groupremove" -> LuckPermsManager.removeGroup(player, stripWorld(PlaceholderManager.unescape(body.trim())))
                "permadd" -> modifyPermission(player, stripWorld(PlaceholderManager.unescape(body.trim())), true)
                "permremove" -> modifyPermission(player, stripWorld(PlaceholderManager.unescape(body.trim())), false)
                "droleadd" -> body.split(",").forEach { DiscordSRVManager.addRole(player.uniqueId, it.trim()) }
                "droleremove" -> body.split(",").forEach { DiscordSRVManager.removeRole(player.uniqueId, it.trim()) }
                "velocity" -> {
                    val v = body.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
                    if (v.size == 3) player.velocity = Vector(v[0], v[1], v[2])
                }
                "checkpoint" -> ScriptManager.checkpoints[player.uniqueId] = player.location
                "return" -> player.teleport(ScriptManager.checkpoints[player.uniqueId] ?: player.location)
                "nofall" -> ScriptManager.noFallPlayers[player.uniqueId] =
                    System.currentTimeMillis() + ((body.trim().toIntOrNull() ?: 0) * 1000L)
                "potion" -> {
                    val args = body.split(":")
                    if (args.size >= 3) {
                        val type = PotionEffectType.getByName(args[0].trim().uppercase())
                        if (type != null) player.addPotionEffect(
                            PotionEffect(type, (args[1].toIntOrNull() ?: 1) * 20, args[2].toIntOrNull() ?: 0)
                        )
                    }
                }
                "actionbar" -> sendActionBar(player, body)
                "title" -> sendTitle(player, body)
                "execute" -> executeAt(player, body, depth)
            }
        }
    }

    private fun cleanCommand(text: String): String = PlaceholderManager.format(text).trim().removePrefix("/")

    /** `<target> <command>` を分割する */
    private fun splitTarget(body: String): Pair<String, String>? {
        val data = body.trim()
        val idx = data.indexOf(' ')
        if (idx == -1) return null
        return data.substring(0, idx) to data.substring(idx + 1)
    }

    private fun stripWorld(text: String): String = text.trim().substringAfter("/")

    private fun hasGroup(player: Player, group: String): Boolean =
        if (LuckPermsManager.isHooked) LuckPermsManager.hasGroup(player, group) else player.hasPermission("group.$group")

    private fun modifyPermission(player: Player, node: String, add: Boolean) {
        when {
            LuckPermsManager.isHooked ->
                if (add) LuckPermsManager.addPermission(player, node) else LuckPermsManager.removePermission(player, node)
            VaultManager.hasPermissionProvider ->
                if (add) VaultManager.addPermission(player, node) else VaultManager.removePermission(player, node)
        }
    }

    private fun evaluateIf(player: Player, opt: Option): Boolean {
        val args = opt.body.trim().split(" ", limit = 4)
        if (args.size < 3) return true
        val v1 = PlaceholderManager.unescape(args[0])
        val operator = args[1]
        val v2 = PlaceholderManager.unescape(args[2])
        val n1 = v1.toDoubleOrNull()
        val n2 = v2.toDoubleOrNull()
        val passed = if (n1 != null && n2 != null) {
            when (operator) {
                "==" -> n1 == n2; "!=" -> n1 != n2; ">" -> n1 > n2
                ">=" -> n1 >= n2; "<" -> n1 < n2; "<=" -> n1 <= n2; else -> false
            }
        } else {
            when (operator) { "==" -> v1 == v2; "!=" -> v1 != v2; else -> false }
        }
        val result = passed != opt.inverted
        if (!result && args.size == 4) {
            player.sendMessage(PlaceholderManager.format(args[3]).replace("%value1%", v1).replace("%value2%", v2))
        }
        return result
    }

    // ---- アイテム ----

    /** `<ItemId>[:Damage] <Amount> [Name] [Lore]` */
    private fun parseItemSpec(body: String): ItemSpec? {
        val parts = body.trim().split(" ", limit = 4)
        val material = Material.matchMaterial(parts[0].substringBefore(":").uppercase()) ?: return null
        val amount = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val name = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { PlaceholderManager.format(it) }
        val lore = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }?.let { PlaceholderManager.format(it) }
        return ItemSpec(material, amount, name, lore)
    }

    private fun matches(item: ItemStack?, spec: ItemSpec): Boolean {
        if (item == null || item.type != spec.material) return false
        if (spec.name == null && spec.lore == null) return true
        val meta = item.itemMeta ?: return false
        if (spec.name != null && meta.displayName != spec.name) return false
        if (spec.lore != null && meta.lore?.any { it == spec.lore } != true) return false
        return true
    }

    private fun countItems(player: Player, spec: ItemSpec): Int =
        player.inventory.storageContents.filter { matches(it, spec) }.sumOf { it!!.amount }

    private fun removeItems(player: Player, spec: ItemSpec) {
        var remaining = spec.amount
        val inv = player.inventory
        for (i in 0 until inv.storageContents.size) {
            val item = inv.getItem(i)
            if (!matches(item, spec)) continue
            if (item!!.amount <= remaining) {
                remaining -= item.amount
                inv.setItem(i, null)
            } else {
                item.amount -= remaining
                remaining = 0
            }
            if (remaining <= 0) break
        }
    }

    // ---- 演出系 ----

    private fun playSound(player: Player, body: String) {
        val broadcast = body.substringAfter("/", "").trim().trim('{', '}').equals("true", ignoreCase = true) ||
            body.contains("broadcast=true")
        val soundStr = body.substringBefore("/").substringBefore("{")
        val parts = soundStr.split("-")
        val sound = runCatching { Sound.valueOf((parts.getOrNull(0) ?: "").trim().uppercase().replace('.', '_')) }.getOrNull() ?: return
        val volume = parts.getOrNull(1)?.toFloatOrNull() ?: 1.0f
        val pitch = parts.getOrNull(2)?.toFloatOrNull() ?: 1.0f
        val delay = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        val play = Runnable {
            if (player.isOnline) {
                if (broadcast) player.world.playSound(player.location, sound, volume, pitch)
                else player.playSound(player.location, sound, volume, pitch)
            }
        }
        if (delay > 0) Bukkit.getScheduler().runTaskLater(ScriptBlockNext.instance, play, delay) else play.run()
    }

    private fun sendTitle(player: Player, body: String) {
        val parts = body.split("/")
        val timing = Regex("^\\d+-\\d+-\\d+$")
        var fadeIn = 10
        var stay = 70
        var fadeOut = 20
        var texts = parts
        if (parts.size >= 2 && timing.matches(parts.last().trim())) {
            val t = parts.last().trim().split("-").map { it.toInt() }
            fadeIn = t[0]; stay = t[1]; fadeOut = t[2]
            texts = parts.dropLast(1)
        }
        player.sendTitle(
            PlaceholderManager.format(texts.getOrElse(0) { "" }),
            PlaceholderManager.format(texts.getOrElse(1) { "" }),
            fadeIn, stay, fadeOut
        )
    }

    private fun sendActionBar(player: Player, body: String) {
        val seconds = body.substringAfterLast("/", "").trim().toIntOrNull()
        val message = PlaceholderManager.format(if (seconds != null) body.substringBeforeLast("/") else body)
        val component = TextComponent.fromLegacyText(message)
        val send = { player.spigot().sendMessage(ChatMessageType.ACTION_BAR, *component) }
        send()
        if (seconds != null) {
            for (n in 1 until seconds) {
                Bukkit.getScheduler().runTaskLater(ScriptBlockNext.instance, Runnable {
                    if (player.isOnline) send()
                }, 20L * n)
            }
        }
    }

    /** `@execute:<ScriptKey>/<World,X,Y,Z>` */
    private fun executeAt(player: Player, body: String, depth: Int) {
        if (depth >= MAX_EXECUTE_DEPTH) return
        val type = body.substringBefore("/").trim().lowercase()
        val coords = body.substringAfter("/", "").split(",").map { it.trim() }
        if (coords.size != 4) return
        val world = Bukkit.getWorld(coords[0]) ?: return
        val x = coords[1].toIntOrNull() ?: return
        val y = coords[2].toIntOrNull() ?: return
        val z = coords[3].toIntOrNull() ?: return
        val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
        val data = ScriptManager.getScript(loc, type) ?: return
        run(player, data, loc, emptySet(), depth + 1)
    }
}
