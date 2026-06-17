package cn.gfhnv.game.system.thinkingSystem;

import java.util.Objects;

public class Tag {
    private final TagTypes tagType;
    private final double priority;

    private Tag(double priority, TagTypes tagType) {
        this.priority = priority;
        this.tagType = tagType;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return Double.compare(getPriority(), tag.getPriority()) == 0 && getTagType() == tag.getTagType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTagType(), getPriority());
    }

    public double getPriority() {
        return priority;
    }

    public TagTypes getTagType() {
        return tagType;
    }
}
