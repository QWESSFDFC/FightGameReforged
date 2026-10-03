package cn.gfhnv.game.system.configLoadingSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动期"配置播报"的输出策略：<b>正常路径默认静默，异常路径照旧说话</b>。
 * <p>
 * <b>为什么要有这个类</b>：配置加载的每一句成功播报都只对第一次有用 ——
 * 从第二次启动开始，{@code [配置] TagConfig.json:命中 6 个实体、1 个物品,跳过 0 项} 这种
 * "一切正常"的句子就只是噪声，而它偏偏每一条都刷在最前面，把真正的报错淹掉。
 * 反过来，{@code [配置错误]} / {@code [配置跳过]} 这类<b>能定位到"哪个文件、哪个键、第几行"</b>
 * 的信息一句都不能少：它们是唯一的排查手段。
 * <p>
 * 所以这里的判据只有一条：<b>"这句话是不是在说'一切正常'"</b>。
 * <ul>
 *     <li>{@link #info(String)} —— <b>纯进度与纯成功</b>（"加载配置中"、"实体X加载配置成功"、
 *     "应用 N 项，影响 M 个模板"）→ 默认一行不打，verbose 时原样打；</li>
 *     <li>{@link #problem(String)} —— <b>跳过 / 错误 / 警告 / 未知键 / 坏 JSON</b> →
 *     永远打，与 verbose 无关（诊断能力不许被这次改动削掉）；</li>
 *     <li>{@link #noteworthy(String)} —— <b>提醒</b>（配置没错、值也生效，但两处写同一个数）→
 *     永远打：它本来就是"只在真出事时才出现"的那一类，见
 *     {@code EntityDataPatcher#noticeDualEntryHpMax}。</li>
 * </ul>
 * <p>
 * <b>那"应用了 300 项"怎么确认？</b>成功的播报不是被删掉，而是<b>汇总成一行</b>：
 * {@link #patchApplied} 把每一次补丁的记账累加起来，{@link #printPatchSummary()} 在
 * 全部配置加载完之后打成一句<b>总量</b>（"应用 N 项…合计影响 M 个模板"），
 * 想看逐条明细就开 verbose。所以"配置到底生没生效"这条判据仍然在，只是从 N 行变成 1 行。
 *
 * <h2>verbose 开关（三种写法都认，优先级从高到低）</h2>
 * <ol>
 *     <li><b>系统属性</b>：{@code -Ddsh.config.verbose=true}（注意：{@code -D} 要写在 {@code -jar} 之前）；</li>
 *     <li><b>环境变量</b>：{@code DSH_CONFIG_VERBOSE=true}；</li>
 *     <li><b>配置文件</b>：{@code config/dsh-verbose.txt} 里写一行 {@code config=true}
 *     （这一条是给"双击 bat 启动"准备的 —— 那种启动方式传不进 {@code -D}）。
 *     文件<b>不存在是常态</b>，不产生任何输出。</li>
 * </ol>
 * 开关只在"配错 / 找不到"时<b>从不出声</b>：写错的开关退回默认（静默），
 * 因为"解释一件本来就正常的事"正是这次要收敛掉的东西。
 * <p>
 * <b>注意</b>：本项目还有另一个 {@code -Ddsh.color=on|off}（控制台着色，见
 * {@code 60-COMBAT.md} 的 §5.5.2），那是<b>另一件事</b>，两者互不影响。
 *
 * @author AI（DeepSeek）生成
 */
public final class ConfigOutput {

    /**
     * 打开详细播报的系统属性名。
     */
    public static final String VERBOSE_PROPERTY = "dsh.config.verbose";
    /**
     * 打开详细播报的环境变量名。
     */
    public static final String VERBOSE_ENV = "DSH_CONFIG_VERBOSE";
    /**
     * 打开详细播报的配置文件（相对进程工作目录）。
     */
    public static final String VERBOSE_FILE = "config/dsh-verbose.txt";
    /**
     * 配置文件里认的键名。
     */
    public static final String VERBOSE_FILE_KEY = "config";
    /**
     * 逐次补丁的记账，用来汇总成那一行总量。
     */
    private static final List<Tally> TALLIES = new ArrayList<>();
    /**
     * 显式设置（只给自测用；{@code null} = 回到"按开关判断"）。
     */
    private static Boolean forced;

    /**
     * 工具类，不允许实例化。
     */
    private ConfigOutput() {
    }

    /* ------------------------------------------------------------------
     * 开关
     * ------------------------------------------------------------------ */

    /**
     * @return 现在是不是"详细播报"模式
     */
    public static synchronized boolean verbose() {
        if (forced != null) {
            return forced;
        }
        String property = System.getProperty(VERBOSE_PROPERTY);
        if (property != null && !property.isBlank()) {
            return isOn(property);
        }
        String env = System.getenv(VERBOSE_ENV);
        if (env != null && !env.isBlank()) {
            return isOn(env);
        }
        return fileSwitch();
    }

    /**
     * 自测入口：强制打开/关闭详细播报，{@code null} 表示恢复"按开关判断"。
     *
     * @param value {@code true} / {@code false} / {@code null}
     */
    public static synchronized void setVerboseForTest(Boolean value) {
        forced = value;
    }

    /**
     * @param value 用户写的值
     * @return 是不是"打开"
     */
    private static boolean isOn(String value) {
        String text = value.trim();
        return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("on")
                || text.equalsIgnoreCase("yes") || text.equals("1");
    }

    /**
     * @return 配置文件里是不是写了打开（文件不存在、读不了、键不认 → 一律 {@code false}）
     */
    private static boolean fileSwitch() {
        try {
            java.io.File file = new java.io.File(VERBOSE_FILE);
            if (!file.isFile()) {
                return false;
            }
            for (String line : java.nio.file.Files.readAllLines(file.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }
                int index = trimmed.indexOf('=');
                if (index <= 0) {
                    continue;
                }
                if (trimmed.substring(0, index).trim().equalsIgnoreCase(VERBOSE_FILE_KEY)) {
                    return isOn(trimmed.substring(index + 1));
                }
            }
            return false;
        } catch (java.io.IOException | RuntimeException e) {
            // 开关本身读不了不是"配置问题"，说出来只会再造一行噪声
            return false;
        }
    }

    /* ------------------------------------------------------------------
     * 播报
     * ------------------------------------------------------------------ */

    /**
     * 纯进度 / 纯成功的播报：<b>默认静默</b>，verbose 时才打。
     *
     * @param message 内容
     */
    public static void info(String message) {
        if (verbose()) {
            System.out.println(message);
        }
    }

    /**
     * 跳过 / 错误 / 警告 / 未知键 / 坏 JSON：<b>永远打</b>（与 verbose 无关）。
     *
     * @param message 内容
     */
    public static void problem(String message) {
        System.out.println(message);
    }

    /**
     * 提醒：配置本身没错、值也生效，但有"另一个入口也写了同一个数"这种情况必须被看见 ——
     * <b>永远打</b>。
     *
     * @param message 内容
     */
    public static void noteworthy(String message) {
        System.out.println(message);
    }

    /* ------------------------------------------------------------------
     * 总量汇总（把"一切正常"的 N 行压成 1 行）
     * ------------------------------------------------------------------ */

    /**
     * 记一次补丁的记账（不打印）。同一份配置被调用多次时取<b>最后一次</b>，
     * 所以重复调用幂等、不会把数字叠起来。
     *
     * @param note     来源说明（通常就是文件名）
     * @param applied  应用了几项
     * @param skipped  跳过了几项
     * @param errors   整份配置层面的问题有几条
     * @param affected 影响了几个对象（模板 / 技能）
     * @param unit     对象的量词（"个模板" / "个技能"）；不需要时传 {@code null}
     */
    public static synchronized void tally(String note, int applied, int skipped, int errors,
                                          int affected, String unit) {
        put(new Tally(note, applied, skipped, errors, affected, unit, null));
    }

    /**
     * 记一次"不属于补丁器"的记账（标签配置这种"命中几个"的口径用它），
     * {@code detail} 是这一项在汇总行里的显示文本。
     *
     * @param note    来源说明
     * @param applied 计入总量的项数
     * @param skipped 跳过了几项
     * @param errors  问题有几条
     * @param detail  汇总行里显示成什么样（{@code null} 时显示成 {@code N 项}）
     */
    public static synchronized void tally(String note, int applied, int skipped, int errors,
                                          String detail) {
        put(new Tally(note, applied, skipped, errors, 0, null, detail));
    }

    /**
     * 同一份配置只留最后一条（重复调用幂等，不会把数字叠起来）。
     *
     * @param tally 记账
     */
    private static void put(Tally tally) {
        for (int i = 0; i < TALLIES.size(); i++) {
            if (TALLIES.get(i).note.equals(tally.note)) {
                TALLIES.set(i, tally);
                return;
            }
        }
        TALLIES.add(tally);
    }

    /**
     * 补丁器的记账入口：应用 / 跳过 / 影响几个对象。
     *
     * @param note     来源说明
     * @param applied  应用了几项
     * @param skipped  跳过了几项
     * @param errors   问题有几条
     * @param affected 影响了几个对象
     * @param unit     对象的量词
     */
    public static void patchApplied(String note, int applied, int skipped, int errors,
                                    int affected, String unit) {
        tally(note, applied, skipped, errors, affected, unit);
    }

    /**
     * 补丁器的记账入口（没有"影响几个对象"这个口径时用它）。
     *
     * @param note    来源说明
     * @param applied 应用了几项
     * @param skipped 跳过了几项
     * @param errors  问题有几条
     */
    public static void patchApplied(String note, int applied, int skipped, int errors) {
        tally(note, applied, skipped, errors, 0, null);
    }

    /**
     * 把这一轮启动的全部补丁记账打成<b>一行</b>，然后清空记账（下次启动从零开始）。
     * <p>
     * 一句都不打的情形有两个：没有任何一份配置真的改了值（全是出厂值），
     * 或者 verbose 已经打过逐条明细了。
     *
     * @return 打出去的那一行；没打则返回 {@code null}
     */
    public static synchronized String printPatchSummary() {
        List<Tally> tallies = new ArrayList<>(TALLIES);
        TALLIES.clear();
        String line = summary(tallies);
        if (line != null && !verbose()) {
            System.out.println(line);
        }
        return line;
    }

    /**
     * 清空记账（不打印）。
     */
    public static synchronized void resetTallies() {
        TALLIES.clear();
    }

    /**
     * @return 现在攒了几条记账（只给自测用：验证"汇总打完之后从零开始"）
     */
    public static synchronized int tallyCount() {
        return TALLIES.size();
    }

    /**
     * @param tallies 记账
     * @return 汇总那一行；没东西可说明返回 {@code null}
     */
    private static String summary(List<Tally> tallies) {
        int total = 0;
        int skipped = 0;
        int errors = 0;
        List<String> parts = new ArrayList<>();
        for (Tally tally : tallies) {
            total += tally.applied;
            skipped += tally.skipped;
            errors += tally.errors;
            if (tally.applied > 0) {
                parts.add(tally.note + " " + (tally.detail == null
                        ? tally.applied + " 项" : tally.detail));
            }
        }
        if (parts.isEmpty() && skipped == 0 && errors == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder("[配置] 已加载 ");
        builder.append(String.join("、", parts.isEmpty() ? List.of("(没有改动)") : parts));
        builder.append("，应用 ").append(total).append(" 项，跳过 ").append(skipped).append(" 项");
        if (errors > 0) {
            builder.append("，问题 ").append(errors).append(" 条");
        }
        List<String> affected = new ArrayList<>();
        for (Tally tally : tallies) {
            if (tally.affected > 0 && tally.unit != null) {
                affected.add(tally.affected + " " + tally.unit);
            }
        }
        if (!affected.isEmpty()) {
            builder.append("，影响 ").append(String.join(" / ", affected));
        }
        return builder.toString();
    }

    /**
     * 一份配置的记账。
     */
    private static final class Tally {

        /**
         * 来源说明（文件名）。
         */
        private final String note;
        /**
         * 应用了几项。
         */
        private final int applied;
        /**
         * 跳过了几项。
         */
        private final int skipped;
        /**
         * 问题有几条。
         */
        private final int errors;
        /**
         * 影响了几个对象。
         */
        private final int affected;
        /**
         * 对象的量词。
         */
        private final String unit;
        /**
         * 汇总行里这一项的显示文本（{@code null} = 显示成 {@code N 项}）。
         */
        private final String detail;

        /**
         * @param note     来源说明
         * @param applied  应用了几项
         * @param skipped  跳过了几项
         * @param errors   问题有几条
         * @param affected 影响了几个对象
         * @param unit     对象的量词
         * @param detail   汇总行里这一项的显示文本
         */
        private Tally(String note, int applied, int skipped, int errors, int affected, String unit,
                      String detail) {
            this.note = note;
            this.applied = applied;
            this.skipped = skipped;
            this.errors = errors;
            this.affected = affected;
            this.unit = unit;
            this.detail = detail;
        }
    }
}
