package one.chartsy.data.provider.file;

import one.chartsy.Candle;
import one.chartsy.data.provider.DataProviderConfiguration;
import one.chartsy.data.provider.FileSystemDataProvider;
import org.openide.util.lookup.ServiceProvider;

import java.nio.file.Paths;

@ServiceProvider(service = FlatFileItemReaderFactory.class)
public class FlatFileItemReaderFactory {

//    public FlatFileItemReader<Candle> create(FlatFileFormat fileFormat) {
//        SimpleCandleLineMapper lineMapper = new SimpleCandleLineMapper(fileFormat.getFieldDelimiter(), fileFormat.getFields());
//
//        FlatFileItemReader<Candle> fileReader = new FlatFileItemReader<>(fileFormat);
//        fileReader.setLineMapper(lineMapper);
//
//    }

}
