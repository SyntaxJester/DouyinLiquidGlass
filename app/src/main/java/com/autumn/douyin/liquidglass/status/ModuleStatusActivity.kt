package com.autumn.douyin.liquidglass.status

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autumn.douyin.liquidglass.settings.ModuleSettings
import com.autumn.douyin.liquidglass.settings.SettingsStore
import kotlin.math.roundToInt

/**
 * The module's launcher UI. It shows whether Xposed is active AND lets the user
 * tune every glass parameter live. Values are written to SharedPreferences
 * (world-readable via LSPosed's prefs bridge) so the hook inside Douyin picks
 * them up next time an activity resumes.
 */
class ModuleStatusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = SettingsStore.openAppPrefs(this)
        val initial = SettingsStore.load(prefs)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        active = isModuleActive(),
                        initial = initial,
                        onSave = { s -> SettingsStore.save(prefs, s) }
                    )
                }
            }
        }
    }

    /** Overwritten by the module's own hook at runtime → returns true. */
    private fun isModuleActive(): Boolean = false
}

@Composable
private fun SettingsScreen(
    active: Boolean,
    initial: ModuleSettings,
    onSave: (ModuleSettings) -> Unit
) {
    var s by remember { mutableStateOf(initial) }

    fun update(next: ModuleSettings) {
        s = next
        onSave(next) // autosave on every change
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("抖音液态玻璃底栏", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (active) "✅ 模块已激活（Xposed 环境正常）"
                    else "⚠️ 模块未激活",
                    style = MaterialTheme.typography.titleMedium
                )
                if (!active) {
                    Text(
                        "请在 LSPosed 启用本模块，作用域勾选「抖音」，然后强制停止抖音重开。" +
                            "需要 Android 13+。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    "改动即时保存。回到抖音后切换一次页面（或重进）即可生效，无需重启抖音。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        SwitchRow("启用模块", s.enabled) { update(s.copy(enabled = it)) }
        SwitchRow(
            "手动贴底放置（推荐先开着）", s.manualPlacement,
            hint = "开启后玻璃条固定贴屏幕底部，任何抖音版本都能显示；关闭则尝试自动对齐原生底栏。"
        ) { update(s.copy(manualPlacement = it)) }
        SwitchRow(
            "注入时弹 Toast 提示", s.debugToast,
            hint = "在抖音里注入成功时弹一条提示，方便确认模块生效。稳定后可关。"
        ) { update(s.copy(debugToast = it)) }

        SectionTitle("尺寸与位置")
        SliderRow("玻璃高度", s.barHeightDp, 36f, 96f, "dp") { update(s.copy(barHeightDp = it)) }
        SliderRow("圆角", s.cornerRadiusDp, 0f, 48f, "dp") { update(s.copy(cornerRadiusDp = it)) }
        SliderRow("左右边距", s.horizontalMarginDp, 0f, 48f, "dp") { update(s.copy(horizontalMarginDp = it)) }
        SliderRow("底部偏移（手动模式）", s.manualBottomOffsetDp, 0f, 96f, "dp") { update(s.copy(manualBottomOffsetDp = it)) }
        SliderRow("底部间距（自动模式）", s.bottomMarginDp, 0f, 64f, "dp") { update(s.copy(bottomMarginDp = it)) }

        SectionTitle("玻璃效果")
        SliderRow("模糊半径", s.blurRadiusDp, 0f, 48f, "dp") { update(s.copy(blurRadiusDp = it)) }
        SliderRow("折射高度", s.refractionHeightDp, 0f, 40f, "dp") { update(s.copy(refractionHeightDp = it)) }
        SliderRow("折射强度", s.refractionAmountDp, 0f, 64f, "dp") { update(s.copy(refractionAmountDp = it)) }
        SliderRow("高光强度", s.highlightAlpha, 0f, 1f, "", decimals = 2) { update(s.copy(highlightAlpha = it)) }
        SwitchRow("色散（彩色边缘）", s.chromaticAberration) { update(s.copy(chromaticAberration = it)) }
        SwitchRow("深度凸起", s.depthEffect) { update(s.copy(depthEffect = it)) }

        SectionTitle("磨砂着色（透明度)")
        val tintAlpha = ((s.tintColor ushr 24) and 0xFF) / 255f
        SliderRow("白色磨砂浓度", tintAlpha, 0f, 0.6f, "", decimals = 2) {
            val a = (it * 255).roundToInt().coerceIn(0, 255)
            update(s.copy(tintColor = (a shl 24) or 0x00FFFFFF))
        }

        SwitchRow("遮住原生底栏", s.hideNativeBar) { update(s.copy(hideNativeBar = it)) }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { update(ModuleSettings.DEFAULT) }) {
                Text("恢复默认")
            }
            Button(onClick = { onSave(s) }) {
                Text("保存")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "定位调试：若自动模式没对齐，用 adb logcat -s DouyinLiquidGlass 查看候选底栏，" +
                "把匹配的 id/类名填进 NativeBottomBarLocator。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SwitchRow(
    label: String,
    value: Boolean,
    hint: String? = null,
    onChange: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = value, onCheckedChange = onChange)
        }
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    unit: String,
    decimals: Int = 0,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            val shown = if (decimals == 0) {
                "${value.roundToInt()}$unit"
            } else {
                val f = "%.${decimals}f".format(value)
                "$f$unit"
            }
            Text(shown, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max
        )
    }
}
