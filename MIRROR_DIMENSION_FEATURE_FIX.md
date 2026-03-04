# 镜像维度特征生成一致性修复

## 问题描述
镜像维度即使使用与主世界完全相同的世界生成配置（相同的生成器类型、生物群系源、设置），但生成的树木等自然特征仍然不完全一致。

## 问题原因分析

### 世界生成的随机数机制
Minecraft 的世界生成使用随机数生成器来决定特征的位置、大小、形状等。关键的随机数生成发生在：

1. **特征生成阶段 (FEATURES)**：`ChunkGenerator.generateFeatures()`
2. **随机种子初始化**：
   ```java
   ChunkRandom chunkRandom = new ChunkRandom(new Xoroshiro128PlusPlusRandom(RandomSeed.getSeed()));
   long l = chunkRandom.setPopulationSeed(world.getSeed(), blockPos.getX(), blockPos.getZ());
   ```

### 关键问题
`chunkRandom.setPopulationSeed(world.getSeed(), ...)` 使用的是 **当前世界的种子** (`world.getSeed()`)。

- **主世界**：使用存档级别的世界种子
- **镜像维度**：即使配置相同，但作为不同的维度实例，其 `world.getSeed()` 可能受到：
  - 维度注册顺序
  - 维度类型的哈希值
  - 内部维度ID
  - 等因素的影响

因此，即使配置完全相同，两个维度的随机数序列也会不同，导致树木位置、大小不同。

## 解决方案

### ChunkRandomMixin
创建一个 Mixin 来拦截 `ChunkGenerator.generateFeatures()` 中对 `world.getSeed()` 的调用：

```java
@Mixin(ChunkGenerator.class)
public class ChunkRandomMixin {
    
    @Redirect(
            method = "generateFeatures",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/StructureWorldAccess;getSeed()J"
            )
    )
    private long chunkModify$useOverworldSeedForMirror(StructureWorldAccess world) {
        if (world instanceof ServerWorld serverWorld) {
            String dimensionName = serverWorld.getRegistryKey().getValue().toString();
            
            // 如果是镜像维度，强制使用主世界的种子
            if (dimensionName.contains("mirror_")) {
                ServerWorld overworld = serverWorld.getServer().getOverworld();
                long overworldSeed = overworld.getSeed();
                return overworldSeed;
            }
        }
        
        return world.getSeed();
    }
}
```

### 工作原理

1. **拦截点**：在 `ChunkGenerator.generateFeatures()` 方法中
2. **拦截的调用**：`world.getSeed()` 
3. **判断逻辑**：检查维度名称是否包含 `"mirror_"`
4. **替换行为**：
   - 如果是镜像维度 → 返回主世界的种子
   - 否则 → 返回原始种子

### 效果
通过这个 Mixin：
- 镜像维度在生成特征时会使用与主世界完全相同的随机种子
- 相同的区块坐标 + 相同的种子 = 相同的随机数序列
- 相同的随机数序列 = 完全一致的特征生成（树木位置、大小、形状等）

## 技术细节

### 随机种子计算公式
```java
long populationSeed = decorationSeed XOR worldSeed;
// 其中 decorationSeed 由区块坐标计算：
decorationSeed = (chunkX * 341873128712L + chunkZ * 132897987541L) ^ worldSeed;
```

只要 `worldSeed` 和 `chunkX`, `chunkZ` 相同，`populationSeed` 就会相同，从而保证随机数序列一致。

### 为什么不拦截其他随机数调用？
- **结构生成 (STRUCTURE_STARTS)**：也使用世界种子，但结构位置由结构放置计算器决定，与特征生成分开
- **洞穴生成 (CARVERS)**：使用相同的机制，也会受益于这个修复
- **生物群系放置**：由 BiomeSource 决定，与特征随机数无关

## 验证方法

1. **启动服务器**，确保镜像维度已创建
2. **进入主世界**，记录一棵树的位置（如 X=100, Z=200）
3. **进入镜像维度**，传送到相同坐标
4. **比较**：树的位置、大小、形状应该完全一致

### 使用命令验证
```
/chunkmodify analyze 100 200
```
查看镜像维度与主世界的区块差异，修改率应该接近 0%（排除玩家修改的情况）。

## 注意事项

1. **仅对新生成的区块有效**
   - 如果镜像维度的区块已经生成并保存，需要删除该维度的区块数据重新生成
   - 或者使用 `/forceload` 命令重新加载

2. **维度配置必须完全一致**
   - 生成器类型必须相同
   - 生物群系源必须相同
   - 所有生成器设置必须相同
   - 仅随机种子被强制同步

3. **不影响主世界**
   - 主世界继续使用自己的原始种子
   - 只有镜像维度会被重定向到使用主世界种子

4. **与其他Mod的兼容性**
   - 如果其他Mod也修改了特征生成的随机数，可能需要调整Mixin优先级
   - 建议在 `chunk-modify.mixins.json` 中设置适当的优先级

## 配置
在 `chunk-modify.mixins.json` 中已添加：
```json
{
  "mixins": [
    ...
    "ChunkRandomMixin"
  ]
}
```

## 日志输出
启用后，特征生成时会输出：
```
[ChunkModify] 镜像维度 minecraft:mirror_overworld 使用主世界种子: 123456789
```

这表示 Mixin 正在正常工作。

## 性能影响
- **几乎无性能影响**：只是替换一个 long 值
- **无额外计算**：不涉及复杂的随机数重新计算
- **线程安全**：每次调用都是独立的，不存在竞态条件

