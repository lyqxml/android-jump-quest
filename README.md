# 跳跃冒险 · JumpQuest

纯原生 Android（Kotlin）2D 横版跳跃闯关小游戏。绘制用 `Canvas + SurfaceView`，没有任何游戏引擎、没有 WebView、没有第三方素材，所有美术都是代码画的几何图形。

## 玩法

- 左下 `←` `→` 移动，右下 `跳` 跳跃（支持土狼时间 + 跳跃缓冲，长按跳更高）
- 踩怪消灭巡逻怪，碰到会掉血：小个子直接死，大个子变小并短暂无敌
- 顶问号块出金币，顶带道具的块会掉出「能量果」，吃到变大
- 金币加分，掉进坑里扣一条命
- 跑到终点旗杆过关，时间越快奖励越高
- 右上角刷新按钮随时重开

## 技术要点

| 模块 | 说明 |
| --- | --- |
| `MainActivity` | 横屏 + 沉浸式全屏，只挂一个 GameView |
| `GameView` | SurfaceView + 独立渲染线程，120Hz 固定步长物理，多指触摸虚拟按键 |
| `GameWorld` | 状态机（游玩 / 死亡 / 过关 / 结束）、物理、AABB 图块碰撞、计分 |
| `Level` / `Levels` | 图块关卡数据 + 用代码拼装的第 1 关（224×15 格） |
| `Player` / `Walker` / `PowerFruit` | 玩家、巡逻怪、能量果 |
| `Renderer` | 全部绘制：天空视差、图块、角色、粒子、HUD、虚拟按键、结算层 |

世界单位：1 格 = 32，屏幕缩放 = `屏幕高 / 关卡世界高`，所以任何分辨率都自动适配。

## 本地构建

需要 JDK 17、Android SDK（compileSdk 34、build-tools 34.0.0）。

```bash
# 生成 wrapper（仓库里没有提交 jar）
gradle wrapper --gradle-version 8.2.1

./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` 里写上 `sdk.dir=/path/to/Android/Sdk`，或者直接用环境变量 `ANDROID_HOME`。

## 云编译

推送到 `main` 或在 Actions 页面手动 `Run workflow`，`.github/workflows/android.yml` 会自动：

1. 装 JDK 17 + Android SDK + Gradle 8.2.1
2. `assembleDebug assembleRelease`
3. 上传 `jumpquest-debug-apk` / `jumpquest-release-apk` 两个 Artifact
4. 打 `v*` tag 时自动创建 Release 并附上 release APK

release 默认用 debug 签名（保证随包可安装）；如果要正式签名，在仓库 Secrets 里配置
`KEYSTORE_BASE64` 之外的四个值并在构建前还原 keystore：

```bash
- name: Restore keystore
  if: ${{ env.KEYSTORE_PATH != '' }}
  run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > release.keystore
  env:
    KEYSTORE_PATH: release.keystore
```

`app/build.gradle` 会读取 `KEYSTORE_PATH / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD`
这四个环境变量，存在就用正式签名，不存在退回 debug 签名。

## 版权

角色、贴图、音乐全部自制几何图形，没有使用任何任天堂素材。
