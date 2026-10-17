# 跳跃冒险 · JumpQuest

纯原生 Android（Kotlin）2D 横版跳跃闯关小游戏。渲染用 `Canvas + SurfaceView`，**没有游戏引擎、没有 WebView、没有第三方素材**，全部美术都是代码绘制的几何图形。

- 包名：`com.lyq.jumpquest`
- 版本：`1.0.0`（versionCode 1）
- 最低支持：Android 7.0（minSdk 24），compileSdk / targetSdk 34
- 屏幕：强制横屏 + 沉浸式全屏

## 玩法

| 操作 | 说明 |
| --- | --- |
| 左下 `←` `→` | 左右移动（多指可同时按住方向 + 跳） |
| 右下 `跳` | 跳跃；长按跳更高，带土狼时间与跳跃缓冲 |
| 右上按钮 | 随时重开本局 |

- 踩巡逻怪消灭它，连续踩踏得分翻倍（100 / 200 / 300…）
- 被怪撞到：小个子直接失去一条命，大个子变小并短暂无敌
- 顶 `?` 块出金币；顶带道具的块掉出「能量果」，吃到变大
- 金币 +100，掉坑扣一条命，3 条命用完进入 GAME OVER
- 跑到终点旗杆过关，用时越短时间奖励越高
- 关卡 224×15 格，含地面 / 砖块 / 问号块 / 管道 / 上下台阶 / 三个深坑 / 起终点

## 目录结构

```
android-jump-quest/
├── build.gradle                     # 项目级：AGP 8.2.2 + Kotlin 2.0.21
├── settings.gradle                  # 仓库配置 + include ':app'
├── gradle.properties                # JVM 内存 / AndroidX / 编码
├── gradle/wrapper/gradle-wrapper.properties   # Gradle 8.2.1 分发地址
├── .github/workflows/android.yml    # 云编译：debug + release + tag 发 Release
└── app/
    ├── build.gradle                 # compileSdk 34 / minSdk 24 / 签名 / 打包配置
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml      # 横屏、全屏主题、单 Activity
        ├── res/values/strings.xml
        ├── res/values/styles.xml
        ├── res/mipmap/ic_launcher.xml   # 矢量图标（代码画的图形）
        └── java/com/lyq/jumpquest/
            ├── MainActivity.kt      # 横屏沉浸式全屏 + 挂载 GameView
            ├── GameView.kt          # SurfaceView + 渲染线程 + 多指虚拟按键
            └── game/
                ├── Constants.kt     # TILE / STEP / Physics 手感参数 / GameState
                ├── Entity.kt        # 实体基类（AABB、中心点、重叠判定）
                ├── Player.kt        # 玩家：大小形态、朝向、无敌、挤压动画
                ├── Enemy.kt         # 巡逻怪 Walker（被踩扁动画）
                ├── Item.kt          # PowerFruit 能量果 & 粒子/飘字
                ├── Level.kt         # 图块网格、碰撞查询、出生点/旗杆
                ├── Levels.kt        # LevelBuilder + 第 1 关关卡数据
                ├── Camera.kt        # 跟随 + 屏幕震动
                ├── Controls.kt      # dp 自适应虚拟按键布局 + 圆形命中
                ├── InputState.kt    # UI 线程→渲染线程的输入快照（AtomicBoolean 跳跃边沿）
                ├── GameWorld.kt     # 状态机、物理、碰撞、踩怪、方块、计分
                └── Renderer.kt      # 全部绘制：视差背景、图块、角色、粒子、HUD、结算
```

## 技术要点

| 模块 | 说明 |
| --- | --- |
| 游戏循环 | `SurfaceView` + 独立线程，120Hz 固定步长（`STEP = 1/120`）累加器，最多补 8 步，避免掉帧穿墙 |
| 分辨率适配 | 世界单位 1 格 = 32；`scale = 屏幕高 / 关卡世界高`，相机横向跟随，任意比例自动铺满 |
| 物理 | 重力 2400、最大下落 1500、走速 265、跳跃初速 -845（约 4.6 格高）、落地摩擦 3000 |
| 手感 | 土狼时间 0.10s、跳跃缓冲 0.13s、松手截断 -300、着地挤压动画 |
| 碰撞 | X/Y 分轴移动 + 图块 AABB 扫描，踩怪判定用「上一帧脚底」防误判 |
| 输入 | 每个 `pointerId` 映射一个虚拟键，支持左手方向 + 右手跳跃；跳跃按下用 `AtomicBoolean` 保证边沿不被吃 |
| 绘制 | 无图片资源，全部 `Canvas` 几何图形：渐变天空、云、远山、草皮地面、砖块纹理、金属管道、渐变金币 |

## 本地构建

需要 JDK 17、Android SDK（platform 34、build-tools 34.0.0）。

```bash
# 仓库只提交了 wrapper 配置，第一次先在本地生成 wrapper（或直接用系统 gradle）
gradle wrapper --gradle-version 8.2.1

echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 云编译（GitHub Actions）

工作流：`.github/workflows/android.yml`

- 触发：push 到 `main`、`v*` tag、PR、手动 `workflow_dispatch`
- 环境：`ubuntu-latest` + JDK 17 + runner 预装 Android SDK（不依赖已下线的 `setup-android`）+ Gradle 8.2.1
- 步骤：安装 `platforms;android-34` / `build-tools;34.0.0` → 还原签名 keystore → `assembleDebug assembleRelease` → 上传两个 Artifact
- 打 `v*` tag 时用 `softprops/action-gh-release` 自动建 Release，并附上 release + debug APK

正式签名由仓库 Secrets 提供（已配置）：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | keystore 的 base64 |
| `STORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | `jumpquest` |
| `KEY_PASSWORD` | key 密码 |

`app/build.gradle` 读取 `KEYSTORE_PATH / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD` 四个环境变量，存在就用正式签名，否则回退 debug 签名（APK 依旧可安装）。

### 下载 APK

- 打开 Actions → 对应构建 → 页面底部 Artifacts：`jumpquest-release-apk` / `jumpquest-debug-apk`
- 或者直接到 Releases 页下载 `app-release.apk`

命令行下载：

```bash
TOKEN=你的_PAT
# 列 artifact
curl -s -H "Authorization: token $TOKEN" \
  https://api.github.com/repos/lyqxml/android-jump-quest/actions/artifacts | \
  python3 -c "import sys,json;[print(a['id'],a['name']) for a in json.load(sys.stdin)['artifacts']]"
# 下载（-L 跟随重定向，别自己加 Authorization 到 blob 域名）
curl -sL -H "Authorization: token $TOKEN" -o apk.zip \
  https://api.github.com/repos/lyqxml/android-jump-quest/actions/artifacts/<ARTIFACT_ID>/zip
```

## 常见坑

| 现象 | 原因 / 处理 |
| --- | --- |
| `Failed to find package 'tools'` | 新版 runner 的 cmdline-tools 16 已移除 `tools` 包，`android-actions/setup-android@v3` 会因此挂掉 → 本仓库改为直接用预装 SDK + `sdkmanager --install` |
| `Platform declaration clash ... setBig` | Kotlin 属性 `big` 自动生成 `setBig` 与手写 `setBig` 撞签名 → 手写方法改名 `changeSize` |
| `SDK location not found` | 本地缺 `local.properties`，或 CI 没设 `ANDROID_HOME` |
| Gradle 下载慢 | 可在 `gradle.properties` 加代理，或用 `gradle/actions/setup-gradle` 缓存（已启用） |
| 推送含 workflow 的文件被拒 403 | PAT 缺 `workflow` scope |
| Release 附件空 | tag 不是 `v*` 形式，或 `permissions: contents: write` 缺失 |
| APK 装不上（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`） | 换过签名，先卸载旧版 |
| git 协议被墙、只有 api.github.com 可达 | 用 Git Data API 上传 blob/tree/commit 再更新 ref（见 `push_via_api.py` 思路） |

## 版权

角色、贴图、音乐全部自制几何图形与程序化绘制，未使用任何任天堂素材。
