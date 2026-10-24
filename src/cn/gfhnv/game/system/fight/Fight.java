package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;
import cn.gfhnv.game.world.World;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class Fight {
    private List<LivingThing> enemiesList;
    private List<Item> rewardList;
    private List<LivingThing> fighterList;
    private List<LivingThing> allEntities = new ArrayList<LivingThing>();

    public Fight(List<LivingThing> enemiesList, List<Item> rewardList, List<LivingThing> fighterList) {
        this.enemiesList = enemiesList;
        this.rewardList = rewardList;
        this.fighterList = fighterList;
        this.allEntities.addAll(enemiesList);
        this.allEntities.addAll(fighterList);
    }

    public List<LivingThing> getOpponentList(LivingThing user) {
        if (getEnemiesList().contains(user)) {
            return new ArrayList<>(fighterList);
        } else return new ArrayList<>(enemiesList);
    }

    public List<LivingThing> getOwnList(LivingThing user) {
        if (getEnemiesList().contains(user)) {
            return new ArrayList<>(enemiesList);
        } else return new ArrayList<>(fighterList);
    }

    /**
     * 判断一个实体是否属于<b>我方</b>（{@link #getFighterList()}）。
     * <p>
     * 这是"我方/敌方"的<b>唯一判据</b>：{@link #sideNameOf(LivingThing)} 与回合循环里的
     * 命令系统 {@code @s} 跟随（{@code FightTurnPastListener}）都走它 ——
     * 别在别处再 {@code contains} 一遍，两套口径迟早会漂。
     *
     * @param entity 实体；{@code null} 时返回 {@code false}
     * @return 在我方阵营里则为 {@code true}
     */
    public boolean isOurSide(LivingThing entity) {
        return entity != null && getFighterList() != null && getFighterList().contains(entity);
    }

    /**
     * 判断一个实体在这局战斗里属于哪一边 —— <b>用词与回合头完全一致</b>（{@code 我方}/{@code 敌方}）。
     * <p>
     * 回合头、攻击行、侵蚀行等所有"要标明对象来源"的输出都共用这一个方法，
     * 免得各处自己判、判出两套口径。
     * <p>
     * 判据只有一条：在不在 {@link #getFighterList()} 里 —— 在就是我方（见 {@link #isOurSide(LivingThing)}）。
     * 注意<b>两个阵营列表都没进去的实体也会被算成敌方</b>（{@link #getOpponentList(LivingThing)}
     * 的口径同样如此），所以召唤物必须加进召唤者那一侧，否则会被当成敌人打（见 TIPS_FOR_LLM §5.8）。
     *
     * @param entity 实体；{@code null} 时返回空串
     * @return {@code 我方} / {@code 敌方} / {@code ""}（{@code entity} 为 {@code null}）
     */
    public String sideNameOf(LivingThing entity) {
        if (entity == null) {
            return "";
        }
        return isOurSide(entity) ? "我方" : "敌方";
    }

    @Override
    public String toString() {
        return "Fight{" +
                "enemiesList=" + enemiesList +
                ", rewardList=" + rewardList +
                ", fighterList=" + fighterList +
                ", allEntities=" + allEntities +
                '}';
    }

    public List<LivingThing> getAllEntities() {
        return allEntities;
    }

    public void setAllEntities(List<LivingThing> allEntities) {
        this.allEntities = allEntities;
    }

    /**
     * 把<b>已经倒下</b>的生物从「全体实体」列表里摘掉，返回摘了几个。
     * <p>
     * {@code /list} 和 {@code @e} 这类选择器读的就是这条列表 —— 不清的话，打久了
     * （尤其盗火行者那种一直召唤容器的 BOSS）尸体越堆越多，{@code /list} 全是死人和残破容器。
     * <p>
     * ⚠️ <b>只清这一条</b>：{@link #getFighterList()} / {@link #getEnemiesList()} <b>不动</b> ——
     * 那两个是战斗逻辑（技能打谁、谁还算某个阵营）在读，半路摘人会改变现有行为
     * （而且很多招式在"一次打多目标"的循环里遍历它们，中途改会直接 ConcurrentModification）。
     * 回合循环每回合调一次这个方法（紧挨 {@code TurnManager#removeTheDeath()}），
     * 所以永远不会落在某个技能的多目标循环中间。
     *
     * @return 摘掉的数量
     */
    public int removeDeadFromAllEntities() {
        int before = allEntities.size();
        allEntities.removeIf(thing -> thing != null && !thing.isAlive());
        return before - allEntities.size();
    }

    /**
     * 把一名生物加入己方阵营。
     * <p>
     * 入列前会把 id 补成注册表里的完整 id：技能召唤出来的生物是直接 {@code new} 的，
     * 构造器里只有短 id（见 {@link World#applyRegisteredId(Thing)}）。
     *
     * @param fighter 己方生物
     */
    public void addFighter(LivingThing fighter) {
        World.applyRegisteredId(fighter);
        fighterList.add(fighter);
        allEntities.add(fighter);
        TurnManager.getTurns().add(new TurnEntry(fighter, BigDecimal.valueOf(10000)
                .divide(BigDecimal.valueOf(fighter.getSpeed()), 10, RoundingMode.HALF_UP), TurnManager.getPresentTime()));
    }

    /**
     * 把一名生物加入敌方阵营（同上，入列前补全 id）。
     *
     * @param e 敌方生物
     */
    public void addEnemy(LivingThing e) {
        World.applyRegisteredId(e);
        enemiesList.add(e);
        allEntities.add(e);
        TurnManager.getTurns().add(new TurnEntry(e, BigDecimal.valueOf(10000)
                .divide(BigDecimal.valueOf(e.getSpeed()), 10, RoundingMode.HALF_UP), TurnManager.getPresentTime()));
    }

    public List<LivingThing> getEnemiesList() {
        return enemiesList;
    }

    public void setEnemiesList(List<LivingThing> enemiesList) {
        this.enemiesList = enemiesList;
    }

    public List<Item> getRewardList() {
        return rewardList;
    }

    public void setRewardList(List<Item> rewardList) {
        this.rewardList = rewardList;
    }

    public List<LivingThing> getFighterList() {
        return fighterList;
    }

    public void setFighterList(List<LivingThing> fighterList) {
        this.fighterList = fighterList;
    }
}
