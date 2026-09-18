package cn.gfhnv.game.system.command;

/**
 * 命令参数类型：负责把输入字符串中的「一段」解析成具体的 Java 值。
 * <p>
 * 这是本命令系统与《我的世界》Java 版 Brigadier 对齐的关键抽象：
 * <b>解析职责下沉到参数类型自己身上</b>，命令类只负责声明「我要一个整数 / 一个实体选择器」，
 * 不用再手写字符串切分。
 * <p>
 * 常用类型请优先使用静态工厂（都在本包内）：
 * <ul>
 *     <li>{@link WordArgumentType#word()} —— 一个不含空白的词</li>
 *     <li>{@link StringArgumentType#string()} / {@link StringArgumentType#greedyString()} —— 带引号字符串 / 吃掉整行</li>
 *     <li>{@link IntegerArgumentType#integer()}、{@link LongArgumentType#longArg()}、{@link DoubleArgumentType#doubleArg()}</li>
 *     <li>{@link BoolArgumentType#bool()} —— true / false</li>
 *     <li>{@link EntityArgumentType#entity()} / {@link EntityArgumentType#entities()} —— 实体选择器</li>
 * </ul>
 * <p>
 * 本接口是函数式接口，因此也可以直接用 lambda 定义一次性参数类型：
 * <pre>{@code
 * .argument("方向", reader -> {
 *     String word = reader.readWord();
 *     if (!word.equals("上") && !word.equals("下")) {
 *         throw CommandSyntaxException.at(reader, "方向只能是「上」或「下」");
 *     }
 *     return word;
 * })
 * }</pre>
 *
 * @param <T> 解析结果的类型
 * @author AI（DeepSeek）生成
 */
@FunctionalInterface
public interface ArgumentType<T> {

    /**
     * 从读取器当前位置解析出一个值，并把光标推进到该值的末尾。
     *
     * @param reader 输入读取器
     * @return 解析结果，不应为 {@code null}
     * @throws CommandSyntaxException 输入不合法时抛出（应带定位信息）
     */
    T parse(StringReader reader) throws CommandSyntaxException;
}
