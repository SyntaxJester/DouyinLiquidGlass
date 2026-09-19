# 抖音液态玻璃底栏 (Douyin Liquid Glass)

一个 LSPosed 模块骨架，为抖音（`com.ss.android.ugc.aweme`）的底部导航栏叠加 **液态玻璃**效果：实时高斯模糊 + 边缘折射 + 色散 + 顶部高光。

液态玻璃着色器（AGSL）提炼自 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Apache-2.0）的 SDF 折射 + 高光实现。

## 要求

- **Android 13+（API 33）** —— AGSL `RuntimeShader` 的硬性要求
- 已 root 并安装 **LSPosed**（经典 Xposed API 82）

## 安装

1. 编译出 APK（见下）并安装
2. 打开 **LSPosed 管理器 → 模块**，启用「抖音液态玻璃」
3. 作用域勾选 **抖音**（`com.ss.android.ugc.aweme`）
4. 强制停止抖音后重新打开，底栏会被替换为悬浮的液态玻璃条

打开模块 App 会显示状态页，激活成功时提示 ✅。

## 工作原理

```
LiquidGlassHook (IXposedHookLoadPackage)
  └─ 命中 com.ss.android.ugc.aweme 时，hook Application.onCreate
     └─ 注册 ActivityLifecycleCallbacks
        ├─ onResumed → OverlayController.install()
        │    ├─ 创建 LiquidGlassOverlayView（独立 WindowManager 子窗口，
        │    │   FLAG_NOT_TOUCHABLE 让点击穿透到原生底栏）
        │    ├─ NativeBottomBarLocator 定位原生底栏并对齐
        │    └─ DynamicBitmapBackdrop 用 PixelCopy 每帧抓取背景
        └─ onPaused → 移除

渲染管线（LiquidGlassOverlayView.onDraw）：
  backdrop bitmap → RenderNode
    → RenderEffect: 高斯模糊 → 折射着色器(REFRACTION)
    → 圆角裁剪
    → 磨砂着色 tint
    → 顶部高光(HIGHLIGHT)
```

## 需要你按抖音版本适配的部分

抖音的视图层级是混淆且频繁变动的，没有稳定的 resource id。

**`nativebar/NativeBottomBarLocator.kt`** 用一组启发式规则（贴底、高度 40–72dp、宽度占屏 ≥75%、3–6 个子 View、id/类名关键词）给候选底栏打分。若某个抖音版本没对齐：

```bash
adb logcat -s DouyinLiquidGlass
```

看它打印的候选列表，把当前版本真实的 resource-entry-name 或类名片段填进 `KNOWN_ID_HINTS` / `KNOWN_CLASS_HINTS`，即可锁定精确匹配。

## 参数调节

全部可调项集中在 **`settings/ModuleSettings.kt`**：玻璃高度、圆角、模糊半径、折射高度/强度、色散开关、深度效果、着色、高光透明度、左右/底部边距、是否隐藏原生底栏。改完重新编译即可。

## 编译

本项目走 **GitHub Actions**（`.github/workflows/android.yml`）自动出包：

- push 到 `main`/`master` 或手动 `workflow_dispatch` → 产出 debug + release APK（artifact `DouyinLiquidGlass-apk`）
- push `v*` tag → 自动创建 Release 并附上 APK

本地有 Android SDK 也可以：

```bash
./gradlew :app:assembleRelease
```

## 声明

仅供学习研究。Xposed API jar (`app/libs/api-82.jar`) 为运行时由框架提供的经典 Xposed API。

## License

MIT
