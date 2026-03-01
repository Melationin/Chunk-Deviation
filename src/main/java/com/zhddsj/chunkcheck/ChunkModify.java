package com.zhddsj.chunkcheck;

import com.zhddsj.chunkcheck.command.ChunkCompareCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkModify implements ModInitializer {
	public static final String MOD_ID = "chunk-modify";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[ChunkModify] Initializing Chunk Compare mod...");

		// Register the /chunkcompare command
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				ChunkCompareCommand.register(dispatcher)
		);

		LOGGER.info("[ChunkModify] Commands registered.");
	}
}