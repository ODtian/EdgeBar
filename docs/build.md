# 构建与原生职责探针

## 环境

- JDK 17，`JAVA_HOME` 指向 JDK 根目录，或将 `javac` 放入 `PATH`。
- Python 3.9 或更新版本。
- Android SDK Platform 36：`platforms/android-36/android.jar`。
- Android SDK Build Tools 36.0.0。

构建复用现有 `javac → D8 → aapt2 → zipalign → apksigner` 链路。libxposed API 102 的依赖位于 `third_party/libxposed/`。

## 构建

```sh
python tools/build.py --sdk-root /path/to/android-sdk
```

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
python tools/build.py --sdk-root 'C:\Android\Sdk'
```

构建产物为 `dist/EdgeBar-v0.5.14.apk`，编译中间文件位于 `build/`。

| 参数 | 含义 |
| --- | --- |
| `--sdk-root` | Android SDK 根目录；也可使用 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` |
| `--build-tools` | Build Tools 目录；也可使用 `EDGEBAR_BUILD_TOOLS` |
| `--android-jar` | Platform 36 的 `android.jar`；也可使用 `EDGEBAR_ANDROID_JAR` |
| `--java-home` | JDK 根目录；也可使用 `JAVA_HOME` |
| `--keystore` | 签名文件，默认 `.local/signing.jks` |
| `--key-alias` | 签名别名，默认 `edgebar` |

## 签名

首次开发构建会在 `.local/signing.jks` 创建本地开发密钥。`EDGEBAR_STORE_PASSWORD` 和 `EDGEBAR_KEY_PASSWORD` 用于传入密码，默认开发密码为 `android`。

使用已有密钥：

```sh
python tools/build.py --sdk-root /path/to/android-sdk --keystore /private/path/signing.jks --key-alias edgebar
```

覆盖安装使用相同的应用签名。维护者保存发布密钥，开发构建使用各自的本地密钥。`.local/`、签名文件和构建产物由 `.gitignore` 管理。

## 原生职责探针

`NativeRoleProbe` 使用模块中同一套 `NativeDex` 与 `NativeRoles`，从目标 Edge APK 解析控件类型、资源、签名和调用关系。

在仓库根目录编译：

```sh
mkdir -p build/probe
javac -encoding UTF-8 -d build/probe module/src/dev/edgestyle/module/NativeDex.java module/src/dev/edgestyle/module/NativeRoles.java tools/NativeRoleProbe.java
java -Xmx2g -cp build/probe dev.edgestyle.module.NativeRoleProbe /path/to/edge-base.apk /path/to/split_chrome.apk
```

Windows 可先通过 `New-Item -ItemType Directory -Force build/probe` 创建目录，再运行相同的 `javac` 与 `java` 命令。

APK 路径来自目标 Edge 安装包；分包安装时同时提供主 APK 与包含 Chrome 类的 split APK。探针输出各原生职责解析到的方法、字段和耗时。实际类加载器中的反射绑定由模块运行时完成。
