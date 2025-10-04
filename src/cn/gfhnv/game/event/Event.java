package cn.gfhnv.game.event;

public class Event {
    private boolean isCanceled=false;

    public boolean isCanceled() {
        return isCanceled;

    }
    public void setCanceled(boolean canceled) {
        isCanceled = canceled;
    }
}
