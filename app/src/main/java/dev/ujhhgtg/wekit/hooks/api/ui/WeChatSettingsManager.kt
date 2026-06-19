package dev.ujhhgtg.wekit.hooks.api.ui

import android.app.Activity
import android.content.Context
import com.android.dx.stock.ProxyBuilder
import de.robv.android.xposed.XC_MethodHook
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.reflekt.utils.toClass
import dev.ujhhgtg.wekit.utils.hookAfterDirectly
import dev.ujhhgtg.wekit.utils.hookBeforeDirectly
import dev.ujhhgtg.wekit.utils.reflection.buildClass
import dev.ujhhgtg.wekit.utils.reflection.createProxyBuilder
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class WeChatSettingsManager(
    private val classBaseSettingItem: Class<*>,
    private val classSettingLocation: Class<*>,
    private val classSettingItemClassesProvider: Class<*>,
    private val classBaseSettingPrefUI: Class<*>,
    private val classBaseSettingUI: Class<*>,
    private val methodResourceHelperGetStringById: Method,
    // Method names resolved dynamically via DexKit
    private val mGetPageGroupItemClass: String, // Natively: C6()
    private val mGetLevel: String,              // Natively: K6()
    private val mOnClick: String,               // Natively: Q6()
    private val mGetKey: String,                // Natively: w6()
    private val mGetSettingLocation: String,    // Natively: x6()
    private val mGetNameResId: String,          // Natively: z6()
    private val mGetGroupNameResId: String      // Natively: u6()
) {
    private val TAG = This.Class.simpleName

    private val registeredItems = CopyOnWriteArrayList<ItemRegistration>()
    private val stringPool = ConcurrentHashMap<Int, String>()
    private var dynamicResIdCounter = -2000 // Safely scale downward away from positive Android R system IDs

    private var contextGetStringUnhook: XC_MethodHook.Unhook? = null
    private var resourcesGetStringUnhook: XC_MethodHook.Unhook? = null

    /**
     * Configuration specification for a custom setting entry.
     */
    class SettingItemSpec {
        var key: String = ""
        var title: String = ""
        var groupTitle: String? = null
        var pageClass: Class<*>? = null          // If null, resolves to the host page container (or self if sub-page)
        var frontItemClass: Class<*>? = null     // The element this row should sit behind
        var insertBeforeClass: Class<*>? = null  // Optional: Element that should now sit behind this one
        var level: Int = 1
        var onClick: ((Activity) -> Unit)? = null
    }

    private class ItemRegistration(
        val spec: SettingItemSpec,
        val proxyClass: Class<*>
    )

    private fun allocateString(value: String): Int {
        val id = dynamicResIdCounter--
        stringPool[id] = value
        return id
    }

    /**
     * Builds and registers a custom UI component using dynamic proxying.
     */
    fun createItem(init: SettingItemSpec.() -> Unit): Class<*> {
        val spec = SettingItemSpec().apply(init)

        val titleResId = allocateString(spec.title)
        val groupResId = spec.groupTitle?.let { allocateString(it) } ?: titleResId

        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                mGetPageGroupItemClass -> spec.pageClass ?: proxy.javaClass
                mGetLevel -> spec.level
                mOnClick -> {
                    val activity = args[0] as Activity
                    spec.onClick?.invoke(activity) ?: ProxyBuilder.callSuper(proxy, method, *args)
                }
                mGetKey -> spec.key
                mGetSettingLocation -> {
                    val resolvedPage = spec.pageClass ?: proxy.javaClass
                    ProxyBuilder.callSuper(proxy, method, *args)
                    classSettingLocation.constructors.first().newInstance(resolvedPage, spec.frontItemClass)
                }
                mGetNameResId -> titleResId
                mGetGroupNameResId -> if (spec.groupTitle != null) groupResId else null
                else -> ProxyBuilder.callSuper(proxy, method, *args)
            }
        }

        val proxyClass = createProxyBuilder(
            classBaseSettingItem,
            arrayOf("androidx.appcompat.app.AppCompatActivity".toClass()),
            handler
        ).buildClass(handler)

        // Automatically hijack the relative position linking chain if downstream intercept is targeted
        spec.insertBeforeClass?.let { targetClass ->
            val resolvedPage = spec.pageClass ?: proxyClass
            targetClass.reflekt()
                .firstMethod { returnType = classSettingLocation }
                .hookBeforeDirectly {
                    result = classSettingLocation.constructors.first().newInstance(resolvedPage, proxyClass)
                }
        }

        registeredItems.add(ItemRegistration(spec, proxyClass))
        return proxyClass
    }

    /**
     * Installs the composite platform layout system and resource hook bindings.
     */
    @Suppress("UNCHECKED_CAST")
    fun install() {
        // 1. Inject components into the global UI layout provider maps
        classSettingItemClassesProvider.reflekt().firstMethod()
            .hookAfterDirectly {
                val originalMap = result as? Map<Any, Any> ?: return@hookAfterDirectly
                val mutMap = originalMap.toMutableMap()

                val groupedByPage = registeredItems.groupBy { it.spec.pageClass ?: it.proxyClass }
                for ((page, items) in groupedByPage) {
                    val classesToAdd = items.map { it.proxyClass }
                    val existingCollection = mutMap[page] as? Collection<Any>

                    if (existingCollection != null) {
                        val updatedSet = LinkedHashSet(existingCollection)
                        updatedSet.addAll(classesToAdd)
                        mutMap[page] = updatedSet
                    } else {
                        mutMap[page] = LinkedHashSet(classesToAdd)
                    }
                }
                result = mutMap
            }

        // 2. Lifecycle attachment engine injection
        classBaseSettingPrefUI.reflekt()
            .firstMethod { name = "superImportUIComponents" }
            .hookAfterDirectly {
                val currentUiName = thisObject.javaClass.name
                if (!currentUiName.endsWith("MainSettingsUI") && !currentUiName.endsWith("CommonSettingsUI")) return@hookAfterDirectly

                @Suppress("UNCHECKED_CAST")
                val layoutComponentSet = args[0] as? HashSet<Class<*>> ?: return@hookAfterDirectly

                // Active injection of proxy blueprints to initialize components
                for (item in registeredItems) {
                    layoutComponentSet.add(item.proxyClass)
                }

                // Hook String allocations safely for context strings
                contextGetStringUnhook = Context::class.reflekt()
                    .firstMethod { name = "getString"; parameters(Int::class) }
                    .hookBeforeDirectly {
                        stringPool[args[0] as Int]?.let { result = it }
                    }

                // Hook standard ResourceHelper mapping layers
                resourcesGetStringUnhook = methodResourceHelperGetStringById.hookBeforeDirectly {
                    stringPool[args[1] as Int]?.let { result = it }
                }
            }

        // 3. Dynamic layout resource clean unhook operations on destroy
        classBaseSettingUI.reflekt()
            .firstMethod { name = "onDestroy" }
            .hookAfterDirectly {
                val currentUiName = thisObject.javaClass.name
                if (!currentUiName.endsWith("MainSettingsUI") && !currentUiName.endsWith("CommonSettingsUI")) return@hookAfterDirectly

                contextGetStringUnhook?.unhook(); contextGetStringUnhook = null
                resourcesGetStringUnhook?.unhook(); resourcesGetStringUnhook = null
            }
    }
}
