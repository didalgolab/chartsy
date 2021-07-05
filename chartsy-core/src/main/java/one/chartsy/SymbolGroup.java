package one.chartsy;

public record SymbolGroup(String name) {
    public static SymbolGroup BASE = new SymbolGroup("BASE");

    public boolean isBase() {
        return name().equals(BASE.name());
    }
}
