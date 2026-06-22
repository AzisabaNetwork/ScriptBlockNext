package com.github.albardoo02.scriptBlockNext

import com.github.albardoo02.scriptBlockNext.command.ScriptCommand
import com.github.albardoo02.scriptBlockNext.hook.DiscordSRVManager
import com.github.albardoo02.scriptBlockNext.hook.LuckPermsManager
import com.github.albardoo02.scriptBlockNext.hook.MythicMobsManager
import com.github.albardoo02.scriptBlockNext.listener.InteractListener
import com.github.albardoo02.scriptBlockNext.listener.ScriptListener
import com.github.albardoo02.scriptBlockNext.manager.MessageManager
import com.github.albardoo02.scriptBlockNext.hook.PlaceholderManager
import com.github.albardoo02.scriptBlockNext.hook.VaultManager
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ScriptBlockNext : JavaPlugin() {

    companion object {
        lateinit var instance: ScriptBlockNext
        private set
    }

    override fun onEnable() {
        instance = this

        MessageManager.init(this)

        DiscordSRVManager.init(this)
        LuckPermsManager.init(this)
        MythicMobsManager.init(this)
        PlaceholderManager.init(this)
        VaultManager.init(this)

        disableAndRenameOldPlugin()

        val hookedPlugins = mutableListOf<String>()
        if (server.pluginManager.isPluginEnabled("PlaceholderAPI")) hookedPlugins.add("PlaceholderAPI")
        if (MythicMobsManager.isHooked) hookedPlugins.add("MythicMobs")
        if (LuckPermsManager.isHooked) hookedPlugins.add("LuckPerms")

        if (hookedPlugins.isNotEmpty()) {
            logger.info("外部プラグインと連携しました: ${hookedPlugins.joinToString(", ")}")
        } else {
            logger.info("連携している外部プラグインはありません。")
        }

        val scriptCommand = ScriptCommand()
        getCommand("scriptblocknext")?.let {
            it.setExecutor(scriptCommand)
            it.tabCompleter = scriptCommand
        }
        ScriptManager.init(this)
        server.pluginManager.registerEvents(InteractListener(), this)
        server.pluginManager.registerEvents(ScriptListener(), this)
        logger.info("ScriptBlockNext has been enabled.")
    }

    override fun onDisable() {
        logger.info("ScriptBlockNext has been disabled.")
    }

    private fun disableAndRenameOldPlugin() {
        val pluginManager = server.pluginManager
        val oldPlugin = pluginManager.getPlugin("ScriptBlockPlus") ?: return
        logger.info("ScriptBlockPlusが検知されました。競合を防ぐため、安全に無効化を試みます...")
        pluginManager.disablePlugin(oldPlugin)
        logger.info("-> ScriptBlockPlusをメモリ上で無効化しました。")
        try {
            val jarFile = File(oldPlugin.javaClass.protectionDomain.codeSource.location.toURI())
            if (jarFile.extension == "jar") {
                val bakFile = File(jarFile.parentFile, jarFile.name + ".bak")
                if (jarFile.renameTo(bakFile)) {
                    logger.info("-> 成功: ${jarFile.name} を ${bakFile.name} にリネームしました！次回の起動時からは読み込まれません。")
                } else {
                    logger.warning("===================================================")
                    logger.warning("【警告】OSの制限により、ScriptBlockPlus.jar の自動リネームに失敗しました。")
                    logger.warning("現在の一時的な無効化には成功していますが、次回起動時に再び読み込まれてしまいます。")
                    logger.warning("サーバーを一度停止し、手動でプラグインフォルダから ScriptBlockPlus.jar を削除してください！")
                    logger.warning("===================================================")
                }
            }
        } catch (e: Exception) {
            logger.warning("JARファイルの特定に失敗しました。手動で旧プラグインを削除してください。")
        }
    }
}