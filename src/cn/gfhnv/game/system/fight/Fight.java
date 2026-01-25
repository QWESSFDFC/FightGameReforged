package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.Entity;
import cn.gfhnv.game.item.Item;

import java.util.List;

public class Fight {
    private List<? extends Entity> enemiesList;
    private List<? extends Item> rewardList;
    private List<? extends Entity> fighterList;

    public Fight(List<? extends Entity> enemiesList, List<? extends Item> rewardList, List<? extends Entity> fighterList) {
        this.enemiesList = enemiesList;
        this.rewardList = rewardList;
        this.fighterList = fighterList;
    }

    public List<? extends Entity> getEnemiesList() {
        return enemiesList;
    }

    public void setEnemiesList(List<? extends Entity> enemiesList) {
        this.enemiesList = enemiesList;
    }

    public List<? extends Item> getRewardList() {
        return rewardList;
    }

    public void setRewardList(List<? extends Item> rewardList) {
        this.rewardList = rewardList;
    }

    public List<? extends Entity> getFighterList() {
        return fighterList;
    }

    public void setFighterList(List<? extends Entity> fighterList) {
        this.fighterList = fighterList;
    }
}
