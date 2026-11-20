# 女仆光环兼容 · Maid Halo Compat

让 **车万女仆（Touhou Little Maid）** 戴上光环饰品时，像玩家一样正常显示。

> Minecraft **1.20.1** · Forge **47.x** · **仅客户端** · 当前版本 **0.3.0**

女仆在原版里用的是完全独立的渲染管线，光环类饰品虽然能装备，但画面上不会出现。
本模组通过车万女仆官方的扩展接口，把光环按**与玩家一致的绘制方式**补到女仆身上 ——
仅客户端运行，不影响原模组的其它功能。

---

## 支持的光环

| 光环 | 来源模组 |
| --- | --- |
| 晋升之环 / 破碎之环 / 终末之环 | [诡厄巫法：启示录] GoetyRevelation |
| 不洁圣冠 | [诡厄巫法] goety |
| 泡影之领域 | [梦幻终焉] fantasy_ending |
| BA光环 | Blue-Archive-Halos |

两条女仆渲染路径都已覆盖：**传统 Bedrock 模型** 与 **GeckoLib / YSM 模型**。
酒狐（winefox）系列模型会自动额外抬高 0.5 格，其余模型可用配置手动微调。

## 适配模组与最低已验证版本

| 模组 | 最低已验证版本 | 所含支持光环 | 其它 |
| --- | --- | --- | --- |
| 车万女仆（Touhou Little Maid） | **1.5.2** | - | **唯一必装前置** |
| 诡厄巫法（Goety） | **2.5.49.3** | 不洁圣冠 | - |
| 诡厄巫法：启示录（Goety: Revelation） | **2.3.1** | 晋升之环 / 破碎之环 / 终末之环 | - |
| 梦幻终焉（FantasyEnding） | **2.7.20** | 泡影之领域 | - |
| BA光环包（Blue-Archive-Halos） | **1.0.20.0** | BA光环（需要 GeckoLib） | - |
| Curios | 5.11.0 | - | 饰品槽 |

> 以上仅为**推荐的最低已验证版本**。只要不是大版本更新，这些模组的近期版本理论上都可以直接使用；
> 若遇到不兼容，优先尝试升级到对应模组的最新版本再反馈。

**车万女仆为必装前置**，其余模组全部为可选项，会在启动时自动检测。

## 安装

1. 准备好你的 Forge 1.20.1 客户端；
2. 把release的 jar 拖入启动器安装或手动放进 `mods/`（按需装载支持的光环来源模组）；
3. 启动minecraft，享受你的游戏。

## 使用

- **打开配置页**：`Mods → Maid Halo Compat → Config`；
  或在「选项 → 控制 → 按键绑定 → 女仆光环兼容」里给「打开女仆光环配置界面」绑定快捷打开按键
  （**默认不指定按键**）。
- **配置页里可以**：
  - 一键开关各类光环；
  - 调整光环整体缩放、自转速度、各类光环的位置偏移；
  - 按模型单独调整终末之环高度；
  - 点「完成」则为保存，立刻生效。

## 配置

配置文件：`config/maid_halo_compat-client.toml`（也可用上面的配置页修改）。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `general.enableAscensionHalo` | `true` | 晋升之环 |
| `general.enableBrokenHalo` | `true` | 破碎之环 |
| `general.enableEndHalo` | `true` | 终末之环 |
| `general.enableUnholyHatHalo` | `true` | 不洁圣冠 |
| `general.enableDomainOfFade` | `true` | 泡影之领域 |
| `general.enableBlueArchiveHalo` | `true` | BA光环 |
| `general.endHaloLiftDefault` | `0.0` | 终末之环整体上抬量（方块） |
| `general.endHaloLiftByModel` | `[]` | 按模型覆盖高度，如 `["geckolib:winefox=0.5"]` |
| `general.ascensionSpinDegreesPerSecond` | `18.0` | 晋升之环自转速度 |
| `general.haloScale` | `1.0` | 光环整体缩放 |
| `general.unholyHatRenderFullModel` | `true` | `false` 时只绘制光环、不画帽子 |
| `offsets.*` | `0.0` | 各类光环的位置微调（方块） |

## 兼容性说明

- **纯客户端**：不需要服务端安装；在多人服务器上只在客户端生效。
- 不修改车万女仆 / 诡厄巫法 / 诡厄巫法:启示录 / 梦幻终焉 / BA光环包的任何代码或资源，
  所有光环几何与贴图都直接调用自原模组。
- 兼容使用 YSM 模型的女仆
  `config/yes_steve_model/custom` 里的模型，并支持按模型单独调整高度。

## 猜你想问 (FAQ)

**Q: 在整合包里能用吗？**
A: 可以，经测试落幕曲1.6.8可正常运行。

## 构建（开发者）

```bash
./gradlew build        # 产物：build/libs/maid-halo-compat-0.3.0.jar
./gradlew runClient    # 开发客户端
```

- 需要 **JDK 17**（Gradle toolchain 会自动查找；找不到时可用
  `./gradlew build -Porg.gradle.java.installations.paths="C:/path/to/jdk-17"` 显式指定）；
- **无需手动放置任何依赖 jar**：车万女仆（Modrinth Maven）与 Curios（theillusivec4 Maven）
  作为 `compileOnly` 依赖在首次构建时自动下载，不会打进产物；
- 若本机需要代理才能访问 Maven，可加
  `-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897`（按需替换端口）。

## 许可

MIT License · Copyright (c) 2026 **AXRECovery**
