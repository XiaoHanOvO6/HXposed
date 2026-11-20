package com.hxposed.ovo.hook

import android.content.Context
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Proxy

/**
 * 在 TIM 设置页「账号安全」下方插入 HXposed 入口（TIM 4.1.0 / 4050）。
 *
 * 设置页条目由 MainSettingConfigProvider#d(Context) 统一组装：
 * 返回 List<c>，每个 c 是一组，c.b() 是组内条目 List<d>。
 * 第一组依次为 账号信息 / 手机号 / 账号安全（AccountSecurityItemProcessor），
 * 向该组追加一个 process.h 即可得到标准单行条目。
 */
object SettingEntryHook {

    private const val PROVIDER_CLASS = "com.tencent.mobileqq.setting.main.MainSettingConfigProvider"
    private const val ITEM_CLASS = "com.tencent.mobileqq.setting.processor.h"
    private const val ACCOUNT_SECURITY_CLASS =
        "com.tencent.mobileqq.setting.main.processor.AccountSecurityItemProcessor"
    private const val R_DRAWABLE_CLASS = "com.tencent.mobileqq.R\$drawable"
    private const val ICON_NAME = "qui_tuning"

    private const val ITEM_ID = 0x4858
    private const val TITLE = "HXposed"
    private const val TOAST_TEXT = "泥嚎~"

    fun install(classLoader: ClassLoader) {
        runCatching {
            val itemClass = XposedHelpers.findClass(ITEM_CLASS, classLoader)
            XposedHelpers.findAndHookMethod(
                PROVIDER_CLASS,
                classLoader,
                "d",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam?) {
                        param ?: return
                        runCatching { inject(param, itemClass, classLoader) }
                            .onFailure { log("注入设置项失败: $it") }
                    }
                }
            )
            log("已 hook $PROVIDER_CLASS#d")
        }.onFailure { log("设置页 Hook 失败，TIM 版本可能不匹配: $it") }
    }

    @Suppress("UNCHECKED_CAST")
    private fun inject(param: XC_MethodHook.MethodHookParam, itemClass: Class<*>, classLoader: ClassLoader) {
        val context = param.args.getOrNull(0) as? Context ?: return
        val groups = param.result as? MutableList<Any> ?: return
        if (groups.isEmpty()) return

        val group = groups.firstOrNull { group ->
            itemsOf(group)?.any { it.javaClass.name == ACCOUNT_SECURITY_CLASS } == true
        } ?: groups.first()

        val items = itemsOf(group) ?: return
        if (items.any { itemIdOf(it) == ITEM_ID }) return

        val item = XposedHelpers.newInstance(itemClass, context, ITEM_ID, TITLE, drawableId(context))
        setClickAction(itemClass, item, context, classLoader)

        val index = items.indexOfFirst { it.javaClass.name == ACCOUNT_SECURITY_CLASS }
        items.add(if (index >= 0) index + 1 else items.size, item)
        log("已在设置页插入 $TITLE")
    }

    /**
     * 用宿主 ClassLoader 生成 kotlin.jvm.functions.Function0 的代理，
     * 避免模块自己的 Kotlin lambda 与宿主类型不是同一个 Class 而抛 NoSuchMethodError。
     */
    private fun setClickAction(itemClass: Class<*>, item: Any, context: Context, classLoader: ClassLoader) {
        val function0Class = XposedHelpers.findClass("kotlin.jvm.functions.Function0", classLoader)
        val unitInstance = runCatching {
            XposedHelpers.getStaticObjectField(XposedHelpers.findClass("kotlin.Unit", classLoader), "INSTANCE")
        }.getOrNull()

        val click = Proxy.newProxyInstance(classLoader, arrayOf(function0Class)) { proxy, method, args ->
            when (method.name) {
                "invoke" -> {
                    Toast.makeText(context, TOAST_TEXT, Toast.LENGTH_SHORT).show()
                    unitInstance
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "HXposedClick"
                else -> null
            }
        }
        itemClass.getDeclaredMethod("s", function0Class).invoke(item, click)
    }

    @Suppress("UNCHECKED_CAST")
    private fun itemsOf(group: Any): MutableList<Any>? =
        runCatching { XposedHelpers.callMethod(group, "b") as? MutableList<Any> }.getOrNull()

    private fun itemIdOf(item: Any): Int? =
        runCatching { XposedHelpers.callMethod(item, "e") as? Int }.getOrNull()

    private fun drawableId(context: Context): Int {
        val id = runCatching {
            Class.forName(R_DRAWABLE_CLASS, false, context.classLoader)
                .getField(ICON_NAME)
                .getInt(null)
        }.getOrNull() ?: 0
        if (id != 0) return id
        return context.resources.getIdentifier(ICON_NAME, "drawable", context.packageName)
    }

    private fun log(message: String) = XposedBridge.log("[HXposed] $message")
}
