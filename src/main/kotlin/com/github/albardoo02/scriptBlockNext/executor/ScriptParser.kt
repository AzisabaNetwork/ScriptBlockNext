package com.github.albardoo02.scriptBlockNext.executor

/**
 * コマンドから入力された引数列をスクリプトの各行へ分割する。
 *
 * - `[@command /spawn] [@msg hi]` のように `[ ]` で囲んだ単位が1行になる
 * - 行の中に `[` `]` を含めたい場合は、対になっていればそのまま(ネスト可)、
 *   対にならない場合は `\[` `\]` と書く
 * - `[ ]` が1つも無い場合は、入力全体を1行として扱う(従来互換)
 */
object ScriptParser {

    fun split(input: String): List<String> {
        val text = input.trim()
        if (text.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var foundGroup = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            when {
                c == '\\' && (next == '[' || next == ']') -> {
                    if (depth > 0) current.append(next)
                    i++
                }
                c == '[' -> {
                    foundGroup = true
                    if (depth > 0) current.append(c)
                    depth++
                }
                c == ']' && depth > 0 -> {
                    depth--
                    if (depth == 0) {
                        current.toString().trim().takeIf { it.isNotEmpty() }?.let { lines.add(it) }
                        current.setLength(0)
                    } else {
                        current.append(c)
                    }
                }
                depth > 0 -> current.append(c)
            }
            i++
        }
        if (depth > 0) current.toString().trim().takeIf { it.isNotEmpty() }?.let { lines.add(it) }

        return if (foundGroup) lines else listOf(text)
    }
}
