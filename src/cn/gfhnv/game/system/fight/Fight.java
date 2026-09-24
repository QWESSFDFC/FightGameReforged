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
