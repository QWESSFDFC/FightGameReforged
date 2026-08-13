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

/**
 * 配置加载器,用于加载游戏本身的配置文件。
 * <p>
 * 目前仅支持加载实体(Entity)和物品(Item)的标签(Tag)配置,
 * 配置文件位于 {@code ./config/gameConfig/TagConfig.json}。
 * <p>
 * 注意:模组的配置加载由各模组自行实现,建议模组配置文件放在
 * {@code config/模组id(或其他尽量唯一的名字)} 目录下。
 */
public class ConfigLoader {
    /**
     * 标签(Tag)配置文件的路径。
     */
    private static final File TAG_CONFIG_FILE = new File("./config/gameConfig/TagConfig.json");
    /**
     * 默认的标签配置内容,以 JSON 字符串形式保存。
     * 当配置文件不存在或不是文件时,将使用该默认配置。
     */
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
    /**
     * 标签映射表。
     * 键为实体或物品的 id,值为该 id 对应的标签类型与标签值的映射。
     */
    private static Map<String, Map<TagType, Tag>> tagsMap = new HashMap<>();

    /**
     * 静态初始化块:确保配置文件的父目录存在,以便后续写入配置文件。
     */
    static {
        if (!TAG_CONFIG_FILE.getParentFile().exists()) TAG_CONFIG_FILE.getParentFile().mkdirs();
    }

    /**
     * 获取标签映射表。
     *
     * @return 标签映射表,键为实体或物品的 id,值为该 id 对应的标签类型与标签值的映射
     */
    public static Map<String, Map<TagType, Tag>> getTagsMap() {
        return tagsMap;
    }

    /**
     * 加载配置文件中的标签配置。
     * <p>
     * 该方法会先清空已有的标签映射表,然后检查配置文件:
     * <ul>
     *     <li>若配置文件不存在,则调用 {@link #setDefaultConfig()} 写入并使用默认配置;</li>
     *     <li>若配置文件存在但不是文件,则删除该路径后写入并使用默认配置;</li>
     *     <li>否则解析配置文件,将配置中的标签应用到对应的实体和物品上。</li>
     * </ul>
     *
     * @throws IOException 当读取或写入配置文件失败时抛出
     */
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

    /**
     * 写入默认配置并加载。
     * <p>
     * 若配置文件的父目录不存在,则会先创建该目录,
     * 然后将默认配置写入配置文件,最后调用 {@link #loadConfig()} 加载配置。
     *
     * @throws IOException 当写入配置文件失败时抛出
     */
    public static void setDefaultConfig() throws IOException {
        if (!TAG_CONFIG_FILE.getParentFile().exists()) TAG_CONFIG_FILE.getParentFile().mkdirs();
        Files.write(TAG_CONFIG_FILE.toPath(), DEFAULT_TAGS_CONFIG.getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        System.out.println("使用默认配置");
        loadConfig();
    }
}
