package cn.gfhnv.game.system.command;

/**
 * 「字面量」参数类型：解析结果就是给定的那个词。
 * <p>
 * 一般不需要直接使用它：写成 {@code .literal("kill")} 时，
 * {@link ArgumentBuilder} 内部就会为这个分支建一个字面量节点。
 * 只有在「同一个词要在多个位置复用、并且希望从上下文里按名字取出」时才需要显式创建，
 * 例如 {@code .argument("模式", LiteralArgumentType.literal("困难"))}。
 *
 * @author AI（DeepSeek）生成
 */
public class LiteralArgumentType implements ArgumentType<String> {

    /**
     * 期望的字面量。
     */
    private final String literal;

    /**
     * 构造一个字面量参数类型。
     *
     * @param literal 期望的字面量
     */
    public LiteralArgumentType(String literal) {
        if (literal == null || literal.isBlank()) {
            throw new IllegalArgumentException("字面量不能为空");
        }
        this.literal = literal;
    }

    /**
     * 静态工厂。
     *
     * @param literal 期望的字面量
     * @return 参数类型实例
     */
    public static LiteralArgumentType literal(String literal) {
        return new LiteralArgumentType(literal);
    }

    /**
     * @return 期望的字面量
     */
    public String getLiteral() {
        return literal;
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        reader.skipWhitespace();
        int start = reader.getCursor();
        String word = reader.readWord();
        if (!word.equals(literal)) {
            throw CommandSyntaxException.at(reader, start,
                    "此处需要字面量「" + literal + "」，实际读到「" + word + "」");
        }
        return literal;
    }

    @Override
    public String toString() {
        return literal;
    }
}
