package dev.ujhhgtg.wekit.hooks.items.chat

import de.robv.android.xposed.XC_MethodHook
import dev.ujhhgtg.wekit.hooks.api.core.WeConversationApi
import dev.ujhhgtg.wekit.hooks.api.ui.WeHomeScreenPopupMenuApi
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.hooks.core.SwitchHookItem
import dev.ujhhgtg.wekit.ui.utils.VisibilityIcon
import dev.ujhhgtg.wekit.ui.utils.VisibilityOffIcon

@HookItem(name = "显隐全部对话", categories = ["聊天", "首页右上角菜单"], description = "在首页右上角菜单添加菜单项, 可显示或隐藏全部对话")
object ToggleAllConversationsVisibility : SwitchHookItem(), WeHomeScreenPopupMenuApi.IMenuItemsProvider {

    override fun onEnable() {
        WeHomeScreenPopupMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeHomeScreenPopupMenuApi.removeProvider(this)
    }

    override fun getMenuItems(param: XC_MethodHook.MethodHookParam): List<WeHomeScreenPopupMenuApi.MenuItem> {
        return listOf(
            WeHomeScreenPopupMenuApi.MenuItem(
                777010, "显示对话", VisibilityIcon
            ) {
                WeConversationApi.setAllConversationVisibility(true)
            },
            WeHomeScreenPopupMenuApi.MenuItem(
                777011, "隐藏对话", VisibilityOffIcon
            ) {
                WeConversationApi.setAllConversationVisibility(false)
            },
        )
    }
}
