package cn.gfhnv.game.system.fight;

import cn.gfhnv.game.entity.Player;
import cn.gfhnv.game.event.Event;

public class FightEndEvent extends Event {
    private boolean isPlayerWin;
    private Fight fight;

    public FightEndEvent(boolean isPlayerWin, Fight fight) {
        this.isPlayerWin = isPlayerWin;
        this.fight = fight;
    }

    public boolean isPlayerWin() {
        return isPlayerWin;
    }

    public void setPlayerWin(boolean playerWin) {
        isPlayerWin = playerWin;
    }

    public Fight getFight() {
        return fight;
    }

    public void setFight(Fight fight) {
        this.fight = fight;
    }
}
