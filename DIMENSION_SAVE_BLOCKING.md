# Minecraft 1.21.6 维度保存拦截方案

## 问题描述
需要阻止特定维度（如 `mirror_` 开头的维度）的数据保存到磁盘，但之前的 Mixin 无法完全阻止保存。

## 解决方案

### 保存流程分析
Minecraft 的世界保存有多个层级：

```
MinecraftServer.save()
    ↓
ServerWorld.save()
    ├─ ServerWorld.savePersistentState()  // 保存末影龙战斗等持久化状态
    ├─ ServerChunkManager.save()
    │   ↓
    │   ServerChunkLoadingManager.save()
    │       ├─ save(Chunk)  // 私有方法，保存区块数据
    │       │   ↓
    │       │   VersionedChunkStorage.setNbt()
    │       │       ↓
    │       │       StorageIoWorker.setResult()
    │       │
    │       └─ PointOfInterestStorage.saveChunk()
    │           ↓
    │           SerializingRegionBasedStorage.save()
    │               ↓
    │               ChunkPosKeyedStorage.set()
    │                   ↓
    │                   StorageIoWorker.setResult()
    │
    └─ ServerEntityManager.save()
        ↓
        EntityChunkDataAccess.writeChunkData()
            ↓
            ChunkPosKeyedStorage.set()
                ↓
                StorageIoWorker.setResult()
```

### 实施的 Mixin 拦截点

#### 1. **WorldMixin** - 全局保存开关
- **拦截方法**: `World.isSavingDisabled()`
- **作用**: 让 `mirror_` 维度的世界返回 `savingDisabled = true`
- **位置**: 最顶层拦截

#### 2. **ServerWorldMixin** - 世界级拦截
- **拦截方法**: 
  - `ServerWorld.save()` - 阻止世界保存
  - `ServerWorld.savePersistentState()` - 阻止持久化状态保存
- **作用**: 在 `ServerWorld` 构造函数中设置 `savingDisabled = true`

#### 3. **ServerChunkManagerMixin** - 区块管理器级拦截
- **拦截方法**: `ServerChunkManager.save()`
- **作用**: 阻止区块管理器的保存操作

#### 4. **ServerChunkLoadingManagerMixin** - 区块加载管理器级拦截
- **拦截方法**: `ServerChunkLoadingManager.save()`
- **作用**: 阻止区块加载管理器的保存操作

#### 5. **VersionedChunkStorageMixin** - 区块存储级拦截（新增）
- **拦截方法**: `VersionedChunkStorage.setNbt()`
- **作用**: 阻止区块 NBT 数据写入 StorageIoWorker
- **位置**: 区块数据写入的关键路径

#### 6. **EntityChunkDataAccessMixin** - 实体数据访问级拦截（新增）
- **拦截方法**: `EntityChunkDataAccess.writeChunkData()`
- **作用**: 阻止实体数据写入磁盘
- **位置**: 实体保存的专用路径

#### 7. **ChunkPosKeyedStorageMixin** - 底层存储级拦截（新增）
- **拦截方法**: `ChunkPosKeyedStorage.set()`
- **作用**: 最底层拦截，阻止所有通过该类的数据写入（包括 POI、区块、实体等）
- **位置**: **最关键的拦截点**，所有数据最终都会通过这里写入磁盘

## 关键改进

### 之前缺失的拦截点
1. **VersionedChunkStorage.setNbt()** - 区块 NBT 直接写入路径
2. **EntityChunkDataAccess.writeChunkData()** - 实体数据专用写入路径
3. **ChunkPosKeyedStorage.set()** - **最重要**，这是所有数据写入的最终通道

### ChunkPosKeyedStorage 的重要性
- **POI（兴趣点）** 数据通过 `SerializingRegionBasedStorage` 最终调用 `ChunkPosKeyedStorage.set()`
- **区块数据** 通过 `VersionedChunkStorage.setNbt()` → `StorageIoWorker.setResult()` → 实际调用 `ChunkPosKeyedStorage`
- **实体数据** 通过 `EntityChunkDataAccess.writeChunkData()` → `ChunkPosKeyedStorage.set()`

拦截 `ChunkPosKeyedStorage.set()` 相当于在水龙头的最末端堵住了所有水流！

## 配置文件
所有 Mixin 已添加到 `src/main/resources/chunk-modify.mixins.json`:

```json
{
  "required": true,
  "package": "com.zhddsj.chunkdeviationtion.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "ChunkPosKeyedStorageMixin",      // 底层存储拦截（新增）
    "EntityChunkDataAccessMixin",      // 实体数据拦截（新增）
    "ServerChunkManagerMixin",         // 区块管理器拦截
    "ServerChunkLoadingManagerMixin",  // 区块加载管理器拦截
    "ServerWorldMixin",                // 世界级拦截
    "VersionedChunkStorageMixin",      // 区块存储拦截（新增）
    "WorldMixin"                       // 全局开关拦截
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

## 测试建议
1. 创建一个 `mirror_` 开头的维度
2. 进入该维度并进行各种操作（放置方块、生成实体等）
3. 执行 `/save-all` 命令
4. 检查日志，应该看到多个"阻止保存"的消息
5. 重启服务器，验证 `mirror_` 维度的更改没有被保存

## 注意事项
- 所有拦截点都检查维度名称是否包含 `"mirror_"`
- 拦截后返回已完成的 CompletableFuture，让游戏认为保存成功
- 建议保留所有日志输出（`System.out.println`），便于调试和确认拦截成功

## 维护
如果 Minecraft 更新后保存机制发生变化，重点检查：
1. `StorageIoWorker` 的方法签名是否改变
2. `ChunkPosKeyedStorage` 是否仍然是数据写入的最终通道
3. 是否有新的保存路径绕过了现有拦截点

