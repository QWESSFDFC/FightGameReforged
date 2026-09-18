package cn.gfhnv.game.officialStuff.customCommands;

import cn.gfhnv.game.system.command.Command;
import cn.gfhnv.game.system.command.CommandParameter;
import cn.gfhnv.game.system.command.CommandParameterType;
import cn.gfhnv.game.system.command.ParameterEntry;

public class KillCommand extends Command {
    @Override
    public CommandParameter resolveInput(String input) {
        return super.resolveInput(input);
    }

    @Override
    public void comeToEffect(CommandParameter parameter) {
        if (parameter.getParameters().isEmpty()) return;
        if (parameter.getParameters().size()!=1) {
            System.out.println("参数不对,只能有一个参数.");
            return;
        }
        if (!parameter.getParameters().getFirst().getType().equals(CommandParameterType.ENTITIES)&&!parameter.getParameters().getFirst().getType().equals(CommandParameterType.ENTITY_SELECTOR) ){
            System.out.println("参数不对.参数只能是实体或者实体选择器");
        }
    }
}
