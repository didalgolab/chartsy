package one.chartsy.data.provider.file;

public interface LineMapperType<T> {

    LineMapper<T> createLineMapper(ExecutionContext context);

}
