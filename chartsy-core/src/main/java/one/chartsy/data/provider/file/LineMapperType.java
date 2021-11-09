package one.chartsy.data.provider.file;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.LineMapper;

public interface LineMapperType<T> {

    LineMapper<T> createLineMapper(ExecutionContext context);

}
