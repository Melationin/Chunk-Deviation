package com.zhddsj.chunkdeviation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.zhddsj.chunkdeviation.comparison.ChunkComparator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config
{

    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public static JsonObject config = new JsonObject();
    private static JsonObject createDefaultConfig() {
        JsonObject defaultConfig = new JsonObject();
        defaultConfig.add("worlds", new JsonObject());
        defaultConfig.getAsJsonObject("worlds").add("common", new JsonObject());

        return defaultConfig;
    }


    public static void load(MinecraftServer server)
    {
        Path path =  server.getSavePath(WorldSavePath.ROOT).resolve("chunk_modify_config.json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                config = gson.fromJson(reader, JsonObject.class);
                // 若解析后为 null（例如空文件），创建默认对象
                if (config == null) {
                    config = createDefaultConfig();
                }
            } catch (IOException | JsonSyntaxException e) {
                config = createDefaultConfig();
            }
        } else {
            config = createDefaultConfig();
            ChunkComparator.init(server);
        }
    }

    public static void save(MinecraftServer server)
    {
        Path path =  server.getSavePath(WorldSavePath.ROOT).resolve("chunk_modify_config.json");
        if(!Files.exists(path)){
            try {
                Files.createFile(path);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            String jsonString = gson.toJson(config);
            Files.writeString(path, jsonString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
