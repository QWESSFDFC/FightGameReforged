package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.entity.LivingThing;
import cn.gfhnv.game.item.Item;

import java.util.ArrayList;
import java.util.List;

public class Fight {
    private List<LivingThing> enemiesList;
    private List<Item> rewardList;
    private List<LivingThing> fighterList;
    private List<LivingThing> allEntities=new ArrayList<LivingThing>();

    public List<LivingThing> getAllEntities() {
        return allEntities;
    }

    public void setAllEntities(List<LivingThing> allEntities) {
        this.allEntities = allEntities;
    }

    public Fight(List<LivingThing> enemiesList, List<Item> rewardList, List<LivingThing> fighterList) {
        this.enemiesList =  enemiesList;
        this.rewardList = rewardList;
        this.fighterList = fighterList;
        this.allEntities.addAll(enemiesList);
        this.allEntities.addAll(fighterList);
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
        this.fighterList =  fighterList;
    }
}
