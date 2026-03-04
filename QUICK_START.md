# ChunkModify 快速入门指南

## 安装

1. **下载必要文件**
   - Fabric Loader for Minecraft 1.21.6
   - Fabric API for 1.21.6
   - chunk-modify-1.0.0.jar

2. **安装到服务器**
   ```
   server/
   ├── mods/
   │   ├── fabric-api-x.x.x.jar
   │   └── chunk-modify-1.0.0.jar
   └── ...
   ```

3. **启动服务器**
   ```bash
   java -jar fabric-server-launch.jar
   ```

## 首次使用

### 1. 确认镜像维度已创建
启动服务器后，检查日志中是否有：
```
[ChunkModify] ChunkModify initialized.
```

### 2. 进入镜像维度
使用命令传送到镜像维度：
```
/execute in chunk-modify:mirror_overworld run tp @s ~ ~ ~
```

### 3. 查看镜像维度
- 镜像维度的地形应该与主世界完全一致
- 但没有玩家建筑和修改
- 树木、矿石等自然特征位置相同

### 4. 返回主世界
```
/execute in minecraft:overworld run tp @s ~ ~ ~
```

## 基本命令

### 分析当前区��
在主世界中，站在要分析的位置：
```
/chunkmodify analyze
```

输出示例：
```
=== Chunk(5, 10) 分析结果 ===
  修改率: 12.34%  (1234 / 10000 方块)
  Top 差异对:
    stone → air : 456
    dirt → grass_block : 234
```

### 分析指定区块
```
/chunkmodify analyze 100 200
```
分析方块坐标 (100, 200) 所在的区块

### 查看修改排行
```
/chunkmodify report
```
显示修改率最高的前5个区块

```
/chunkmodify report 10
```
显示前10个

### 清空缓存
```
/chunkmodify clear
```

## 使用场景示例

### 场景1：检查玩家是否偷矿
1. 怀疑玩家在某个区域偷挖矿石
2. 传送到该区域：`/tp 玩家名 100 64 200`
3. 分析区块：`/chunkmodify analyze`
4. 查看报告，如果 `stone → air` 数量很大，说明挖了很多石头/矿石

### 场景2：评估建筑对地形的改动
1. 玩家完成一个建筑
2. 站在建筑中心
3. 分析当前区块：`/chunkmodify analyze`
4. 查看修改率，评估建筑的"地形友好度"

### 场景3：还原被破坏的保护区
1. 发现保护区被破坏
2. 分析该区域的多个区块
3. 根据差异报告，判断需要还原的范围
4. （未来功能）自动从镜像维度复制方块进行还原

### 场景4：检查区域是否原始
1. 需要确认某个区域是否为原始地形
2. 分析该区域：`/chunkmodify analyze X Z`
3. 如果修改率接近 0%，说明是原始地形
4. 如果修改率高，说明有玩家活动

## 理解分析结果

### 修改率
```
修改率: 12.34%  (1234 / 10000 方块)
```
- **12.34%**：该区块中有 12.34% 的方块与原始生成不同
- **1234**：具体修改的方块数量
- **10000**：区块总方块数（16×16×256 = 65536，但实际可能更少）

### 差异对
```
Top 差异对 (生成方块 → 实际方块 : 次数):
  stone → air : 456
  dirt → grass_block : 234
  air → oak_log : 123
```

- **stone → air : 456**
  - 原始生成了石头，现在是空气
  - 表示挖掉了 456 个石头方块
  - 可能是挖矿或者建筑开挖

- **dirt → grass_block : 234**
  - 原始是泥土，现在是草方块
  - 这是自然现象（泥土暴露在阳光下会变草）
  - 通常不算玩家修改

- **air → oak_log : 123**
  - 原始是空气，现在是橡木原木
  - 表示放置了 123 个橡木原木
  - 可能是建筑或种树

### 等价方块
某些方块被认为是等价的，不算作修改：
- `dirt` ↔ `grass_block`
- `dirt` ↔ `coarse_dirt` ↔ `podzol`
- `water` ↔ `bubble_column`
- 所有树叶方块互相等价
- 所有原木方块互相等价
- 等等

这些转换不会计入修改率，因为它们是自然现象或无关紧要的变化。

## 高级技巧

### 批量分析多个区块
使用循环命令（需要数据包或命令方块）：
```
/execute positioned <X1> 0 <Z1> run function my_datapack:analyze_grid
```

在数据包中定义网格分析函数：
```mcfunction
# my_datapack:analyze_grid
chunkmodify analyze ~0 ~0
chunkmodify analyze ~16 ~0
chunkmodify analyze ~0 ~16
chunkmodify analyze ~16 ~16
# ...
```

### 导出分析结果
1. 执行分析命令
2. 从服务器日志中提取结果
3. 使用脚本处理日志，生成报表

### 定期检查
使用 cron 任务或服务器插件定期执行分析：
```bash
#!/bin/bash
# daily_check.sh
rcon-cli "chunkmodify analyze 100 200"
rcon-cli "chunkmodify report 20"
```

## 常见问题

### Q: 为什么修改率不是 0%？
**A:** 即使没有玩家修改，也可能有非零修改率：
- 自然生长（植物、树叶腐烂）
- 实体掉落（沙子、沙砾）
- 水流扩散
- 等价方块转换

通常 < 1% 可以认为是原始地形。

### Q: 镜像维度会保存数据吗？
**A:** 不会。镜像维度的所有数据都在内存中，服务器重启后会丢失。这是设计行为，确保镜像维度始终是"原始"状态。

### Q: 可以在镜像维度中建筑吗？
**A:** 理论上可以，但不推荐：
- 修改会暂时存在于内存中
- 服务器重启后所有修改消失
- 主要用于查看原始地形，不适合建筑

### Q: 分析速度慢怎么办？
**A:** 区块分析涉及大量方块对比，是异步执行的：
- 第一次分析一个区块可能需要几秒
- 后续查询使用缓存，速度很快
- 使用 `/chunkmodify clear` 清空缓存可以释放内存

### Q: 能分析下界/末地吗？
**A:** 当前版本只支持主世界类型维度。需要为下界和末地创建对应的镜像维度配置：
- `data/chunk-modify/dimension/mirror_the_nether.json`
- `data/chunk-modify/dimension/mirror_the_end.json`

### Q: 如何知道是哪个玩家修改的？
**A:** 当前版本不追踪玩家操作。需要配合其他工具：
- CoreProtect：记录方块修改历史
- LogBlock：类似功能
- 先用 ChunkModify 确定修改区域，再用这些工具查详情

## 性能建议

### 内存
- 镜像维度会占用内存
- 建议至少 4GB 内存的服务器
- 定期使用 `/chunkmodify clear` 清理缓存

### CPU
- 区块分析是 CPU 密集型操作
- 避免同时分析大量区块
- 使用异步命令，不会卡服务器

### 磁盘
- 镜像维度不写入磁盘，无影响
- 主世界的保存不受影响

## 下一步

- 阅读 `PROJECT_SUMMARY.md` 了解技术细节
- 阅读 `DIMENSION_SAVE_BLOCKING.md` 了解保存机制
- 阅读 `MIRROR_DIMENSION_FEATURE_FIX.md` 了解随机数修复
- 根据需求修改镜像维度配置

## 支持

如果遇到问题：
1. 检查服务器日志
2. 确认 Fabric API 版本匹配
3. 确认 Minecraft 版本是 1.21.6
4. 查看项目文档

## 更新日志

### v1.0.0
- ✅ 镜像维度系统
- ✅ 完整的保存阻止机制
- ✅ 特征生成一致性修复
- ✅ 区块对比和分析
- ✅ 命令系统
- ✅ 结果缓存管理

