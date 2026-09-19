package cn.gfhnv.game.system.command;

/**
 * 字面量节点：匹配一个固定的单词（如 {@code kill}、{@code next}）。
 * <p>
 * 字面量匹配是<b>大小写不敏感</b>的（{@code KILL} 与 {@code kill} 等价），
 * 但输出与提示使用注册时的原始写法。字面量节点本身可以绑定执行体，
 * 例如 {@code give hand} 这种「不需要额外参数」的分支。
 *
 * @author AI（DeepSeek）生成
 */
public class LiteralCommandNode extends CommandNode {

    /**
     * 字面量文本。
     */
    private final String literal;

    /**
     * 构造一个字面量节点。
     *
     * @param literal 字面量文本
     */
    public LiteralCommandNode(String literal) {
        if (literal == null || literal.isBlank()) {
            throw new IllegalArgumentException("字面量不能为空");
        }
        this.literal = literal;
    }

    @Override
    public String getName() {
        return literal;
    }

    @Override
    public boolean isLiteralNode() {
        return true;
    }

    /**
     * @return 字面量文本
     */
    public String getLiteral() {
        return literal;
    }

    @Override
    public void parse(StringReader reader, CommandContext context) throws CommandSyntaxException {
        reader.skipWhitespace();
        int start = reader.getCursor();
        String word = reader.readWord();
        if (!word.equalsIgnoreCase(literal)) {
            throw CommandSyntaxException.at(reader, start,
                    "此处需要「" + literal + "」，实际读到「" + word + "」");
        }
    }

    @Override
    public String getUsageText() {
        return literal;
    }
}
