package one.chartsy.data.provider.file;

import one.chartsy.Candle;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Stream;

public class StooqFlatFileItemReader {

    public static void main(String[] args) throws IOException {
        FileSystem fs = FileSystems.newFileSystem(Path.of("C:/Users/Mariusz/Downloads/d_pl_txt(1).zip"));

        Stream<Path> files = Files.list(fs.getPath("data/daily/pl/wse stocks"));
        for (Path file : files.toList()) {
            //System.out.println(file);
            SimpleCandleLineMapper lineMapper = new SimpleCandleLineMapper(',', Arrays.asList("SKIP","SKIP","DATE","SKIP","OPEN","HIGH","LOW","CLOSE","VOLUME"));
            lineMapper.setDateFormat(DateTimeFormatter.ofPattern("yyyyMMdd"));

            FlatFileItemReader<Candle> itemReader = new FlatFileItemReader<>();
            itemReader.setLineMapper(lineMapper);
            itemReader.setLinesToSkip(1);
            itemReader.setInputStreamSource(() -> Files.newInputStream(file));

            itemReader.open();
            Candle c, first = null, last = null;
            int count = 0;
            while ((c = itemReader.read()) != null) {
                //System.out.println(c);
                if (first == null)
                    first = c;
                last = c;
                count++;
            }
            itemReader.close();

            //break;
            System.out.println(file.getFileName() + "\t" + count + "\t" + last + "\t" + first);
        }
    }
}
