# LunarClock

轻量的 Android 农历时钟桌面微件。它解决的是系统时钟微件不显示中国农历的问题:不做完整日历 App,不常驻后台,不联网,不显示 Launcher 图标。

## 功能

- 桌面 App Widget,默认 4x1,可横向/纵向调整大小。
- 大号 `HH:mm` 时间显示,分钟刷新交给系统 `TextClock`。
- 高度足够时显示:
  - 公历日期:`2026-01-01`
  - 农历信息:`乙巳(蛇)-冬月十三`
  - 星期:`周四`
- 二十四节气提前 3 天提示:临近节气时星期行追加显示「今天 / 明天 / 后天 + 节气名」,例如 `周四 · 后天立春`。
- 农历与节气全部用天文算法实时计算(定朔 + 中气置闰、太阳黄经),覆盖 **1900–2200**,不依赖任何农历数据表。
- 点击时间打开系统时钟/闹钟。
- 点击日期/农历区域打开系统日历。
- 无网络权限。
- 无后台服务、无轮询、无 WorkManager。
- 无 Launcher 图标,安装后从系统“小部件”入口添加。

## 截图

小部件选择器预览:

<img src="docs/images/widget-picker-preview.webp" alt="小部件选择器预览" width="480">

桌面实际效果:

<img src="docs/images/home-screen-widget.webp" alt="桌面实际效果" width="480">

## 环境

- Android Studio(需支持 AGP 9 的版本)
- Android SDK 36
- JDK 21
- Gradle 9.6.1
- AGP 9.2.1
- Kotlin(由 AGP 9 内置提供,无需独立 Kotlin 插件)
- 源码语言:Kotlin,单文件实现

如果系统默认 JDK 不是 21,请在构建时指定:

```bash
JAVA_HOME=/path/to/jdk-21 ./gradlew :app:assembleDebug
```

本项目的 Gradle wrapper 配置使用本地 distribution zip:

```properties
distributionUrl=./gradle-9.6.1-bin.zip
```

`gradle-9.6.1-bin.zip` 体积较大,不提交到仓库。构建前请下载或复制到:

```text
gradle/wrapper/gradle-9.6.1-bin.zip
```

可以从 Gradle 官方下载:

```bash
curl -L -o gradle/wrapper/gradle-9.6.1-bin.zip \
  https://services.gradle.org/distributions/gradle-9.6.1-bin.zip
```

## 构建

Debug 包:

```bash
./gradlew :app:assembleDebug
```

Release 包:

```bash
./gradlew :app:assembleRelease
```

如果仓库中没有本地 release keystore,`assembleRelease` 会生成未签名 release APK。个人安装测试可以直接使用 debug 包,正式发布需要配置自己的签名。

## 本地 release 签名

创建本地 keystore:

```bash
mkdir -p keystore
keytool -genkeypair \
  -keystore keystore/lunarclock-release.jks \
  -alias lunarclock \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

创建 `keystore.properties`:

```properties
storeFile=keystore/lunarclock-release.jks
storePassword=your_store_password
keyAlias=lunarclock
keyPassword=your_key_password
```

然后重新构建:

```bash
./gradlew :app:assembleRelease
```

`keystore.properties` 和 `keystore/` 已在 `.gitignore` 中排除,不要提交到仓库。

## 安装和使用

安装到已连接的设备:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或安装你自己签名后的 release 包:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

安装后应用抽屉里不会出现图标。添加方式:

1. 回到桌面。
2. 长按空白区域。
3. 进入“小部件 / Widgets”。
4. 搜索或找到“农历时钟”。
5. 拖到桌面。

卸载:

```bash
adb uninstall io.github.liuanxin.lunarclock
```

也可以在手机 `设置 -> 应用 -> 查看所有应用` 中搜索“农历时钟”卸载。

## 设计原则

- 尽量接近系统时钟微件的观感。
- 时间优先,农历与节气信息作为补充。
- 矮高度时只显示大时间,避免文字被裁剪。
- 高度足够时显示完整日期、农历、星期,临近节气时在星期行追加提示。
- 农历与节气用天文算法实时计算,不内置数据表,年份范围不受表限制。
- 所有刷新都依赖系统 App Widget / 系统广播 / `TextClock`;跨天刷新用一次性 `AlarmManager` 兜底,不做后台常驻或轮询。
