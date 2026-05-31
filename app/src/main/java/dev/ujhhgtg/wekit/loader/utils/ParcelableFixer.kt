package dev.ujhhgtg.wekit.loader.utils

import android.content.Intent
import android.os.Bundle
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.wekit.loader.abc.IHookBridge
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.applyHook
import dev.ujhhgtg.wekit.utils.reflection.BString
import dev.ujhhgtg.wekit.utils.reflection.ClassLoaders
import dev.ujhhgtg.wekit.utils.reflection.asResolver

object ParcelableFixer {

    private val TAG = This.Class.simpleName

    lateinit var hybridClassLoader: ClassLoader
        private set

    private var initialized = false

    @Suppress("unused")
    fun init() {
        if (initialized) return
        initialized = true

        hybridClassLoader = object : ClassLoader(ClassLoaders.HOST) {
            override fun findClass(name: String): Class<*> = ClassLoaders.MODULE.loadClass(name)
        }

        hookIntentMethods()
    }

    private fun fixIntentExtrasClassLoader(intent: Intent?) {
        val cl = hybridClassLoader
        runCatching { intent?.setExtrasClassLoader(cl) }
    }

    private fun hookIntentMethods() {
        val hook = object : IHookBridge.IMemberHookCallback {
            override fun beforeHookedMember(param: IHookBridge.IMemberHookParam) {
                (param.thisObject as? Intent)?.let { fixIntentExtrasClassLoader(it) }
            }

            override fun afterHookedMember(param: IHookBridge.IMemberHookParam) {
                val cl = hybridClassLoader
                (param.result as? Bundle)?.classLoader = cl
            }
        }

        runCatching {
            Intent::class.asResolver().apply {
                firstMethod { name = "getExtras" }.applyHook(hook)

                val clazz = Class::class.java
                firstMethod { name = "getBundleExtra"; parameters(BString) }.applyHook(hook)
                firstMethod { name = "getParcelableExtra"; parameters(BString) }.applyHook(hook)
                firstMethod { name = "getParcelableArrayListExtra"; parameters(BString) }.applyHook(hook)
                firstMethod { name = "getSerializableExtra"; parameters(BString) }.applyHook(hook)
                firstMethod { name = "getParcelableExtra"; parameters(BString, clazz) }.applyHook(hook)
                firstMethod { name = "getParcelableArrayListExtra"; parameters(BString, clazz) }.applyHook(hook)
                firstMethod { name = "getSerializableExtra"; parameters(BString, clazz) }.applyHook(hook)
            }
        }.onFailure { WeLogger.w(TAG, "failed to hook some Intent methods: ${it.message}") }
    }
}
