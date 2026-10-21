package com.github.albardoo02.scriptBlockNext.hook

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.BlockLocation
import com.github.albardoo02.scriptBlockNext.manager.ExecutionCounter
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player

object PlaceholderManager {

    private lateinit var plugin: ScriptBlockNext
    private var hasPlaceholderAPI = false

    private val escapes = listOf(
        "\\a" to "&", "\\b" to " ", "\\c" to ",", "\\h" to "-", "\\o" to ":",
        "\\p" to "%", "\\s" to "/", "\\l" to "<", "\\g" to ">",
        "\\i" to "[", "\\j" to "]"
    )
    private val randomColors = listOf("a", "b", "c", "d", "e", "f", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    fun init(plugin: ScriptBlockNext) {
        this.plugin = plugin
        hasPlaceholderAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        if (hasPlaceholderAPI) {
            plugin.logger.info("PlaceholderAPI hooked successfully.")
        }
    }

    /** `<player>` / `<world>`、組み込みプレースホルダー、PlaceholderAPI を展開する(エスケープ・色変換は行わない) */
    fun replace(player: Player, text: String): String {
        var result = text.replace("<player>", player.name).replace("<world>", player.world.name)
        if (result.contains('%')) {
            result = replaceBuiltin(player, result)
            if (hasPlaceholderAPI) result = PlaceholderAPI.setPlaceholders(player, result)
        }
        return result
    }

    /** `\b` などのエスケープシーケンスを実際の文字へ変換する */
    fun unescape(text: String): String {
        if (!text.contains('\\')) return text
        var result = text
        for ((key, value) in escapes) result = result.replace(key, value)
        return result
    }

    /** `&` カラーコードと `&rc`(ランダムカラー)を変換する */
    fun color(text: String): String {
        var result = text
        while (result.contains("&rc")) {
            result = result.replaceFirst("&rc", "&${randomColors.random()}")
        }
        return ChatColor.translateAlternateColorCodes('&', result)
    }

    /** エスケープ展開 + 色変換 */
    fun format(text: String): String = color(unescape(text))

    private fun replaceBuiltin(player: Player, text: String): String {
        return Regex("%[A-Za-z0-9_.,/-]+%").replace(text) { m ->
            val key = m.value.trim('%').lowercase()
            builtin(player, key) ?: m.value
        }
    }

    @Suppress("DEPRECATION")
    private fun ping(player: Player): Int? = try {
        player.javaClass.getMethod("getPing").invoke(player) as? Int
    } catch (e: Exception) {
        null
    }

    private fun builtin(player: Player, key: String): String? {
        if (key.startsWith("player_ping_")) {
            val target = Bukkit.getPlayerExact(key.removePrefix("player_ping_")) ?: return null
            return ping(target)?.toString()
        }
        if (key.startsWith("player_count_")) {
            // %player_count_[scriptkey/]<world>,<x>,<y>,<z>%
            val spec = key.removePrefix("player_count_")
            val type = if (spec.contains("/")) spec.substringBefore("/") else (ExecutionCounter.current?.first ?: "interact")
            val c = spec.substringAfter("/").split(",")
            if (c.size != 4) return null
            val loc = BlockLocation(c[0], c[1].toIntOrNull() ?: return null, c[2].toIntOrNull() ?: return null, c[3].toIntOrNull() ?: return null)
            return ExecutionCounter.get(player.uniqueId, type, loc).toString()
        }
        if (key.startsWith("player_others_in_range_")) {
            val range = key.substringAfterLast("_").toDoubleOrNull() ?: return null
            return player.getNearbyEntities(range, range, range).count { it is Player }.toString()
        }
        if (key.startsWith("server_online_")) {
            val world = Bukkit.getWorld(key.removePrefix("server_online_")) ?: return null
            return world.players.size.toString()
        }
        if (key.startsWith("objective_score_")) {
            val objective = Bukkit.getScoreboardManager()?.mainScoreboard?.getObjective(key.removePrefix("objective_score_"))
                ?: return null
            return objective.getScore(player.name).score.toString()
        }
        return when (key) {
            "player_ping" -> ping(player)?.toString()
            "player_count" -> ExecutionCounter.current?.let { (type, loc) ->
                ExecutionCounter.get(player.uniqueId, type, loc).toString()
            }
            "server_online" -> Bukkit.getOnlinePlayers().size.toString()
            "server_offline" -> Bukkit.getOfflinePlayers().size.minus(Bukkit.getOnlinePlayers().size).coerceAtLeast(0).toString()
            "player_gamemode" -> player.gameMode.name
            "player_world_time" -> player.world.time.toString()
            "player_level" -> player.level.toString()
            "player_exp" -> player.exp.toString()
            "player_exp_to_level" -> player.expToLevel.toString()
            "player_total_exp" -> player.totalExperience.toString()
            "player_fly_speed" -> player.flySpeed.toString()
            "player_walk_speed" -> player.walkSpeed.toString()
            "player_x" -> player.location.blockX.toString()
            "player_y" -> player.location.blockY.toString()
            "player_z" -> player.location.blockZ.toString()
            "player_bed_x" -> player.bedSpawnLocation?.blockX?.toString()
            "player_bed_y" -> player.bedSpawnLocation?.blockY?.toString()
            "player_bed_z" -> player.bedSpawnLocation?.blockZ?.toString()
            "player_compass_x" -> player.compassTarget.blockX.toString()
            "player_compass_y" -> player.compassTarget.blockY.toString()
            "player_compass_z" -> player.compassTarget.blockZ.toString()
            "player_food_level" -> player.foodLevel.toString()
            "player_saturation" -> player.saturation.toString()
            "player_health" -> player.health.toString()
            "player_health_scale" -> player.healthScale.toString()
            "player_max_health" -> player.maxHealth.toString()
            "player_last_damage" -> player.lastDamage.toString()
            "player_no_damage_ticks" -> player.noDamageTicks.toString()
            "player_max_no_damage_ticks" -> player.maximumNoDamageTicks.toString()
            "player_sleep_ticks" -> player.sleepTicks.toString()
            "player_max_air" -> player.maximumAir.toString()
            "player_remaining_air" -> player.remainingAir.toString()
            "player_time" -> System.currentTimeMillis().toString()
            "player_time_offset" -> player.playerTimeOffset.toString()
            "player_ticks_lived" -> player.ticksLived.toString()
            "player_seconds_lived" -> (player.ticksLived / 20).toString()
            "player_minutes_lived" -> (player.ticksLived / 1200).toString()
            "vault_eco_balance" -> if (VaultManager.isHooked) VaultManager.balance(player).toString() else null
            else -> null
        }
    }
}
