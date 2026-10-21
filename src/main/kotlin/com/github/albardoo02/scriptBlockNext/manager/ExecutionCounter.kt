package com.github.albardoo02.scriptBlockNext.manager

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.BlockLocation
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/** プレイヤーごとのスクリプト実行回数(%player_count% 用)。counts.json に UTF-8 で保存する */
object ExecutionCounter {

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val counts = mutableMapOf<String, Int>()
    private lateinit var file: File

    /** 実行中のスクリプト(%player_count% の参照先) */
    @Volatile
    var current: Pair<String, BlockLocation>? = null

    fun init(plugin: ScriptBlockNext) {
        file = File(plugin.dataFolder, "counts.json")
        counts.clear()
        if (!file.exists()) return
        try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            file.reader(Charsets.UTF_8).use { r -> gson.fromJson<Map<String, Int>>(r, type)?.let { counts.putAll(it) } }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load counts.json: ${e.message}")
        }
    }

    private fun key(uuid: UUID, type: String, loc: BlockLocation) =
        "$uuid|$type|${loc.world},${loc.x},${loc.y},${loc.z}"

    fun get(uuid: UUID, type: String, loc: BlockLocation): Int = counts[key(uuid, type, loc)] ?: 0

    fun increment(uuid: UUID, type: String, loc: BlockLocation) {
        counts.merge(key(uuid, type, loc), 1, Int::plus)
    }

    fun save() {
        try {
            file.writer(Charsets.UTF_8).use { gson.toJson(counts, it) }
        } catch (e: Exception) {
            ScriptBlockNext.instance.logger.warning("Failed to save counts.json: ${e.message}")
        }
    }
}
