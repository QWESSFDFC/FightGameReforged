package cn.gfhnv.game.entityController;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.event.EventBus;
import cn.gfhnv.game.event.SelectTargetEvent;
import cn.gfhnv.game.interfaces.IInitialize;
import cn.gfhnv.game.interfaces.ISpecialAction;
import cn.gfhnv.game.interfaces.TargetStrategy;
import cn.gfhnv.game.skill.Skill;
import cn.gfhnv.game.system.fight.ActionSignal;
import cn.gfhnv.game.system.fight.Fight;
import cn.gfhnv.game.system.fight.TargetStrategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UniversalController {
    private List<Skill> skills = new ArrayList<>();
    private LivingThing owner;
    private ISpecialAction specialAction;
    private ActionSignal actionSignal = ActionSignal.NORMAL;
    private IInitialize iInitialize = null;

    /**
     * 目标选择策略。默认随机（与加这个字段之前的行为一致）；
     * 想改成「先打带嘲讽的」「先打血最少的」，见 {@link TargetStrategies}。
     */
    private TargetStrategy targetStrategy = TargetStrategies.random();

    //不会使用物品.懒得写.
    public UniversalController(UniversalController universalController, LivingThing owner) {
        this.owner = owner;
        if (universalController.getiInitialize() != null)
            this.setiInitialize(universalController.getiInitialize().copy());
        for (Skill skill : universalController.skills) {
            if (universalController.skills != null) {
                skills.add(skill.copy());

            }
        }
        // 策略本身是无状态的（或由内容自己保证可共享），直接沿用
        this.targetStrategy = universalController.getTargetStrategy();

    }

    public UniversalController(List<Skill> skills, LivingThing owner) {
        this.owner = owner;
        for (Skill skill : skills) {
            this.skills.add(skill.copy());
        }
    }

    public TargetStrategy getTargetStrategy() {
        return targetStrategy;
    }

    /**
     * 设置目标选择策略。
     *
     * @param targetStrategy 策略；传 {@code null} 时回到默认的随机策略
     */
    public void setTargetStrategy(TargetStrategy targetStrategy) {
        this.targetStrategy = targetStrategy == null ? TargetStrategies.random() : targetStrategy;
    }

    public IInitialize getiInitialize() {
        return iInitialize;
    }

    public void setiInitialize(IInitialize iInitialize) {
        this.iInitialize = iInitialize;
    }

    public ActionSignal getActionSignal() {
        return actionSignal;
    }

    public void setActionSignal(ActionSignal actionSignal) {
        this.actionSignal = actionSignal;
    }

    public ISpecialAction getSpecialAction() {
        return specialAction;
    }

    public void setSpecialAction(ISpecialAction specialAction) {
        this.specialAction = specialAction;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        List<Skill> skills1 = new ArrayList<>();
        for (Skill skill : skills) {
            skills1.add(skill.copy());
        }
        this.skills = skills1;
    }

    public LivingThing getOwner() {
        return owner;
    }

    public void setOwner(LivingThing owner) {
        this.owner = owner;
    }


    public void act(Fight fight) {
        List<Skill> approachableSkill = new ArrayList<>();
        for (Skill skill : getSkills()) {
            if (skill.canUse(fight, getOwner()) && skill.canUse(fight, getOwner(), null)) {
                approachableSkill.add(skill);
            }
        }
        if (approachableSkill.isEmpty()) {
            System.out.println(getOwner().getName() + "没行动");
            return;
        }
        Random rand = new Random();
        Skill selectedSkill = approachableSkill.get(rand.nextInt(approachableSkill.size()));
        if (selectedSkill.getAims() == 0) {
            selectedSkill.use(fight, owner);
            return;
        }
        List<LivingThing> targets = resolveTargets(fight, selectedSkill);
        selectedSkill.use(fight, getOwner(), targets);
        EventBus.post(new SelectTargetEvent(getOwner(), targets, fight));
    }

    /**
     * 解析一个技能这次要作用的目标（按技能的 {@code aims} 与 {@link #getTargetStrategy()} 决定）。
     * <p>
     * 抽出来给子类复用：{@code aims=0}（自身）返回空列表，{@code aims=-1}（全体）返回全部候选，
     * 正数则按策略排好序后取前 N 个。<b>返回的是新列表</b>，不是候选列表的视图。
     *
     * @param fight 当前战斗
     * @param skill 正在释放的技能
     * @return 目标列表（可能为空）
     */
    protected List<LivingThing> resolveTargets(Fight fight, Skill skill) {
        if (skill == null || skill.getAims() == 0) {
            return new ArrayList<>();
        }
        List<LivingThing> candidates = new ArrayList<>(skill.isForEnemies()
                ? fight.getOpponentList(getOwner())
                : fight.getOwnList(getOwner()));
        if (skill.getAims() < 0) {
            return candidates;
        }
        List<LivingThing> ordered = getTargetStrategy().order(fight, getOwner(), skill, new ArrayList<>(candidates));
        if (ordered == null || ordered.isEmpty()) {
            return new ArrayList<>();
        }
        int aimCount = Math.min(skill.getAims(), ordered.size());
        return new ArrayList<>(ordered.subList(0, aimCount));
    }

    public void useItem(Fight fight) {


    }
}
