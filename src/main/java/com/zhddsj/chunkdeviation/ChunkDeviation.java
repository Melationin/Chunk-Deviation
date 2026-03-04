package com.zhddsj.chunkdeviation;

import com.zhddsj.chunkdeviation.command.BlockAndBlockTagArgumentType;
import com.zhddsj.chunkdeviation.command.ConfigCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkDeviation implements ModInitializer {
    public static final String MOD_ID = "chunk-deviation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String modId = "chunk-deviation";
    @Override
    public void onInitialize() {
        ConfigCommand.register();
        ArgumentTypeRegistry.registerArgumentType(
                Identifier.of(modId, "block_and_tag"),   // 唯一标识符
                BlockAndBlockTagArgumentType.class,               // 参数类型类
                ConstantArgumentSerializer.of(BlockAndBlockTagArgumentType::blockOrTag) // 序列化器
        );

        LOGGER.info("ChunkModify initialized.");
    }
}
