package com.hxposed.ovo.hook

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/*
让 TIM 聊天界面里每条消息都显示头像【TIM 4.1.0（4050）】

com.tencent.mobileqq.aio.msg.ab 的四个静态谓词决定消息在列表中的"独立/合并"归类：
e = 单条消息, b = 合并组首条, c = 合并组中间, d = 合并组末条
全部强制为"独立消息"后，每条消息各自成为一组，头像因此逐条渲染。
*/
object ChatAvatarHook {

    private const val MERGE_CLASS = "com.tencent.mobileqq.aio.msg.ab"
    private const val MERGE_INFO_CLASS = "com.tencent.mobileqq.aio.msg.p"

    fun install(classLoader: ClassLoader) {
        val mergeClass = runCatching { XposedHelpers.findClass(MERGE_CLASS, classLoader) }.getOrNull()
        if (mergeClass == null) return log("未找到 $MERGE_CLASS，TIM 版本不匹配，跳过")
        val infoClass = runCatching { XposedHelpers.findClass(MERGE_INFO_CLASS, classLoader) }.getOrNull()
        if (infoClass == null) return log("未找到 $MERGE_INFO_CLASS，TIM 版本不匹配，跳过")

        mapOf("e" to true, "b" to false, "c" to false, "d" to false).forEach { (method, value) ->
            runCatching {
                XposedHelpers.findAndHookMethod(
                    mergeClass,
                    method,
                    infoClass,
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam?): Any? = value
                    }
                )
                log("已 hook $MERGE_CLASS#$method -> $value")
            }.onFailure { log("hook $MERGE_CLASS#$method 失败: $it") }
        }
    }
    private fun log(message: String) = XposedBridge.log("[HXposed] $message")
}
