# ChunkModify - 完整功能总结

## 项目概述
ChunkModify 是一个 Fabric Mod，用于在 Minecraft 1.21.6 中创建镜像维度，实现区块对比和修改检测功能。

## 核心功能

### 1. 镜像维度系统
- **位置**：`src/main/resources/data/chunk-modify/dimension/mirror_overworld.json`
- **特性**：
  - 与主世界使用完全相同的世界生成配置
  - 维度名称包含 `mirror_` 前缀
  - 数据不会保存到磁盘（只读维度）

### 2. 维度保存阻止机制
通过 7 个 Mixin 完全阻止镜像维度的数据保存：

#### 顶层拦截
- **WorldMixin**：设置 `savingDisabled = true`
- **ServerWorldMixin**：阻止世界保存和持久化状态保存
- **ServerChunkManagerMixin**：阻止区块管理器保存
- **ServerChunkLoadingManagerMixin**：阻止区块加载管理器保存

#### 底层拦截（关键）
- **VersionedChunkStorageMixin**：阻止区块 NBT 数据写入
- **EntityChunkDataAccessMixin**：阻止实体数据写入
- **ChunkPosKeyedStorageMixin**：**最底层拦截**，阻止所有数据写入磁盘

**详细文档**：`DIMENSION_SAVE_BLOCKING.md`

### 3. 特征生成一致性修复
通过 ChunkRandomMixin 确保镜像维度与主世界生成完全相同的自然特征（树木、矿石等）：

#### 问题根源
- Minecraft 特征生成使用随机数
- 随机数种子基于 `world.getSeed()`
- 不同维度的种子可能不同，导致特征位置/形状不同

#### 解决方案
- **ChunkRandomMixin**：拦截 `ChunkGenerator.generateFeatures()` 中的 `world.getSeed()` 调用
- 强制镜像维度使用主世界的种子
- 保证相同坐标的区块生成完全相同的特征

**详细文档**：`MIRROR_DIMENSION_FEATURE_FIX.md`

### 4. 区块对比系统

#### OriginalChunkGenerator
- **位置**：`src/main/java/com/zhddsj/chunkcheck/comparison/OriginalChunkGenerator.java`
- **功能**：在内存中重新生成"原始"区块
- **生成步骤**：
  - STRUCTURE_STARTS → STRUCTURE_REFERENCES → BIOMES
  - NOISE → SURFACE → CARVERS → FEATURES
- **特点**：不写入磁盘，纯内存操作

#### ChunkComparator
- **位置**：`src/main/java/com/zhddsj/chunkcheck/comparison/ChunkComparator.java`
- **功能**：对比镜像维度区块与主世界区块
- **输出**：
  - 修改率百分比
  - 修改的方块总数
  - 方块差异对统计（生成方块 → 实际方块）

### 5. 命令系统

#### /chunkmodify analyze
分析当前或指定区块的修改情况：
```
/chunkmodify analyze              # 分析玩家所在区块
/chunkmodify analyze <x> <z>      # 分析指定方块坐标的区块
```

**输出示例**：
```
=== Chunk(5, 10) 分析结果 ===
  修改率: 12.34%  (1234 / 10000 方块)
  Top 差异对 (生成方块 → 实际方块 : 次数):
    stone → air : 456
    dirt → grass_block : 234
    air → oak_log : 123
    ...
```

#### /chunkmodify report [count]
列出修改率最高的区块：
```
/chunkmodify report        # 显示前5个
/chunkmodify report 10     # 显示前10个
```

**输出示例**：
```
=== ChunkModify 修改率排行 (Top 5) ===
  #1 Chunk(5,10): 12.3% (1234/10000 方块修改)
  #2 Chunk(3,8): 8.9% (890/10000 方块修改)
  ...
```

#### /chunkmodify clear
清空缓存的分析结果：
```
/chunkmodify clear
```

## 项目结构

```
src/main/java/com/zhddsj/chunkcheck/
├── ChunkModify.java                    # 主入口
├── command/
│   └── ChunkCompareCommand.java        # 命令注册和处理
├── comparison/
│   ├── ChunkComparator.java            # 区块对比逻辑
│   ├── ChunkDiffResult.java            # 对比结果数据类
│   └── OriginalChunkGenerator.java     # 内存区块生成
├── storage/
│   └── ChunkDiffState.java             # 结果缓存管理
├── world/
│   └── SimpleChunkHolder.java          # 区块持有者实现
└── mixin/
    ├── cancelSave/                     # 保存阻止相关Mixin
    │   ├── ChunkPosKeyedStorageMixin.java
    │   ├── EntityChunkDataAccessMixin.java
    │   ├── ServerChunkManagerMixin.java
    │   ├── ServerChunkLoadingManagerMixin.java
    │   ├── ServerWorldMixin.java
    │   ├── VersionedChunkStorageMixin.java
    │   └── WorldMixin.java
    └── ChunkRandomMixin.java           # 随机种子同步Mixin

src/main/resources/
├── chunk-modify.mixins.json            # Mixin 配置
├── fabric.mod.json                     # Mod 元数据
└── data/chunk-modify/dimension/
    └── mirror_overworld.json           # 镜像维度定义
```

## 工作流程

### 区块分析流程
1. 玩家执行 `/chunkmodify analyze`
2. 系统查找镜像维度
3. 从镜像维度获取对应区块（ChunkStatus.FEATURES）
4. 从主世界获取实际区块
5. 逐方块对比，统计差异
6. 生成分析报告并缓存结果
7. 向玩家显示结果

### 维度生成流程
1. 服务器启动时加载镜像维度定义
2. 玩家进入镜像维度或区块被加载
3. ChunkGenerator 开始生成区块
4. **ChunkRandomMixin** 拦截种子获取，返回主世界种子
5. 使用主世界种子生成特征
6. 区块生成完成，与主世界完全一致
7. **保存阻止Mixin** 拦截所有保存操作
8. 数据保留在内存中，不写入磁盘

## 技术亮点

### 1. 多层拦截架构
- 从顶层（World）到底层（Storage）的完整拦截链
- 保证无数据泄漏到磁盘
- 每一层都有明确的职责

### 2. 随机数种子同步
- 精准定位随机数生成点
- 最小化侵入性修改
- 保证特征生成的确定性

### 3. 内存区块生成
- 不依赖磁盘I/O
- 完整的生成管线模拟
- 支持邻居区块依赖

### 4. 高效对比算法
- 逐方块遍历
- 差异对统计
- 缓存管理

## 使用场景

### 1. 反作弊检测
检测玩家是否修改了自然生成的矿石、地形等

### 2. 区域保护验证
验证保护区域是否被破坏或修改

### 3. 地图还原
对比当前地图与原始生成的差异，用于地图还原

### 4. 建筑评估
评估建筑对自然地形的影响程度

## 性能考虑

### 内存使用
- 镜像维度不保存数据，服务器关闭后自动清理
- 分析结果缓存在内存中，可手动清理

### CPU使用
- 区块生成是异步的，不阻塞主线程
- 对比操作在worker线程中执行
- Mixin 拦截的性能开销几乎为零

### 网络带宽
- 镜像维度可以像普通维度一样访问
- 玩家可以在镜像维度中移动查看
- 区块数据按需生成和传输

## 配置选项

当前版本所有功能都是硬编码的，未来可以考虑添加：
- 可配置的维度名称前缀（当前固定为 `mirror_`）
- 可配置的分析范围和深度
- 可配置的缓存大小限制
- 可配置的忽略方块列表

## 已知限制

1. **镜像维度仅支持主世界类型**
   - 当前只实现了 `mirror_overworld.json`
   - 下界和末地需要额外配置

2. **实体不进行对比**
   - 当前只对比方块状态
   - 实体位置和属性未包含

3. **TileEntity 数据未对比**
   - 箱子内容、告示牌文本等未对比
   - 只对比方块类型

4. **需要访问镜像维度**
   - 区块必须在镜像维度中被加载过
   - 未加载的区块无法对比

## 编译和安装

### 编译
```bash
./gradlew build
```

### 输出位置
```
build/libs/chunk-modify-1.0.0.jar
```

### 安装
将 jar 文件放入服务器的 `mods` 文件夹，需要同时安装 Fabric API。

## 依赖
- Minecraft 1.21.6
- Fabric Loader
- Fabric API

## 许可证
CC0-1.0（根据 fabric.mod.json）

## 作者
根据项目配置，请更新 `fabric.mod.json` 中的作者信息。

