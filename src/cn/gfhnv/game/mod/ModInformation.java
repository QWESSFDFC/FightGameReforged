package cn.gfhnv.game.mod;

public class ModInformation {
    private String name;
    private String author;
    private String description;
    private String mainClass;
    private String version;

    public ModInformation(String name, String author, String description, String mainClass, String version) {
        this.name = name;
        this.author = author;
        this.description = description;
        this.mainClass = mainClass;
        this.version = version;
    }

    @Override
    public String toString() {
        return String.format("ModInfo{name='%s', author='%s', version='%s', mainClass='%s'}",
                name, author, version, mainClass);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
