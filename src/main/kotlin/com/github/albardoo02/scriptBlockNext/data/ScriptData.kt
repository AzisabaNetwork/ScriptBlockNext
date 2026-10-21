package com.github.albardoo02.scriptBlockNext.data

import java.util.UUID

/** レッドストーン起動の設定。selector は @p / @a / @r(+ [r=距離])、filter は op / perm / limit */
data class RedstoneSetting(
    val selector: String = "@p",
    val filterOp: Boolean? = null,
    val filterPerm: String? = null,
    val filterLimit: Int? = null
)

data class ScriptData (
    val commands: List<String>,
    val type: String = "interact",
    val creator: UUID? = null,
    val permission: String? = null,
    val nametag: String? = null,
    val redstone: RedstoneSetting? = null
)

data class BlockLocation(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int
)

data class ScriptEntry(
    val commands: List<String>,
    val creator: String? = null,
    val nametag: String? = null,
    val redstone: RedstoneSetting? = null
)
