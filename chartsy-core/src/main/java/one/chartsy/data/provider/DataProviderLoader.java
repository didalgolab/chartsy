package one.chartsy.data.provider;

@FunctionalInterface
public interface DataProviderLoader {

    DataProvider load(String descriptor);
}
