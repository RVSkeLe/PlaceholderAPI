package me.clip.placeholderapi.replacer;

public enum Closure {
    BRACKET('{', '}'),
    PERCENT('%', '%');


    public final char head, tail;

    Closure(final char head, final char tail) {
        this.head = head;
        this.tail = tail;
    }
}
