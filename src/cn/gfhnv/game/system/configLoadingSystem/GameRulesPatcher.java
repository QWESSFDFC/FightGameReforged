package cn.gfhnv.game.system.configLoadingSystem;

import cn.gfhnv.game.system.logSystem.LogWriter;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

/**
 * 游戏规则补丁器：把 {@code config/gameConfig/GameRules.json} 里的数值读进
 * {@link GameRules} 那张静态规则表。
 * <p>
 * <b>为什么不是"对象补丁"</b>：实体与技能的默认值住在对象上，所以那边是"改一个已经存在的对象"；
 * 规则值的默认值住在<b>代码公式</b>里，没有对象可改，只能"读进一张表，代码按
 * {@code GameRules.getXxx(键, 字面量)} 取"。所以这里没有 {@code Snapshot}，
 * 取而代之的是 {@link GameRules#resetForTest()}。
 * <p>
 * <b>文件结构</b>：顶层是 {@code version} 加若干段（{@code formula} / {@code mana} /
 * {@code flameReaver} / {@code insectBoss} / {@code actorLiXiaoYan}），
 * 段名 + {@code .} + 键名拼成规则键（{@code formula.hpBase}）。
 * 未知段 / 未知键 / 类型不对都只跳过那一项，结尾汇总打印，<b>一个坏键不废整份配置</b>。
 *
 * @author AI（DeepSeek）生成
 */
public final class GameRulesPatcher {

    /**
     * {@code version} 之外、本版认识的段名。
     * <p>
     * <b>它不再是 5 个手写字面量</b>：直接来自 {@link RuleKeySpecs} 那张唯一的表
     * （按首次出现的顺序去重）—— 加一个段只要在表里加一行，这里自动跟上。
     */
    private static final List<String> SECTIONS = RuleKeySpecs.sections();

    /**
     * 工具类，不允许实例化。
     */
    private GameRulesPatcher() {
    }

    /**
     * 把一份规则文档读进 {@link GameRules}。
     * <p>
     * 调用方负责先 {@link GameRules#beginLoad()}、之后 {@link GameRules#freeze()}。
     *
     * @param root 根对象
     * @param note 来源说明（写进报告与日志）
     * @return 报告
     */
    public static Report apply(JSONObject root, String note) {
        Report report = new Report(note);
        if (root == null) {
            report.error("根不是 JSON 对象");
            return report;
        }
        for (String section : root.keySet()) {
            if (DataKeys.VERSION.equals(section)) {
                continue;
            }
            JSONObject block = root.optJSONObject(section);
            if (block == null) {
                if (root.isNull(section)) {
                    report.skipped(section, "值是 null（整段删掉即可，不要写 null）");
                } else {
                    report.skipped(section, "它不是一个段对象（该是 {\"键\": 数值}）");
                }
                continue;
            }
            if (!SECTIONS.contains(section)) {
                report.skipped(section, "未知段（本版认识的段：" + SECTIONS + "）");
                continue;
            }
            for (String name : block.keySet()) {
                String key = section + DataKeys.SECTION_SEPARATOR + name;
                Object raw = block.opt(name);
                Double value = EntityDataPatcher.asDouble(raw);
                if (value == null) {
                    report.skipped(key, "类型不对（要数字，实际是 " + EntityDataPatcher.describe(raw) + "）");
                    continue;
                }
                if (!GameRules.isKnown(key)) {
                    report.skipped(key, "未知键（它不属于游戏规则，已经忽略）");
                    continue;
                }
                GameRules.put(key, value);
                report.applied(key, String.valueOf(value));
            }
        }
        return report;
    }

    /**
     * 一次性解析 + 应用（给自测用的入口）。
     *
     * @param json 配置文本
     * @param note 来源说明
     * @return 报告；JSON 本身解析不了时返回一个只带错误的报告
     */
    public static Report applyJson(String json, String note) {
        try {
            return apply(new JSONObject(json), note);
        } catch (JSONException e) {
            Report report = new Report(note);
            report.error("不是合法的 JSON：" + e.getMessage());
            return report;
        }
    }

    /**
     * 把报告打到控制台并写日志（错误必须一眼看得见，不能只进日志文件）。
     *
     * @param report 报告
     */
    public static void print(Report report) {
        report.print();
        for (String error : report.errors()) {
            LogWriter.writeLog("[配置错误] " + report.note() + "：" + error);
        }
    }

    /**
     * 一次规则加载的结果：应用了几项、跳过了几项、为什么。
     *
     * @author AI（DeepSeek）生成
     */
    public static final class Report {

        /**
         * 来源说明。
         */
        private final String note;
        /**
         * 应用成功的条目（规则键 → 值）。
         */
        private final Map<String, String> applied = new LinkedHashMap<>();
        /**
         * 被跳过的条目。
         */
        private final List<String> skipped = new ArrayList<>();
        /**
         * 整份配置层面的问题。
         */
        private final List<String> errors = new ArrayList<>();

        /**
         * @param note 来源说明
         */
        public Report(String note) {
            this.note = note;
        }

        /**
         * @param key   规则键
         * @param value 生效值
         */
        public void applied(String key, String value) {
            applied.put(key, value);
        }

        /**
         * @param path   键路径（或段名）
         * @param reason 为什么跳过
         */
        public void skipped(String path, String reason) {
            skipped.add(path + "：" + reason);
        }

        /**
         * @param message 问题描述
         */
        public void error(String message) {
            errors.add(message);
        }

        /**
         * @return 来源说明
         */
        public String note() {
            return note;
        }

        /**
         * @return 应用成功的键 → 值
         */
        public Map<String, String> appliedEntries() {
            return Collections.unmodifiableMap(applied);
        }

        /**
         * @return 被跳过的条目
         */
        public List<String> skippedEntries() {
            return Collections.unmodifiableList(skipped);
        }

        /**
         * @return 整份配置层面的问题
         */
        public List<String> errors() {
            return Collections.unmodifiableList(errors);
        }

        /**
         * @return 是否一切正常（没有错误、也没有跳过项）
         */
        public boolean isClean() {
            return errors.isEmpty() && skipped.isEmpty();
        }

        /**
         * 把结果打到控制台：<b>错在哪里必须一眼看得见</b>（永远打），
         * "一切正常"的汇总行只在 verbose 时打（默认静默，见 {@link ConfigOutput}）。
         */
        public void print() {
            for (String message : errors) {
                ConfigOutput.problem("[配置错误] " + note + "：" + message);
            }
            for (String message : skipped) {
                ConfigOutput.problem("[配置跳过] " + note + "：" + message);
            }
            ConfigOutput.info("[配置] " + note + "：应用 " + applied.size() + " 项，跳过 "
                    + (skipped.size() + errors.size()) + " 项");
            // 规则表没有"影响几个对象"这个口径，留一笔账只为让总量汇总里出现它的名字
            ConfigOutput.patchApplied(note, applied.size(), skipped.size(), errors.size());
        }
    }
}
