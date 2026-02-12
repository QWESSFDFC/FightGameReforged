package cn.gfhnv.game.mod;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

public class JavaSourceCode extends SimpleJavaFileObject {
    private final String content;
    private final String name;

    public JavaSourceCode(String name, String content) {
        super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.content = content;
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String getName() {
        return name;
    }


    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }
}