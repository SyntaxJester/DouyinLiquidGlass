package com.autumn.douyin.liquidglass.status

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A tiny status screen. When Xposed is active the module patches
 * [isModuleActive] to return true at runtime, so this doubles as an
 * "is it working?" check. Everything else is just informational.
 */
class ModuleStatusActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StatusScreen(active = isModuleActive())
                }
            }
        }
    }

    /** Overwritten by Xposed at runtime when the module is loaded. */
    private fun isModuleActive(): Boolean = false
}

@Composable
private fun StatusScreen(active: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("抖音液态玻璃底栏", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (active) "✅ 模块已激活（Xposed 环境正常）"
            else "⚠️ 模块未激活：请在 LSPosed 中启用并勾选作用域「抖音」，然后强制停止抖音重开。",
            style = MaterialTheme.typography.bodyLarge
        )
        Text("使用说明", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. 需要 Android 13+（AGSL 运行时着色器要求）。\n" +
                "2. 在 LSPosed 管理器启用本模块，作用域勾选「抖音」。\n" +
                "3. 强制停止抖音后重新打开，底栏应替换为液态玻璃悬浮条。\n" +
                "4. 若底栏没对齐，是抖音版本更新导致原生底栏结构变化 —— 用\n" +
                "   adb logcat -s DouyinLiquidGlass 查看 NativeBottomBarLocator\n" +
                "   打印的候选列表，在代码里针对该版本补上匹配规则。",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("参数调节", style = MaterialTheme.typography.titleMedium)
        Text(
            "所有可调项都在 settings/ModuleSettings.kt：玻璃高度、圆角、模糊半径、\n" +
                "折射强度、色散、高光、边距、着色等，改完重新编译即可。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
