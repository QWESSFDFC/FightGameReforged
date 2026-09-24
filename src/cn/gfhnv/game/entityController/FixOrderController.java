package cn.gfhnv.game.entityController;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.Fight;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定顺序控制器：按一张固定的「轮转表」依次放技能，并且可以指定下一个要放的技能。
 * <p>
 * 适合做成《崩坏：星穹铁道》里那种怪物：行动顺序是写死的循环，剧本需要时再插一招。
 * <pre>{@code
 * FixOrderController controller = new FixOrderController(skills, boss);
 * controller.setRotationByName("蓄力", "横扫", "分裂");        // 轮转顺序
 * controller.setTargetStrategy(TargetStrategies.tauntAware(TargetStrategies.lowestHp()));
 * ...
 * controller.forceNextSkill("大招");                          // 剧本指定下一招（插入）
 * controller.forceNextSkill("大招", true);                     // 指定并吃掉轮转里的下一步（替换）
 * }</pre>
 * <h2>轮转表</h2>
 * 表里存的是<b>技能名</b>，每次使用前才在自己的技能实例里按名字取 ——
 * 这样控制器被复制（{@code 模板.copy()}）之后，重建出来的控制器会把轮转挂到<b>自己</b>的技能实例上；
 * 若直接存 {@link Skill} 对象引用，冷却就会加到旧的技能对象上，而回合循环递减的是新对象的冷却
 * （{@code FightTurnPastListener} 遍历的是 {@code getController().getSkills()}）。
 * <p>
 * 表为空时：先用 {@link #getiInitialize()}（若内容设置了）填充，还空就按 {@link #getSkills()} 的顺序。
 * <p>
 * <h2>放不出来怎么办</h2>
 * 默认 {@code skipUnusable = true}：轮到的那一招若在冷却/法力不足，就<b>顺延</b>到下一个能放的，
 * 并打印原因（不会像以前那样把技能从表里取走、白白浪费一个回合）。
 * 设成 {@code false} 则是严格顺序：放不出来就不行动，游标停在原地。
 *
 * @author AI（DeepSeek）生成
 */
public class FixOrderController extends UniversalController {

    /**
     * 轮转顺序（技能名）。空表示「按 {@link #getSkills()} 的顺序」。
     */
    private final List<String> rotationNames = new ArrayList<>();

    /**
     * 下一个要用轮转表里的第几个。
     */
    private int rotationIndex = 0;

    /**
     * 指定的「下一个要放的技能」（技能名）；放完或放不出来就清掉。
     */
    private String forcedSkillName;

    /**
     * 指定的技能是否顺手吃掉轮转表里的下一步（替换语义）。
     */
    private boolean forcedReplacesNext;

    /**
     * 放不出来时是否顺延到下一招。
     */
    private boolean skipUnusable = true;

    /**
     * 构造一个固定顺序控制器（轮转表默认用 {@link #getSkills()} 的顺序）。
     *
     * @param skills 技能列表（会被复制一份）
     * @param owner  持有者
     */
    public FixOrderController(List<Skill> skills, LivingThing owner) {
        super(skills, owner);
        resetRotation();
    }

    /**
     * 复制构造器。
     * <p>
     * {@code super(other, owner)} 已经把技能复制成新实例，所以这里按<b>名字</b>重建轮转表，
     * 保证轮转指向的是自己的技能实例（否则冷却会记在旧对象上）。
     *
     * @param other 被复制的控制器
     * @param owner 新的持有者
     */
    public FixOrderController(FixOrderController other, LivingThing owner) {
        super(other, owner);
        this.skipUnusable = other.skipUnusable;
        this.rotationNames.addAll(other.rotationNames);
        this.rotationIndex = other.rotationIndex;
        if (this.rotationNames.isEmpty()) {
            resetRotation();
        }
        if (this.rotationIndex < 0 || this.rotationIndex >= this.rotationNames.size()) {
            this.rotationIndex = 0;
        }
        if (other.forcedSkillName != null && findSkillByName(other.forcedSkillName) != null) {
            this.forcedSkillName = other.forcedSkillName;
            this.forcedReplacesNext = other.forcedReplacesNext;
        }
    }

    /* ------------------------------------------------------------------
     * 轮转表
     * ------------------------------------------------------------------ */

    /**
     * @return 轮转顺序（技能名的副本）
     */
    public List<String> getRotationNames() {
        return new ArrayList<>(rotationNames);
    }

    /**
     * 按技能名设置轮转顺序；名字在自己的技能列表里找不到的会被忽略（并打印提示）。
     *
     * @param skillNames 技能名（按行动顺序）
     */
    public void setRotationByName(List<String> skillNames) {
        rotationNames.clear();
        rotationIndex = 0;
        if (skillNames != null) {
            for (String name : skillNames) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                String trimmed = name.trim();
                if (findSkillByName(trimmed) == null) {
                    System.out.println(getOwner().getName() + "没有技能「" + trimmed + "」，轮转表里已忽略它");
                    continue;
                }
                rotationNames.add(trimmed);
            }
        }
        if (rotationNames.isEmpty()) {
            resetRotation();
        }
    }

    /**
     * 按技能名设置轮转顺序（可变参数版）。
     *
     * @param skillNames 技能名（按行动顺序）
     */
    public void setRotationByName(String... skillNames) {
        List<String> names = new ArrayList<>();
        if (skillNames != null) {
            for (String name : skillNames) {
                names.add(name);
            }
        }
        setRotationByName(names);
    }

    /**
     * 按一批技能设置轮转顺序（只取它们的名字）。
     *
     * @param rotation 技能列表
     */
    public void setRotation(List<Skill> rotation) {
        List<String> names = new ArrayList<>();
        if (rotation != null) {
            for (Skill skill : rotation) {
                if (skill != null) {
                    names.add(skill.getName());
                }
            }
        }
        setRotationByName(names);
    }

    /**
     * 恢复默认轮转：按 {@link #getSkills()} 的顺序。
     */
    public void resetRotation() {
        rotationNames.clear();
        for (Skill skill : getSkills()) {
            if (skill != null && skill.getName() != null) {
                rotationNames.add(skill.getName());
            }
        }
        rotationIndex = 0;
    }

    /* ------------------------------------------------------------------
     * 指定下一个技能
     * ------------------------------------------------------------------ */

    /**
     * 指定下一个要放的技能（插入语义：放完继续按轮转走，不消耗轮转里的下一步）。
     *
     * @param skillName 技能名
     * @return 是否找到了这个技能
     */
    public boolean forceNextSkill(String skillName) {
        return forceNextSkill(skillName, false);
    }

    /**
     * 指定下一个要放的技能。
     *
     * @param skillName   技能名
     * @param replaceNext {@code true} 表示顺手吃掉轮转表里的下一步（替换语义）
     * @return 是否找到了这个技能
     */
    public boolean forceNextSkill(String skillName, boolean replaceNext) {
        Skill skill = findSkillByName(skillName);
        if (skill == null) {
            return false;
        }
        forcedSkillName = skill.getName();
        forcedReplacesNext = replaceNext;
        return true;
    }

    /**
     * 指定下一个要放的技能（按对象，内部仍按名字匹配）。
     *
     * @param skill 技能
     * @return 是否找到了这个技能
     */
    public boolean forceNextSkill(Skill skill) {
        return skill != null && forceNextSkill(skill.getName(), false);
    }

    /**
     * 取消指定。
     */
    public void clearForcedSkill() {
        forcedSkillName = null;
        forcedReplacesNext = false;
    }

    /**
     * @return 当前指定的技能名；没有指定返回 {@code null}
     */
    public String peekForcedSkillName() {
        return forcedSkillName;
    }

    /* ------------------------------------------------------------------
     * 行动
     * ------------------------------------------------------------------ */

    /**
     * 下一个会用的技能（不消耗、[不检查冷却]）：有指定就用指定的，否则用轮转表当前那一招。
     *
     * @return 技能；没有可用技能时返回 {@code null}
     */
    public Skill peekNextSkill() {
        ensureRotation();
        if (forcedSkillName != null) {
            Skill forced = findSkillByName(forcedSkillName);
            if (forced != null) {
                return forced;
            }
        }
        if (rotationNames.isEmpty()) {
            return null;
        }
        return findSkillByName(rotationNames.get(rotationIndex % rotationNames.size()));
    }

    /**
     * 是否在放不出来时顺延到下一招。
     *
     * @return 是否顺延
     */
    public boolean isSkipUnusable() {
        return skipUnusable;
    }

    /**
     * 设置放不出来时是否顺延。
     *
     * @param skipUnusable {@code true}=顺延到下一招；{@code false}=严格顺序，放不出来就不行动
     */
    public void setSkipUnusable(boolean skipUnusable) {
        this.skipUnusable = skipUnusable;
    }

    @Override
    public void act(Fight fight) {
        if (getOwner() == null || getSkills().isEmpty()) {
            System.out.println((getOwner() == null ? "有人" : getOwner().getName()) + "没行动");
            return;
        }
        ensureRotation();

        RotationStep step = takeForcedStep(fight);
        if (step == null) {
            step = takeRotationStep(fight);
        }
        if (step == null) {
            System.out.println(getOwner().getName() + "没行动"
                    + (skipUnusable ? "（轮转表里没有能放的技能）" : "（轮到的技能还在冷却）"));
            return;
        }

        Skill selected = step.skill;
        boolean used;
        if (selected.getAims() == 0) {
            used = selected.use(fight, getOwner());
        } else {
            List<LivingThing> targets = resolveTargets(fight, selected);
            used = selected.use(fight, getOwner(), targets);
            if (targets.isEmpty()) {
                System.out.println(getOwner().getName() + "用「" + selected.getName() + "」时没有可选目标");
            }
            // 与 UniversalController 保持一致：目标选定就发事件（白厄的「火种」靠它计数）
            EventBus.post(new SelectTargetEvent(getOwner(), targets, fight));
        }

        if (used) {
            rotationIndex = step.nextIndex;
        } else {
            System.out.println(getOwner().getName() + "没能放出「" + selected.getName() + "」（冷却或法力不足）");
            if (skipUnusable && !step.fromForced) {
                // 顺延：把这一招跳过，免得永远卡在同一个技能上
                rotationIndex = step.nextIndex;
            }
        }
    }

    /**
     * 轮转表为空时就地填充：先用 {@code iInitialize}（内容可以自定义顺序），否则按技能列表顺序。
     */
    private void ensureRotation() {
        if (!rotationNames.isEmpty()) {
            return;
        }
        if (getiInitialize() != null) {
            getiInitialize().initialize(this);
        }
        if (rotationNames.isEmpty()) {
            resetRotation();
        }
    }

    /**
     * 取「指定」的那一步（没指定或指定的技能不存在/放不出来时返回 {@code null}）。
     *
     * @param fight 当前战斗
     * @return 这一步；没有则返回 {@code null}
     */
    private RotationStep takeForcedStep(Fight fight) {
        if (forcedSkillName == null) {
            return null;
        }
        Skill forced = findSkillByName(forcedSkillName);
        boolean replaceNext = forcedReplacesNext;
        // 无论用不用得上，指定都只生效一次
        forcedSkillName = null;
        forcedReplacesNext = false;
        if (forced == null) {
            return null;
        }
        if (!forced.canUse(fight, getOwner())) {
            System.out.println(getOwner().getName() + "指定的技能「" + forced.getName()
                    + "」放不出来（冷却或法力不足），改为按顺序行动");
            return null;
        }
        int size = rotationNames.size();
        int nextIndex = replaceNext && size > 0 ? (rotationIndex + 1) % size : rotationIndex;
        return new RotationStep(forced, nextIndex, true);
    }

    /**
     * 从轮转表里取下一个能放的技能。
     *
     * @param fight 当前战斗
     * @return 这一步；一轮下来都没有能放的则返回 {@code null}
     */
    private RotationStep takeRotationStep(Fight fight) {
        int size = rotationNames.size();
        if (size == 0) {
            return null;
        }
        if (rotationIndex < 0 || rotationIndex >= size) {
            rotationIndex = 0;
        }
        int index = rotationIndex;
        for (int tried = 0; tried < size; tried++) {
            String name = rotationNames.get(index);
            Skill skill = findSkillByName(name);
            if (skill != null && skill.canUse(fight, getOwner())) {
                return new RotationStep(skill, (index + 1) % size, false);
            }
            if (!skipUnusable) {
                return null;
            }
            if (skill != null) {
                System.out.println(getOwner().getName() + "的「" + skill.getName() + "」还用不了，顺延到下一招");
            }
            index = (index + 1) % size;
        }
        return null;
    }

    /**
     * 按名字在自己的技能实例里找技能。
     *
     * @param name 技能名（忽略大小写、忽略首尾空白）
     * @return 技能；找不到返回 {@code null}
     */
    private Skill findSkillByName(String name) {
        if (name == null) {
            return null;
        }
        String target = name.trim();
        for (Skill skill : getSkills()) {
            if (skill != null && skill.getName() != null && skill.getName().equalsIgnoreCase(target)) {
                return skill;
            }
        }
        return null;
    }

    /**
     * 从轮转表里挑出来的「这一步」：用哪个技能、用完把游标推进到哪。
     */
    private static final class RotationStep {

        /**
         * 这一步要放的技能。
         */
        private final Skill skill;

        /**
         * 放成功之后游标要推进到的位置。
         */
        private final int nextIndex;

        /**
         * 这一步是不是「指定的技能」（指定技能放失败时不做顺延处理）。
         */
        private final boolean fromForced;

        /**
         * 构造一步。
         *
         * @param skill      技能
         * @param nextIndex  用完之后游标的位置
         * @param fromForced 是否来自「指定技能」
         */
        private RotationStep(Skill skill, int nextIndex, boolean fromForced) {
            this.skill = skill;
            this.nextIndex = nextIndex;
            this.fromForced = fromForced;
        }
    }
}
