package cn.gfhnv.game.system.command;

public class ParameterEntry {
    private final CommandParameterType type;
    private final Object content;

    public CommandParameterType getType() {
        return type;
    }

    public Object getContent() {
        return content;
    }

    public ParameterEntry(CommandParameterType type, Object content) {
        this.type = type;
        this.content = content;
    }
}
