package com.github.albardoo02.scriptBlockNext.hook

import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

object VaultManager {
    var isHooked = false
        private set

    private var economy: Economy? = null
    private var permission: Permission? = null

    val hasPermissionProvider: Boolean get() = permission != null

    fun init(plugin: Plugin) {
        if (plugin.server.pluginManager.isPluginEnabled("Vault")) {
            val rsp = Bukkit.getServicesManager().getRegistration(Economy::class.java)
            if (rsp != null) {
                economy = rsp.provider
                isHooked = true
                plugin.logger.info("Vault hooked successfully.")
            }
            permission = Bukkit.getServicesManager().getRegistration(Permission::class.java)?.provider
        }
    }

    fun has(player: OfflinePlayer, amount: Double): Boolean {
        return economy?.has(player, amount) ?: false
    }

    fun balance(player: OfflinePlayer): Double = economy?.getBalance(player) ?: 0.0

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        return economy?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false
    }

    fun addPermission(player: Player, node: String): Boolean = permission?.playerAdd(null as String?, player, node) ?: false

    fun removePermission(player: Player, node: String): Boolean = permission?.playerRemove(null as String?, player, node) ?: false
}
