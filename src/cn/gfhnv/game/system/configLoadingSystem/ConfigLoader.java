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

public class ConfigLoader {//只加载游戏本身设置(目前只有tag设置存在config.json中).模组的自己写加载方法,
    private static final File TAG_CONFIG_FILE = new File("./config/gameConfig/TagConfig.json");
    private static Map<String,Map<TagType, Tag>> tagsMap = new HashMap<>();

    public static Map<String, Map<TagType, Tag>> getTagsMap() {
        return tagsMap;
    }

    private static final String DEFAULT_TAGS_CONFIG ="{\n" +
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
            "  }\n" +
            "}";
    public static void loadConfig() throws IOException {
        if (!TAG_CONFIG_FILE.exists()) {setDefaultConfig();return;}
    if (!TAG_CONFIG_FILE.isFile()) {TAG_CONFIG_FILE.delete();setDefaultConfig();return;}
    JSONObject jsonObject = new JSONObject(Files.readString(TAG_CONFIG_FILE.toPath(), StandardCharsets.UTF_8));
    for (String id : jsonObject.keySet()) {
        JSONObject inner=jsonObject.getJSONObject(id);
        Map<TagType, Tag> map = new EnumMap<>(TagType.class);
      for (String tagType : inner.keySet()) {
          map.put(TagType.valueOf(tagType.toUpperCase()),new Tag(inner.getDouble(tagType)));
      }
      tagsMap.put(id, map);
    }
    if (World.getEntityList().isEmpty()) System.out.println("没有实体");
    if (World.getEntityList().isEmpty())return;//没有实体,游戏无法进行
    for (Entity entity : World.getEntityList()) {
        if (tagsMap.containsKey(entity.getId())) {
            entity.setTags(tagsMap.get(entity.getId()));
            System.out.println("实体"+entity.getId()+"加载配置成功");
        }
    }
    if (World.getItemList().isEmpty()) System.out.println("没有物品");
    if (World.getItemList().isEmpty())return;//有没有物品无所谓
    for (Item item:World.getItemList()) {
        if (tagsMap.containsKey(item.getId())) {
            item.setTags(tagsMap.get(item.getId()));
            System.out.println("物品"+item.getId()+"加载配置成功.");
        }
    }
    }

    public static void setDefaultConfig() throws IOException {
        Files.write(TAG_CONFIG_FILE.toPath(), DEFAULT_TAGS_CONFIG.getBytes(),StandardOpenOption.APPEND,StandardOpenOption.CREATE);
        System.out.println("使用默认配置");
        loadConfig();
    }
}
