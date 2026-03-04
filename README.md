# ChunkModify - Minecraft 区块修改检测系统

> 通过镜像维度技术，对比玩家修改与原始地形的差异

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.6-green)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-orange)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-CC0--1.0-blue)](LICENSE)

## ✨ 特性

- 🌍 **镜像维度**：创建与主世界完全相同的原始维度
- 🔍 **区块对比**：精确检测每个方块的修改情况
- 📊 **详细统计**：修改率、差异对、排行榜
- 💾 **零磁盘占用**：镜像维度不保存数据，重启即重置
- 🎯 **完全一致**：树木、矿石等自然特征位置完全相同
- ⚡ **异步处理**：不阻塞服务器主线程

## 🚀 快速开始

### 安装
1. 下载 [Fabric Loader](https://fabricmc.net/use/) 和 [Fabric API](https://modrinth.com/mod/fabric-api)
2. 将 `chunk-modify-1.0.0.jar` 放入 `mods` 文件夹
3. 启动服务器

### 基本使用
```mcfunction
# 分析当前区块
/chunkmodify analyze

# 分析指定位置的区块
/chunkmodify analyze 100 200

# 查看修改率排行
/chunkmodify report

# 清空缓存
/chunkmodify clear
```

### 访问镜像维度
```mcfunction
/execute in chunk-modify:mirror_overworld run tp @s ~ ~ ~
```

## 📖 文档

- **[快速入门指南](QUICK_START.md)** - 5分钟上手
- **[项目总结](PROJECT_SUMMARY.md)** - 完整功能介绍
- **[保存阻止机制](DIMENSION_SAVE_BLOCKING.md)** - 技术深度解析
- **[特征生成修复](MIRROR_DIMENSION_FEATURE_FIX.md)** - 随机数同步原理

## 🎯 使用场景

### 反作弊检测
检测玩家是否偷挖矿石或破坏地形
```
分析结果：stone → air : 456
说明：挖掘了大量石头，可能在挖矿
```

### 建筑评估
评估建筑对自然地形的影响
```
修改率: 12.34%
说明：建筑较大程度改变了地形
```

### 区域保护验证
确认保护区是否被破坏
```
修改率: 0.2%
说明：基本保持原始状态
```

### 地图还原
对比当前地图与原始生成，用于还原
```
Top 差异对显示需要还原的方块类型
```

## 🛠️ 技术架构

### 核心组件
```
镜像维度系统
    ↓
特征生成同步（ChunkRandomMixin）
    ↓
区块加载（ServerWorld）
    ↓
保存阻止（7层Mixin拦截）
    ↓
内存驻留（零磁盘写入）
```

### 对比流程
```
获取镜像维度区块 → 获取主世界区块 → 逐方块对比 → 统计差异 → 生成报告
```

## 📊 分析结果示例

```
=== Chunk(5, 10) 分析结果 ===
  修改率: 12.34%  (1234 / 10000 方块)
  
  Top 差异对 (生成方块 → 实际方块 : 次数):
    stone → air : 456          # 挖掉的石头
    dirt → grass_block : 234   # 自然生长的草
    air → oak_log : 123        # 放置的原木
    stone → iron_ore : 89      # 不可能！说明作弊
    grass_block → dirt : 67    # 踩踏导致
    ...
```

## ⚙️ 配置

### 镜像维度定义
`src/main/resources/data/chunk-modify/dimension/mirror_overworld.json`
```json
{
  "type": "minecraft:overworld",
  "generator": {
    "type": "minecraft:noise",
    "settings": "minecraft:overworld",
    "biome_source": {
      "type": "minecraft:multi_noise",
      "preset": "minecraft:overworld"
    }
  }
}
```

### Mixin 配置
`src/main/resources/chunk-modify.mixins.json`
```json
{
  "mixins": [
    "cancelSave.ChunkPosKeyedStorageMixin",
    "cancelSave.EntityChunkDataAccessMixin",
    "cancelSave.ServerChunkManagerMixin",
    "cancelSave.ServerChunkLoadingManagerMixin",
    "cancelSave.ServerWorldMixin",
    "cancelSave.VersionedChunkStorageMixin",
    "cancelSave.WorldMixin",
    "ChunkRandomMixin"
  ]
}
```

## 🔧 开发

### 编译
```bash
./gradlew build
```

### 输出
```
build/libs/chunk-modify-1.0.0.jar
```

### 项目结构
```
src/main/java/com/zhddsj/chunkcheck/
├── ChunkModify.java              # 主入口
├── command/                      # 命令系统
├── comparison/                   # 对比逻辑
├── storage/                      # 缓存管理
├── world/                        # 世界相关
└── mixin/                        # Mixin 修改
    ├── cancelSave/               # 保存阻止
    └── ChunkRandomMixin.java     # 随机数同步
```

## 🐛 已知限制

- ❌ 仅支持主世界类型维度（下界、末地需要额外配置）
- ❌ 不对比实体（只对比方块状态）
- ❌ 不对比TileEntity数据（箱子内容、告示牌文本等）
- ❌ 需要先访问镜像维度加载区块

## 📝 更新日志

### v1.0.0 (2026-03-01)
- ✅ 镜像维度系统
- ✅ 7层保存阻止机制
- ✅ 特征生成一致性修复
- ✅ 区块对比和分析命令
- ✅ 结果缓存和排行系统

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

### 开发环境要求
- Java 21
- Gradle 9.3+
- IntelliJ IDEA / VS Code

### 贡献指南
1. Fork 项目
2. 创建特性分支
3. 提交变更
4. 推送到分支
5. 创建 Pull Request

## 📄 许可证

本项目采用 CC0-1.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- [Fabric](https://fabricmc.net/) - 模组加载器
- [Minecraft](https://www.minecraft.net/) - 游戏本体
- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin) - Mixin 框架

## 📧 联系方式

- GitHub Issues: [报告问题](https://github.com/yourusername/chunk-modify/issues)
- Discord: [加入服务器](#)

---

**⚠️ 注意**：本 Mod 仅用于服务器管理和反作弊检测，请勿用于恶意目的。

**💡 提示**：镜像维度的数据不会保存，服务器重启后会重新生成，确保始终与主世界的原始生成保持一致。

