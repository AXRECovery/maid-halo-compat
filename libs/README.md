# libs/

构建用的第三方**编译期**依赖（Gradle `compileOnly`，不会打进产物 jar）。

**本目录下的 jar 不随仓库分发**（版权原因，已在 `.gitignore` 中排除），克隆后需自行准备以下两个文件放在这里：

| 文件名 | 来源 |
| --- | --- |
| `touhoulittlemaid-1.5.3.jar` | 车万女仆（Touhou Little Maid）1.5.3 · Forge 1.20.1，CurseForge / Modrinth |
| `curios-forge-5.14.1.jar` | Curios 5.14.1+1.20.1，CurseForge / Modrinth |

文件名需与上表一致（`build.gradle` 按路径引用）。缺少这两个 jar 时 `./gradlew build` 会在编译阶段报找不到 TLM / Curios 的类，属于预期行为。
