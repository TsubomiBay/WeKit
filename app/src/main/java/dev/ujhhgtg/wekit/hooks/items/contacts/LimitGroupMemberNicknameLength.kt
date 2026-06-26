package dev.ujhhgtg.wekit.hooks.items.contacts

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import de.robv.android.xposed.XC_MethodHook
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.wekit.hooks.api.ui.WeChatMessageViewApi
import dev.ujhhgtg.wekit.hooks.core.ClickableHookItem
import dev.ujhhgtg.wekit.hooks.core.HookItem
import dev.ujhhgtg.wekit.preferences.WePrefs.Companion.prefOption
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.DefaultColumn
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.android.showToast

@HookItem(
    name = "限制群成员昵称长度",
    categories = ["聊天"],
    description = "限制群聊中成员昵称的最大显示长度（不计模块注入的文本）"
)
object LimitGroupMemberNicknameLength : ClickableHookItem(), WeChatMessageViewApi.ICreateViewListener {

    private var maxNicknameLength by prefOption("max_nickname_length", 10)

    override fun onEnable() {
        WeChatMessageViewApi.addListener(this)
    }

    override fun onDisable() {
        WeChatMessageViewApi.removeListener(this)
    }

    override fun onClick(context: Context) {
        showComposeDialog(context) {
            var value by remember { mutableStateOf(maxNicknameLength.toString()) }

            AlertDialogContent(
                title = { Text("限制群成员昵称长度") },
                text = {
                    DefaultColumn {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it.filter { ch -> ch.isDigit() } },
                            label = { Text("最大字符数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                dismissButton = {
                    TextButton(onDismiss) { Text("取消") }
                },
                confirmButton = {
                    Button(onClick = {
                        val lenInput = value.toIntOrNull()
                        if (lenInput == null || lenInput <= 0) {
                            showToast("数字格式不正确!")
                            return@Button
                        }
                        maxNicknameLength = lenInput
                        onDismiss()
                    }) { Text("确定") }
                }
            )
        }
    }

    override fun onCreateView(param: XC_MethodHook.MethodHookParam, view: View) {
        val msgInfo = WeChatMessageViewApi.getMsgInfoFromParam(param)
        if (!msgInfo.isInGroupChat) return
        if (msgInfo.isSend != 0) return

        val textView = view.tag.reflekt()
            .firstField { name = "userTV"; superclass() }
            .get() as? TextView? ?: return

        val currentText = textView.text ?: return
        val maxLen = maxNicknameLength
        if (maxLen <= 0) return

        var pureStart = 0
        var pureEnd = currentText.length

        // 精准识别并剥离由其他模块注入的 Prefix (身份标签) 和 Suffix (实名尾字)
        if (currentText is Spanned) {
            // 1. 识别开头的群身份 Badge (DisplayGroupMemberRoles 注入的是 ReplacementSpan)
            val replacementSpans = currentText.getSpans(0, currentText.length, ReplacementSpan::class.java)
            val roleSpan = replacementSpans.firstOrNull { currentText.getSpanStart(it) == 0 }
            if (roleSpan != null) {
                pureStart = currentText.getSpanEnd(roleSpan)
                // 跳过标签后面的空格 " "
                if (pureStart < currentText.length && currentText[pureStart] == ' ') {
                    pureStart++
                }
            }

            // 2. 识别结尾的实名尾字 (DisplayGroupMemberRealNamesLastChar 注入的是 ForegroundColorSpan)
            val colorSpans = currentText.getSpans(0, currentText.length, ForegroundColorSpan::class.java)
            val realNameSpan = colorSpans.firstOrNull { currentText.getSpanEnd(it) == currentText.length }
            if (realNameSpan != null) {
                pureEnd = currentText.getSpanStart(realNameSpan)
            }
        }

        if (pureStart >= pureEnd) return

        // 提取出真正原始的昵称部分
        val pureNickname = currentText.subSequence(pureStart, pureEnd).toString()

        // 判断纯昵称是否超出限制
        if (pureNickname.length > maxLen) {
            val truncated = pureNickname.take(maxLen) + "..."

            // 重新组装 Spannable，这样可以完美保留原有前后缀的各类 Span（样式、背景等）
            val sb = SpannableStringBuilder()

            // 拼接原有的 Prefix 及其 Span
            if (pureStart > 0) {
                sb.append(currentText.subSequence(0, pureStart))
            }

            // 拼接截断后的核心昵称
            sb.append(truncated)

            // 拼接原有的 Suffix 及其 Span
            if (pureEnd < currentText.length) {
                sb.append(currentText.subSequence(pureEnd, currentText.length))
            }

            textView.text = sb
        }
    }
}
