package dev.ujhhgtg.wekit.hooks.items.system

import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.hooks.core.SwitchHookItem
import org.luckypray.dexkit.DexKitBridge

@HookItem(name = "禁用「转发截图」提示", categories = ["系统与隐私"], description = "你在教我做事?")
object DisableShareScreenshotToast : SwitchHookItem(), IResolveDex {

    private val methodDisplayToast by dexMethod {
        searchPackages("com.tencent.mm.ui.feature.api.screenshot")
        matcher {
            usingEqStrings("MicroMsg.ScreenShotShareService", "showShareTongue, shareTongue already showing, reset onClick & countDown")
        }
    }

    override fun onEnable() {
        methodDisplayToast.hookBefore {
            result = null
        }
    }
}
