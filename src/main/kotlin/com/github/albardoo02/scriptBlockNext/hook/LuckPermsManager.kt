package com.github.albardoo02.scriptBlockNext.hook

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.InheritanceNode
import org.bukkit.entity.Player

object LuckPermsManager {

    var isHooked = false
        private set

    fun init(plugin: ScriptBlockNext) {
        if (plugin.server.pluginManager.isPluginEnabled("LuckPerms")) {
            isHooked = true
        }
    }

    fun addGroup(player: Player, group: String) {
        try {
            val api = LuckPermsProvider.get()
            val user = api.userManager.getUser(player.uniqueId) ?: return
            val node = InheritanceNode.builder(group).build()
            user.data().add(node)
            api.userManager.saveUser(user)
        } catch (e: Exception) {
        }
    }

    fun removeGroup(player: Player, group: String) {
        try {
            val api = LuckPermsProvider.get()
            val user = api.userManager.getUser(player.uniqueId) ?: return
            val node = InheritanceNode.builder(group).build()
            user.data().remove(node)
            api.userManager.saveUser(user)
        } catch (e: Exception) {
        }
    }

    fun hasGroup(player: Player, group: String): Boolean {
        if (!isHooked) return false
        try {
            val api = LuckPermsProvider.get()
            val user = api.userManager.getUser(player.uniqueId) ?: return false
            return user.getNodes().stream().anyMatch { it is InheritanceNode && it.groupName.equals(group, ignoreCase = true) }
        } catch (e: Exception) {
            return false
        }
    }
}