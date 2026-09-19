package cn.gfhnv.game.system.command;

import cn.gfhnv.game.system.logSystem.LogWriter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 命令调度器：命令注册表 + 解析/执行入口。
 * <p>
 * 职责与 MC 的 {@code CommandDispatcher} 对应：
 * <ol>
 *     <li>{@link #register(Command)}：把命令挂到根节点上（同名命令会合并分支，
 *     因此模组可以给已有命令追加子命令）；</li>
 *     <li>{@link #execute(String, CommandSource)}：解析并执行；失败抛
 *     {@link CommandSyntaxException}（自带定位信息）；</li>
 *     <li>{@link #getCompletionSuggestions(String)}：给输入提示（{@code Tab} 补全的雏形）。</li>
 * </ol>
 * <p>
 * 解析规则与 MC 一致：
 * <ul>
 *     <li>先把当前这个词与<b>字面量子节点</b>逐一比较（大小写不敏感），命中就往下走；</li>
 *     <li>没有字面量命中时，才尝试把它当作<b>参数</b>解析；</li>
 *     <li>输入读完时：当前节点可执行就直接执行；还有子节点则报「命令不完整」并给出用法。</li>
 * </ul>
 * 解析失败时抛出的异常<b>不会</b>被本类打印；打印统一由 {@link CommandManager} 负责，
 * 这样调度器可以被模组或测试安静地调用。
 *
 * @author AI（DeepSeek）生成
 */
public class CommandDispatcher {

    /**
     * 命令树根节点（名字为 {@code /}）。
     */
    private LiteralCommandNode root = new LiteralCommandNode("/");

    /**
     * 已注册命令：完整名字 → 根节点（用于 {@code help} 展示与查询）。
     */
    private final Map<String, CommandNode> commands = new LinkedHashMap<>();

    /**
     * 已注册命令的顺序（保持注册顺序，供 {@code help} 使用）。
     */
    private final List<String> commandNames = new ArrayList<>();

    /* ------------------------------------------------------------------
     * 注册
     * ------------------------------------------------------------------ */

    /**
     * 已注册过的命令实例（按对象身份判断）。
     * <p>
     * 用于挡住「把一个 {@link Command} 实例直接塞进自己的子命令列表」这种写法：
     * 那会让 {@code buildNode()} 无限递归，而栈溢出异常非常难排查。
     */
    private final Set<Command> registeredCommands = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * 注册一条命令。同名命令会被合并：新命令的执行体覆盖旧的，分支并进同一棵树。
     *
     * @param command 命令
     * @return 该命令在树中的根节点
     */
    public CommandNode register(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("命令不能为 null");
        }
        if (!registeredCommands.add(command)) {
            // 同一个实例注册两次是安全的（幂等），这里只记一条日志，避免重复构建出两棵子树
            LogWriter.writeLog("命令实例被重复注册，已忽略：" + command.getFullName());
            CommandNode existing = commands.get(command.getFullName());
            if (existing != null) {
                return existing;
            }
        }
        String fullName = command.getFullName();
        CommandNode node = command.build();
        List<String> path = command.getPath();
        if (path.size() > 1) {
            CommandNode parent = resolveOrCreateParent(path);
            parent.addChild(node);
        } else {
            root.addChild(node);
        }
        if (!commands.containsKey(fullName)) {
            commandNames.add(fullName);
        }
        commands.put(fullName, node);
        LogWriter.writeLog("注册命令：" + fullName);
        return node;
    }

    /**
     * 注册若干条命令。
     *
     * @param commands 命令数组
     */
    public void register(Command... commands) {
        if (commands == null) {
            return;
        }
        for (Command command : commands) {
            register(command);
        }
    }

    /**
     * 按路径找到（或建出）父节点，用于挂载多级子命令。
     *
     * @param path 路径片段（最后一段是命令自己的名字）
     * @return 父节点
     */
    private CommandNode resolveOrCreateParent(List<String> path) {
        CommandNode current = root;
        for (int i = 0; i < path.size() - 1; i++) {
            String segment = path.get(i);
            CommandNode child = current.getChild(segment);
            if (child == null) {
                LiteralCommandNode created = new LiteralCommandNode(segment);
                current.addChild(created);
                child = created;
            }
            current = child;
        }
        return current;
    }

    /* ------------------------------------------------------------------
     * 解析与执行
     * ------------------------------------------------------------------ */

    /**
     * 只做「解析」，不执行：返回应当执行的节点，参数已经写进传入的上下文。
     * <p>
     * 这是给测试、补全、以及「想先看看这条命令会走到哪」的场景准备的。
     * 它<b>不会</b>执行命令，因此不会改动任何游戏状态。
     *
     * @param input   命令文本（可带前缀）
     * @param context 用来接收解析出来的参数的上下文；可为 {@code null}（内部新建一个）
     * @return 应当执行的节点
     * @throws CommandSyntaxException 解析失败时抛出
     */
    public CommandNode parse(String input, CommandContext context) throws CommandSyntaxException {
        CommandContext effective = context == null ? new CommandContext(null) : context;
        return parseToNode(input, effective);
    }

    /**
     * 是否把解析过程打到标准输出（排查解析问题时打开）。
     */
    private static boolean debugParsing = false;

    /**
     * 诊断输出（仅在 {@link #setDebugParsing(boolean)} 打开时有效）。
     * <p>
     * 对本包外也开放，方便 {@link ArgumentCommandNode} 这类节点把自己的解析过程报出来。
     *
     * @param message 内容
     */
    public static void debugParse(String message) {
        if (debugParsing) {
            System.out.println("      [解析] " + message);
        }
    }

    /**
     * 打开/关闭解析过程诊断输出。
     *
     * @param value 是否输出
     */
    public static void setDebugParsing(boolean value) {
        debugParsing = value;
    }

    /**
     * 诊断输出（包内简写）。
     *
     * @param message 内容
     */
    private static void debug(String message) {
        debugParse(message);
    }

    /**
     * 解析的核心实现：把输入解析成节点，参数写进 {@code context}。
     *
     * @param input   命令文本
     * @param context 接收参数的上下文
     * @return 应当执行的节点
     * @throws CommandSyntaxException 解析失败时抛出
     */
    private CommandNode parseToNode(String input, CommandContext context) throws CommandSyntaxException {
        if (input == null || input.isBlank()) {
            throw CommandSyntaxException.create("命令不能为空");
        }
        String command = stripPrefix(input.trim());
        StringReader reader = new StringReader(command);
        if (!reader.canRead()) {
            throw CommandSyntaxException.create("命令不能为空");
        }
        // visited 必须传空集合：parseNodes() 会自己把访问到的节点加进去，
        // 预先塞入任何节点都会与内部那次 add 相撞，从而误报「检测到环」。
        CommandNode node = parseNodes(root, reader, context, new LinkedHashSet<>(), 0);
        if (node == null) {
            String firstWord = command.split("\\s+", 2)[0];
            List<String> candidates = getCompletionSuggestions(firstWord);
            String hint = candidates.isEmpty() ? "" : "。你是不是想输入：" + String.join("、", candidates);
            throw CommandSyntaxException.create("未知的命令：" + firstWord + hint);
        }
        return node;
    }

    /**
     * 解析并执行一条命令。
     *
     * @param input  用户输入（<b>不含</b>前缀 {@code /} 或 {@code #}）
     * @param source 命令来源
     * @return 影响到的对象数量
     * @throws CommandSyntaxException 解析或执行失败时抛出
     */
    public int execute(String input, CommandSource source) throws CommandSyntaxException {
        CommandSource effectiveSource = source == null ? CommandSource.console() : source;
        CommandNode node = parseToNode(input, effectiveSource);

        if (!node.getRequires().test(effectiveSource)) {
            throw CommandSyntaxException.create("当前无法使用该命令：" + node.getFullUsage());
        }
        if (node.getExecutor() == null) {
            throw CommandSyntaxException.create("命令不完整：" + node.getFullUsage());
        }
        // 参数已经在 parseToNode 里直接写进 effectiveSource 了（source 与 context 是同一个对象），
        // 这里直接执行即可。
        return node.getExecutor().run(effectiveSource, effectiveSource);
    }

    /**
     * 递归下降解析。
     * <p>
     * 走错分支时会回滚「参数表」与「读取器位置」——回滚依靠 {@code fork}（复制出来的读取器）
     * 与 {@code baseArguments}（参数表快照）完成。
     * <p>
     * <b>参数表只有一个来源</b>：{@code context.getArguments()}，不要另外再传一个 Map 进来。
     * 若那个 Map 和 {@code context.getArguments()} 是同一个对象，
     * 「先 clear 再从 context 拷回来」会把刚解析出来的参数全部抹掉，
     * 表现为「参数节点解析成功但参数表为空」。
     * <p>
     * {@code visited} 只用于「同一个节点在一次解析里被重复进入」这种环的检测；
     * 调用方必须传<b>空集合</b>，本方法会自己把当前节点加进去。
     * 即使真的出现环，{@code depth} 上限也会兜底，不会无限递归。
     *
     * @param node    当前节点
     * @param reader  输入读取器
     * @param context 命令上下文（参数直接写进它）
     * @param visited 已访问节点集合（防环，调用方传空集合）
     * @param depth   递归深度（兜底防死循环）
     * @return 应当执行的节点；没有匹配到任何可执行分支时返回 {@code null}
     * @throws CommandSyntaxException 输入非法时抛出
     */
    private CommandNode parseNodes(CommandNode node, StringReader reader, CommandContext context,
                                   Set<CommandNode> visited, int depth) throws CommandSyntaxException {
        if (depth > 64) {
            throw CommandSyntaxException.at(reader, "命令嵌套层级过深（超过 64 层）");
        }
        if (!visited.add(node)) {
            throw CommandSyntaxException.at(reader, "命令树里检测到环，节点：" + node.getName());
        }

        CommandNode current = node;
        while (true) {
            if (!reader.canRead()) {
                if (current.isExecutable()) {
                    return current;
                }
                throw CommandSyntaxException.create("命令不完整：" + current.getFullUsage());
            }

            String word = reader.peekWord();

            // 1) 先试字面量子节点
            // 判定用 isLiteralNode()（能力判定）而不是 instanceof LiteralCommandNode：
            // 构建器 ArgumentBuilder 建出来的分支同样要参与字面量匹配。
            List<CommandNode> literalCandidates = new ArrayList<>();
            for (CommandNode child : current.getChildren()) {
                if (child.isLiteralNode() && matchesIgnoreCase(child.getName(), word)) {
                    literalCandidates.add(child);
                }
            }
            debug("当前节点=" + current.getName() + " 词=" + word
                    + " 子节点=" + current.getChildrenNames()
                    + " 字面量命中=" + literalCandidates.size());

            if (!literalCandidates.isEmpty()) {
                Map<String, Object> baseArguments = new LinkedHashMap<>(context.getArguments());
                for (CommandNode child : literalCandidates) {
                    StringReader fork = new StringReader(reader.getString());
                    fork.setCursor(reader.getCursor());
                    // 复制上下文，这样某个候选分支失败时不会污染真正的上下文
                    CommandContext forkContext = new CommandContext(context.getSender());
                    forkContext.getArguments().putAll(baseArguments);
                    Set<CommandNode> forkVisited = new LinkedHashSet<>(visited);
                    try {
                        child.parse(fork, forkContext);
                        CommandNode result = parseNodes(child, fork, forkContext, forkVisited, depth + 1);
                        if (result != null) {
                            // 成功：把读取器位置与参数表同步回调用方
                            reader.setCursor(fork.getCursor());
                            context.getArguments().clear();
                            context.getArguments().putAll(forkContext.getArguments());
                            return result;
                        }
                    } catch (CommandSyntaxException ignored) {
                        // 该分支走不通：继续尝试下一个候选
                    }
                }
                // 有字面量匹配但都走不通：把「第一个候选自己」的失败原因原样报出来，
                // 比笼统的一句「无法继续解析」有用得多（例如 /help 少写了参数名 → 命令不完整）。
                throw parseFailureOf(literalCandidates.get(0), reader, baseArguments, visited, depth);
            }

            // 2) 再试参数子节点
            CommandNode argumentMatch = null;
            for (CommandNode child : current.getChildren()) {
                if (child.isLiteralNode()) {
                    continue;
                }
                Map<String, Object> snapshot = new LinkedHashMap<>(context.getArguments());
                StringReader fork = new StringReader(reader.getString());
                fork.setCursor(reader.getCursor());
                try {
                    child.parse(fork, context);
                    reader.setCursor(fork.getCursor());
                    argumentMatch = child;
                    debug("在 " + current.getName() + " 下用参数「" + child.getName() + "」吃掉了「"
                            + fork.getRemaining() + "」之前的内容，当前参数表=" + context.getArguments());
                    break;
                } catch (CommandSyntaxException e) {
                    // 用 debug 开关输出：这类「试了哪个参数节点、为什么失败」的信息每次都写日志
                    // 会把 latest.log 刷满噪音。排查中文选择器这类问题时把 setDebugParsing(true) 打开。
                    debug("在 " + current.getName() + " 下试参数「" + child.getName() + "」失败："
                            + e.getRawMessage() + "，剩余输入=「" + fork.getRemaining() + "」");
                    context.getArguments().clear();
                    context.getArguments().putAll(snapshot);
                }
            }
            if (argumentMatch == null) {
                return null;
            }
            current = argumentMatch;
        }
    }

    /**
     * 让某个字面量分支自己再解析一次，并把它的失败原因作为异常返回。
     * <p>
     * 用于「字面量命中但后续解析不下去」时给出<b>具体</b>的报错，
     * 例如 {@code /help} 会报「命令不完整：/help <命令名>」而不是笼统的解析失败。
     *
     * @param child         要重试的字面量节点
     * @param reader        原读取器
     * @param baseArguments 进入该分支时的参数表快照
     * @param visited       已访问节点集合
     * @param depth         当前递归深度
     * @return 失败异常（由调用方抛出，保证一定有返回值以便编译器识别 {@code throw}）
     */
    private CommandSyntaxException parseFailureOf(CommandNode child, StringReader reader,
                                                  Map<String, Object> baseArguments,
                                                  Set<CommandNode> visited, int depth) {
        StringReader fork = new StringReader(reader.getString());
        fork.setCursor(reader.getCursor());
        CommandContext forkContext = new CommandContext(null);
        forkContext.getArguments().putAll(baseArguments);
        try {
            child.parse(fork, forkContext);
            CommandNode result = parseNodes(child, fork, forkContext, new LinkedHashSet<>(visited), depth + 1);
            return CommandSyntaxException.at(fork, "命令无法继续解析，剩余输入「" + fork.getRemaining() + "」"
                    + (result == null ? "" : "（解析到了 " + result.getFullUsage() + "）"));
        } catch (CommandSyntaxException e) {
            return e;
        }
    }

    /**
     * 大小写不敏感比较。
     *
     * @param a 文本一
     * @param b 文本二
     * @return 是否相等
     */
    private static boolean matchesIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    /**
     * 去掉命令前缀（{@code /} 或 {@code #}）与多余空白。
     *
     * @param input 原始输入
     * @return 纯命令文本
     */
    public static String stripPrefix(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (!trimmed.isEmpty() && (trimmed.charAt(0) == '/' || trimmed.charAt(0) == '#')) {
            return trimmed.substring(1).trim();
        }
        return trimmed;
    }

    /* ------------------------------------------------------------------
     * 补全与查询
     * ------------------------------------------------------------------ */

    /**
     * 取补全建议（{@code Tab} 补全的雏形）。
     * <p>
     * 支持补全第一层命令名与当前节点下的字面量子节点；处在参数位置时会给出
     * {@code <参数名>} 形式的提示。
     *
     * @param input 已输入的内容（可以不含前缀）
     * @return 建议列表（按字典序）
     */
    public List<String> getCompletionSuggestions(String input) {
        if (input == null || input.isBlank()) {
            return new ArrayList<>(commandNames);
        }
        // 注意：尾随空白必须看【原始输入】。stripPrefix() 内部会 trim()（那是给解析用的，
        // "kill " 必须变成 "kill"），所以对 stripPrefix 的结果调 endsWith(" ") 恒为 false，
        // 补全就永远不知道该提示「下一步」了。
        boolean endsWithSpace = endsWithWhitespace(input);

        String command = stripPrefix(input);
        List<String> parts = new ArrayList<>();
        for (String part : command.split("\\s+")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return new ArrayList<>(commandNames);
        }

        CommandNode current = root.getChild(parts.get(0));
        if (current == null) {
            current = findLiteralIgnoreCase(root, parts.get(0));
        }
        if (current == null) {
            TreeSet<String> suggestions = new TreeSet<>();
            for (String name : commandNames) {
                if (name.toLowerCase().startsWith(parts.get(0).toLowerCase())) {
                    suggestions.add(name);
                }
            }
            return new ArrayList<>(suggestions);
        }

        int index = 1;
        while (index < parts.size()) {
            CommandNode child = current.getChild(parts.get(index));
            if (child == null) {
                child = findLiteralIgnoreCase(current, parts.get(index));
            }
            if (child == null) {
                // 这个词不是字面量（多半是个参数值，如 @s / 100）：只要当前节点有参数子节点，
                // 就把它当成参数值吃掉，继续往下走，这样 "hurt @s " 才能提示出 <数值>。
                child = firstArgumentChild(current);
            }
            if (child == null) {
                break;
            }
            current = child;
            index++;
        }

        TreeSet<String> suggestions = new TreeSet<>();
        if (index < parts.size()) {
            String partial = parts.get(index).toLowerCase();
            for (CommandNode child : current.getChildren()) {
                if (child.isLiteralNode() && child.getName().toLowerCase().startsWith(partial)) {
                    suggestions.add(child.getName());
                }
            }
            return new ArrayList<>(suggestions);
        }
        if (endsWithSpace) {
            // 处于「参数位置」：参数分支给出 <参数名>，字面量分支给出字面量
            for (CommandNode child : current.getChildren()) {
                suggestions.add(child.isLiteralNode() ? child.getName() : "<" + child.getName() + ">");
            }
        }
        return new ArrayList<>(suggestions);
    }

    /**
     * 判断输入是否以空白结尾（尾随空白表示「玩家准备输入下一个词」）。
     *
     * @param input 原始输入
     * @return 是否以空白结尾
     */
    private static boolean endsWithWhitespace(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        char last = input.charAt(input.length() - 1);
        return last == ' ' || last == '\t';
    }

    /**
     * 大小写不敏感地找一个字面量子节点。
     *
     * @param parent 父节点
     * @param name   字面量文本
     * @return 子节点；找不到返回 {@code null}
     */
    private static CommandNode findLiteralIgnoreCase(CommandNode parent, String name) {
        for (CommandNode child : parent.getChildren()) {
            if (child.isLiteralNode() && child.getName().equalsIgnoreCase(name)) {
                return child;
            }
        }
        return null;
    }

    /**
     * 取第一个可用的参数子节点（按注册顺序）。
     * <p>
     * 补全时用它把「参数值」这一格吃掉，从而能继续往下一层走：
     * {@code "hurt @s "} → 吃掉 {@code @s} → 到了「数值」那层 → 提示 {@code <数值>}。
     *
     * @param parent 父节点
     * @return 参数子节点；没有则返回 {@code null}
     */
    private static CommandNode firstArgumentChild(CommandNode parent) {
        for (CommandNode child : parent.getChildren()) {
            if (!child.isLiteralNode() && child.getRequires().test(CommandSource.console())) {
                return child;
            }
        }
        return null;
    }

    /**
     * @return 命令树根节点
     */
    public LiteralCommandNode getRoot() {
        return root;
    }

    /**
     * @return 按注册顺序排列的命令名列表
     */
    public List<String> getCommandNames() {
        return new ArrayList<>(commandNames);
    }

    /**
     * 按名字取命令节点。
     *
     * @param name 命令名
     * @return 命令节点；不存在返回 {@code null}
     */
    public CommandNode getCommandNode(String name) {
        return commands.get(name);
    }

    /**
     * 清空所有命令（主要给测试用）。
     * <p>
     * 根节点会被换成一个全新的空根节点，因此之前注册过的节点不会被复用。
     */
    public void clear() {
        commands.clear();
        commandNames.clear();
        registeredCommands.clear();
        root = new LiteralCommandNode("/");
    }
}
