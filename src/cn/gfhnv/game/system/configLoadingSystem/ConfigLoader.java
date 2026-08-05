package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.system.thinkingSystem.Tag;
import cn.gfhnv.game.system.thinkingSystem.TagType;
import cn.gfhnv.game.world.World;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {//只加载游戏本身设置(目前只有tag(实体和物品)设置存在TagConfig.json中).模组的自己写加载方法,建议模组配置文件放在config/模组id(或者其他的尽量唯一的名字)下面
    private static final File TAG_CONFIG_FILE = new File("./config/gameConfig/TagConfig.json");
    private static final String DEFAULT_TAGS_CONFIG = "{\n" +
            "  \"game_official_content:insectBoss\": {\n" +
            "    \"attack\": 2,\n" +
            "    \"heal\": 1,\n" +
            "    \"defence\": 1\n" +
            "  },\n" +
            "  \"game_official_content:commonInsect\": {\n" +
            "    \"attack\": 1,\n" +
            "    \"heal\": 0,\n" +
            "    \"defence\": 0\n" +
            "  },\n" +
            "  \"game_official_content:iceInsect\": {\n" +
            "    \"attack\": 1,\n" +
            "    \"heal\": 0,\n" +
            "    \"defence\": 0,\n" +
            "    \"control_enemies\": 9\n" +
            "  },\n" +
            "  \"game_official_content:playerOne\":{\n" +
            "    \"attack\": 2,\n" +
            "    \"heal\": 1,\n" +
            "    \"defence\": 1,\n" +
            "    \"restoration_mana\": 1,\n" +
            "    \"damage_enhance\": 1\n" +
            "  },\n" +
            "  \"game_official_content:actorLiXiaoYan\":{\n" +
            "    \"attack\": 2,\n" +
            "    \"heal\": 1,\n" +
            "    \"defence\": 1,\n" +
            "    \"restoration_mana\": 1,\n" +
            "    \"damage_enhance\": 1\n" +
            "  },\n" +
            "  \"game_official_content:aNiceSword\": {\n" +
            "    \"damage_enhance\": 2\n" +
            "  },\"game_official_content:phainon\": {\n" +
            "  \"attack\": 5,\n" +
            "  \"heal\": 0,\n" +
            "  \"defence\": 0,\n" +
            "  \"restoration_mana\": 2,\n" +
            "  \"damage_enhance\": 3\n" +
            "}\n" +
            "}";
    private static Map<String, Map<TagType, Tag>> tagsMap = new HashMap<>();

    static {
        if (!TAG_CONFIG_FILE.getParentFile().exists()) TAG_CONFIG_FILE.getParentFile().mkdirs();
    }

    public static Map<String, Map<TagType, Tag>> getTagsMap() {
        return tagsMap;
    }

    public static void loadConfig() throws IOException {
        tagsMap.clear();
        if (!TAG_CONFIG_FILE.exists()) {
            System.out.println("没有检测到配置文件");
            setDefaultConfig();
            return;
        }
        if (!TAG_CONFIG_FILE.isFile()) {
            TAG_CONFIG_FILE.delete();
            setDefaultConfig();
            return;
        }
        JSONObject jsonObject = new JSONObject(Files.readString(TAG_CONFIG_FILE.toPath(), StandardCharsets.UTF_8));
        for (String id : jsonObject.keySet()) {
            JSONObject inner = jsonObject.getJSONObject(id);
            Map<TagType, Tag> map = new EnumMap<>(TagType.class);
            for (String tagType : inner.keySet()) {
                map.put(TagType.valueOf(tagType.toUpperCase()), new Tag(inner.getDouble(tagType)));
            }
            tagsMap.put(id, map);
        }
        if (World.getEntityList().isEmpty()) {
            System.out.println("没有实体");
            return;
        }//没有实体,游戏无法进行
        for (Entity entity : World.getEntityList()) {
            if (tagsMap.containsKey(entity.getId())) {
                entity.setTags(tagsMap.get(entity.getId()));
                System.out.println("实体" + entity.getId() + "加载配置成功");
            }
        }
        if (World.getItemList().isEmpty()) {
            System.out.println("没有物品");
            return;
        }
        //有没有物品无所谓
        for (Item item : World.getItemList()) {
            if (tagsMap.containsKey(item.getId())) {
                item.setTags(tagsMap.get(item.getId()));
                System.out.println("物品" + item.getId() + "加载配置成功.");
            }
        }
    }

    public static void setDefaultConfig() throws IOException {
        if (!TAG_CONFIG_FILE.getParentFile().exists()) TAG_CONFIG_FILE.getParentFile().mkdirs();
        Files.write(TAG_CONFIG_FILE.toPath(), DEFAULT_TAGS_CONFIG.getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        System.out.println("使用默认配置");
        loadConfig();
    }
}
