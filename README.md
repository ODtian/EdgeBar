# EdgeBar

将 Microsoft Edge Android 的原生地址栏、标签计数按钮和菜单组合成单行工具栏的 libxposed 模块。

[下载最新 APK](https://github.com/ODtian/EdgeBar/releases/latest) · [版本记录](CHANGELOG.md) · [实现与调用链](docs/architecture.md) · [构建方法](docs/build.md)

## 功能

- 网页采用单行地址栏与工具按钮，输入时使用原生地址输入和建议列表。
- 支持地址左对齐／居中，以及标签、后退、前进、新标签页按钮的排列。
- 新标签页沿用 Edge 原生页面、搜索框和页面设置，工具按钮支持顶部或搜索框旁的位置。
- 沿用原生按钮点击、标签计数、主题和配置变化生命周期。
- 在 Edge 的「设置 → 扩展安装」中打开内置扩展目录，由 Edge 完成安装。页面也提供 Edge 加载项和 Chrome 应用商店的浏览入口。

## 安装

1. 从 [Releases](https://github.com/ODtian/EdgeBar/releases/latest) 下载并安装 APK。
2. 在支持 libxposed API 102 的 LSPosed／Vector 中启用 EdgeBar，作用域选择 `com.microsoft.emmx`。
3. 重启 Edge。
4. 打开“EdgeBar 设置”，调整排列、地址对齐和新标签页按钮位置，点击“保存并在 Edge 应用”。

覆盖安装沿用已保存的模块设置。

## 当前版本

| 项目 | 已验证配置 |
| --- | --- |
| EdgeBar | 0.5.21 |
| Edge | 158.0.4370.33，arm64-v8a |
| 框架接口 | libxposed API 102 |
| 实机 | PJF110，Android 16 |
| 模块最低系统版本 | Android 10 / API 29 |
| 模块包名 | `dev.edgestyle.module` |

0.5.21 增大单行地址栏内部左侧留白，使用 Edge 原生起始内边距资源及原生图标槽宽度。

## 实机截图

<img src="docs/images/no-lock.png" width="280" alt="图标隐藏后的原生边缘留白"> <img src="docs/images/with-lock.png" width="280" alt="原生安全图标和单行工具栏">

## 项目结构

```text
module/                  Android 清单、Java 源码、资源和 Xposed 元数据
tools/build.py           javac → D8 → aapt2 → zipalign → apksigner 构建链
tools/NativeRoleProbe.java  已安装 Edge APK 的原生职责定位探针
third_party/libxposed/   API 102 编译与运行时依赖及许可
docs/                   构建说明、调用链和实机截图
CHANGELOG.md            版本记录
```

开发环境使用 JDK 17、Python 3、Android SDK Platform 36 与 Build Tools 36.0.0。运行 `python tools/build.py --sdk-root <Android-SDK>`，生成 `dist/EdgeBar-v0.5.21.apk`。参数和签名配置见[构建方法](docs/build.md)。

第三方组件的来源和许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
