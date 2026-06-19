package dev.ujhhgtg.wekit.hooks.api.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.android.dx.stock.ProxyBuilder
import com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI
import com.tencent.mm.plugin.setting.ui.setting_new.base.BaseSettingPrefUI
import com.tencent.mm.plugin.setting.ui.setting_new.base.BaseSettingUI
import com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingAdditionHeaderSearch
import com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupAccountInfo
import com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupMain
import com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupPersonalInfo
import com.tencent.mm.ui.LauncherUI
import com.tencent.mm.ui.base.preference.IconPreference
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.createInstance
import dev.ujhhgtg.reflekt.utils.toClass
import dev.ujhhgtg.reflekt.utils.toClassOrNull
import dev.ujhhgtg.wekit.BuildConfig
import dev.ujhhgtg.wekit.constants.PackageNames
import dev.ujhhgtg.wekit.constants.Preferences
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexClass
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.hooks.core.ApiHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.ui.content.MainSettingsScreen
import dev.ujhhgtg.wekit.ui.utils.ExtensionIcon
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.hookBeforeDirectly
import dev.ujhhgtg.wekit.utils.reflection.buildClass
import dev.ujhhgtg.wekit.utils.reflection.createProxyBuilder
import dev.ujhhgtg.wekit.utils.reflection.int
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Modifier

@HookItem(name = "设置模块入口", categories = ["API"])
object WeSettingsInjector : ApiHookItem(), IResolveDex, WeHomeScreenPopupMenuApi.IMenuItemsProvider {

    private val methodSetKey by dexMethod()
    private val methodSetTitle by dexMethod()
    private val methodGetKey by dexMethod()
    private val methodAddPref by dexMethod()

    // method 2
    private val classSettingItemClassesProvider by dexClass(allowFailure = true) {
        matcher {
            usingEqStrings("Repairer_Setting")

            superClass {
                usingEqStrings("type")
            }
        }
    }
    private val classBaseSettingItem by dexClass(allowFailure = true) {
        matcher {
            usingEqStrings("", "activity", "context", "intent")

            addMethod {
                name = "<init>"
                paramTypes("androidx.appcompat.app.AppCompatActivity")
            }

            addInterface {
                className("com.tencent.mm.plugin.newtips.model", StringMatchType.StartsWith)
            }
        }
    }
    private val classSettingLocation by dexClass(allowFailure = true) {
        matcher {
            usingEqStrings("SettingLocation(parentGroup=", ", frontItem=")
        }
    }
    private val methodSettingGroupAccountInfoGetStringId by dexMethod(allowFailure = true) {
        matcher {
            declaredClass = "com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupAccountInfo"
            usingEqStrings("SettingGroup_Main_AccountInfo")
            returnType = "java.lang.String"
        }
    }
    private val methodSettingGroupAccountInfoReturns1 by dexMethod(allowFailure = true) {
        matcher {
            declaredClass = "com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupAccountInfo"
            usingNumbers(1)
            returnType = "int"
        }
    }
    private val methodSettingGroupPersonalInfoGetGroupNameResId by dexMethod(allowFailure = true) {
        matcher {
            declaredClass = "com.tencent.mm.plugin.setting.ui.setting_new.settings.SettingGroupPersonalInfo"
            returnType = "java.lang.Integer"
        }
    }
    private val methodResourceHelperGetStringById by dexMethod(allowFailure = true) {
        matcher {
            usingEqStrings("MicroMsg.ResourceHelper", "get string, resId %d, but context is null")
        }
    }

    private val TAG = This.Class.simpleName

    private const val PREFS_KEY = "wekit_settings_entry"
    private const val PREFS_TITLE = "${BuildConfig.TAG} 设置"
    private const val PREFERENCE_CLASS_NAME = "com.tencent.mm.ui.base.preference.Preference"

    @SuppressLint("NonUniqueDexKitData")
    override fun resolveDex(dexKit: DexKitBridge) {
        val prefClass = dexKit.findClass {
            matcher { className = PREFERENCE_CLASS_NAME }
        }.single()

        methodSetKey.find(dexKit, allowMultiple = true) {
            searchPackages("com.tencent.mm.ui.base.preference")
            matcher {
                declaredClass = PREFERENCE_CLASS_NAME
                returnType = "void"
                paramTypes("java.lang.String")
                usingStrings("Preference")
            }
        }

        val setTitleCandidates = prefClass.findMethod {
            matcher {
                returnType = "void"
                paramTypes("java.lang.CharSequence")
            }
        }
        if (setTitleCandidates.isNotEmpty()) {
            methodSetTitle.setDescriptor(setTitleCandidates.last())
        }

        val getKeyCandidates = prefClass.findMethod {
            matcher {
                paramCount = 0
                returnType = "java.lang.String"
            }
        }

        val targetGetKey = getKeyCandidates.firstOrNull { it.name != "toString" }

        if (targetGetKey != null) {
            methodGetKey.setDescriptor(targetGetKey)
        }

        val adapterClass = dexKit.findClass {
            searchPackages("com.tencent.mm.ui.base.preference")
            matcher {
                superClass = "android.widget.BaseAdapter"
                methods {
                    add {
                        modifiers = Modifier.PUBLIC
                        name = "getView"
                        paramCount = 3
                    }
                    add {
                        name = "<init>"
                        paramCount = 3
                    }
                }
            }
        }.singleOrNull()

        if (adapterClass != null) {
            methodAddPref.find(dexKit, allowMultiple = true) {
                searchPackages("com.tencent.mm.ui.base.preference")
                matcher {
                    declaredClass = adapterClass.name
                    paramTypes(PREFERENCE_CLASS_NAME, "int")
                    returnType = "void"
                }
            }
        }
    }

    override fun onEnable() {
        injectLegacy()

        // injectModernMethod1()
        injectModernMethod2()
        // injectModernMethod3()

        injectHomeScreenMenu()

        hookLauncherUi()
    }

    private fun injectLegacy() {
        val clsSettingsUi = "${PackageNames.WECHAT}.plugin.setting.ui.setting.SettingsUI"
            .toClassOrNull() ?: run {
            WeLogger.w(TAG, "legacy settings class not found, skipping")
            return
        }

        clsSettingsUi.reflekt().firstMethod {
            name = "initView"
            parameterCount = 0
        }.hookAfter {
            val activity = thisObject as Activity
            val context = activity as Context

            try {
                val prefInstance = IconPreference(context)

                methodSetKey.method.invoke(prefInstance, PREFS_KEY)
                methodSetTitle.method.invoke(prefInstance, PREFS_TITLE)

                val prefScreen = XposedHelpers.callMethod(activity, "getPreferenceScreen")

                methodAddPref.method.invoke(prefScreen, prefInstance, 0)

            } catch (e: Throwable) {
                WeLogger.e(TAG, "插入选项失败", e)
            }
        }

        clsSettingsUi.reflekt().firstMethod { name = "onPreferenceTreeClick" }
            .hookBefore {
                if (args.size < 2) return@hookBefore
                val preference = args[1] ?: return@hookBefore

                val key = methodGetKey.method.invoke(preference) as? String

                if (PREFS_KEY == key) {
                    val activity = thisObject as Activity

                    openSettingsDialog(activity)

                    result = true
                }
            }
    }

//    private fun injectModernMethod1() {
//        val newSettingsCls =
//            "com.tencent.mm.plugin.setting.ui.setting_new.base.BaseSettingPrefUI"
//                .toClassOrNull() ?: return
//
//        newSettingsCls.reflekt().firstMethod { name = "onCreate" }.hookAfter {
//            if (thisObject.javaClass.name
//                != "com.tencent.mm.plugin.setting.ui.setting_new.MainSettingsUI"
//            ) return@hookAfter
//
//            val activity = thisObject as Activity
//            activity.reflekt()
//                .firstMethod {
//                    name = "addTextOptionMenu"
//                    parameters(
//                        Int::class,
//                        String::class,
//                        MenuItem.OnMenuItemClickListener::class
//                    )
//                    superclass()
//                }
//                .invoke(0, BuildConfig.TAG, SettingsMenuItemClickListener(activity))
//        }
//    }

    const val WEKIT_SETTING_ITEM_NAME_RES_ID = -1337

    private val PAGE_GROUP_SETTING_ITEM_CLASS by lazy { SettingGroupMain::class.java }

    // or SettingGroupPrivacyPermission & SettingGroupNotify
    private val PARENT_SETTING_ITEM_CLASS by lazy { SettingAdditionHeaderSearch::class.java }
    private val CHILD_SETTING_ITEM_CLASS by lazy { SettingGroupPersonalInfo::class.java }

    private lateinit var mGetPageGroupItemClass: String
    private lateinit var mReturns1: String
    private lateinit var mOnClick: String
    private lateinit var mGetStringId: String
    private lateinit var mGetSettingLocation: String
    private lateinit var mGetNameResId: String
    private lateinit var mGetGroupNameResId: String

    private fun resolveMethodNames() {
        if (::mGetPageGroupItemClass.isInitialized) return

        // this is only used for resolving method names, so we'll hard-code SettingGroupAccountInfo
        SettingGroupAccountInfo::class.java.declaredMethods.run {
            mGetPageGroupItemClass = first { m -> m.returnType == Class::class.java }.name
            mReturns1 = methodSettingGroupAccountInfoReturns1.method.name
            mOnClick = first { m -> m.parameterCount == 3 }.name
            mGetStringId = methodSettingGroupAccountInfoGetStringId.method.name
            mGetSettingLocation =
                last { m -> m.returnType == classSettingLocation.clazz }.name
            mGetNameResId =
                last { m ->
                    m.returnType == int &&
                            m.name != methodSettingGroupAccountInfoReturns1.method.name
                }.name
            mGetGroupNameResId = methodSettingGroupPersonalInfoGetGroupNameResId.method.name

            // non-play 8.0.69: C6, K6, Q6, w6, x6, z6, u6
            // non-play 8.0.70: k7, r7, w7, g7, h7, j7, ...
            // non-play 8.0.71: p7, w7, B7, l7, m7, o7, ...
            // play 8.0.69 (3022): E6, N6, U6, A6, B6, D6, ...
            WeLogger.d(
                TAG,
                "resolved all method names: $mGetPageGroupItemClass, $mReturns1, $mOnClick, $mGetStringId, $mGetSettingLocation, $mGetNameResId, $mGetGroupNameResId"
            )
        }
    }

    @Suppress("FunctionName", "NOTHING_TO_INLINE")
    private inline fun SettingLocation(pageGroupClass: Class<*>, parentClass: Class<*>) = classSettingLocation.clazz.createInstance(pageGroupClass, parentClass)

    private val customSettingItemClass by lazy {
        resolveMethodNames()

        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                mGetPageGroupItemClass -> PAGE_GROUP_SETTING_ITEM_CLASS
                mReturns1 -> 1
                mOnClick -> openSettingsDialog(args[0] as Activity)
                mGetStringId -> "SettingGroup_Main_Other_WeKit"
                mGetSettingLocation -> SettingLocation(
                    PAGE_GROUP_SETTING_ITEM_CLASS,
                    PARENT_SETTING_ITEM_CLASS
                )
                mGetNameResId -> WEKIT_SETTING_ITEM_NAME_RES_ID
                mGetGroupNameResId -> WEKIT_SETTING_ITEM_NAME_RES_ID

                else -> ProxyBuilder.callSuper(
                    proxy,
                    method,
                    *args
                )
            }
        }

        createProxyBuilder(
            classBaseSettingItem.clazz,
            arrayOf("androidx.appcompat.app.AppCompatActivity".toClass()),
            handler
        ).buildClass(handler)
    }

    private fun injectModernMethod2() {
        "${PackageNames.WECHAT}.plugin.setting.ui.setting_new.settings.SettingGroupMain".toClassOrNull()
            ?: run {
                WeLogger.w(TAG, "modern settings class not found, skipping")
                return
            }

        // for name
        var contextGetStringUnhook: XC_MethodHook.Unhook? = null
        // for group name; we can also hook usingEqStrings("MicroMsg.ResourceHelper", "get string, resId %d, but context is null")
        var resourcesGetStringUnhook: XC_MethodHook.Unhook? = null

        // create dependency chain
        CHILD_SETTING_ITEM_CLASS.reflekt()
            .firstMethod {
                returnType = classSettingLocation.clazz
            }
            .hookBefore {
                result = SettingLocation(
                    PAGE_GROUP_SETTING_ITEM_CLASS,
                    customSettingItemClass
                )
            }

        // inject into all SettingItem::class map in order to be discovered
        classSettingItemClassesProvider.reflekt().firstMethod()
            .hookAfter {
                val map = result as? Map<*, *>? ?: return@hookAfter
                val originalSet = map.values.first() as LinkedHashSet<*>
                result = mapOf(map.keys.first() to originalSet + customSettingItemClass)
            }

        // inject into page
        BaseSettingPrefUI::class.reflekt()
            .firstMethod { name = "superImportUIComponents" }
            .hookAfter {
                if (thisObject !is MainSettingsUI) return@hookAfter

                // a simple way to inject string resource
                contextGetStringUnhook = Context::class.reflekt()
                    .firstMethod {
                        name = "getString"
                        parameters(Int::class)
                    }
                    .hookBeforeDirectly {
                        val resId = args[0] as Int
                        if (resId == WEKIT_SETTING_ITEM_NAME_RES_ID)
                            result = "${BuildConfig.TAG} 设置"
                    }

                resourcesGetStringUnhook = methodResourceHelperGetStringById.method
                    .hookBeforeDirectly {
                        val resId = args[1] as Int
                        if (resId == WEKIT_SETTING_ITEM_NAME_RES_ID)
                            result = "模块"
                    }

                @Suppress("UNCHECKED_CAST")
                val settingItemClasses = args[0] as HashSet<Class<*>>
                settingItemClasses.add(customSettingItemClass)
            }

        BaseSettingUI::class.reflekt()
            .firstMethod { name = "onDestroy" }
            .hookAfter {
                if (thisObject !is MainSettingsUI) return@hookAfter

                contextGetStringUnhook!!.unhook()
                contextGetStringUnhook = null

                resourcesGetStringUnhook!!.unhook()
                contextGetStringUnhook = null
            }
    }

    private fun injectHomeScreenMenu() {
        WeHomeScreenPopupMenuApi.addProvider(this)
    }

    override fun onDisable() {
        WeHomeScreenPopupMenuApi.removeProvider(this)
    }

    override fun getMenuItems(param: XC_MethodHook.MethodHookParam) = listOf(
        WeHomeScreenPopupMenuApi.MenuItem(
            0, "${BuildConfig.TAG} 设置", ExtensionIcon
        ) { openSettingsDialog(LauncherUI.getInstance()!!) }
    )

//    private fun injectModernMethod3() {
//        if (methodSettingGroupPluginOnClick.isPlaceholder) {
//            WeLogger.w(TAG, "methodSettingGroupPluginOnClick not found, skipping")
//            return
//        }
//        methodSettingGroupPluginOnClick.hookBefore {
//            val context = args[0] as Context
//            openSettingsDialog(context)
//            result = null
//        }
//    }

    private fun hookLauncherUi() {
        LauncherUI::class.reflekt().apply {
            firstMethod { name = "onCreate" }
                .hookBefore {
                    val activity = thisObject as Activity
                    val intent = activity.intent ?: return@hookBefore
                    val extra = intent.getStringExtra(BuildConfig.TAG) ?: return@hookBefore
                    if (extra == "2") {
                        Preferences.useActivityInsteadOfDialog = false
                    }
                    // wait for resources & theme to init
                    Handler(Looper.getMainLooper()).postDelayed({
                        openSettingsDialog(activity)
                    }, 500)
                }

            firstMethod { name = "onNewIntent" }
                .hookBefore {
                    val activity = thisObject as Activity
                    val intent = args[0] as? Intent? ?: return@hookBefore
                    val extra = intent.getStringExtra(BuildConfig.TAG) ?: return@hookBefore
                    if (extra == "2") {
                        Preferences.useActivityInsteadOfDialog = false
                    }
                    openSettingsDialog(activity)
                }
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun openSettingsDialog(activity: Activity) {
        MainSettingsScreen().show(activity)
    }

//    private class SettingsMenuItemClickListener(val context: Context) :
//        MenuItem.OnMenuItemClickListener {
//        override fun onMenuItemClick(p0: MenuItem): Boolean {
//            openSettingsDialog(context)
//            return true
//        }
//    }
}
