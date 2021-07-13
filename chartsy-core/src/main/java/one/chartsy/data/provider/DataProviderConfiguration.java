package one.chartsy.data.provider;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

@Getter
@Setter
public class DataProviderConfiguration {
    private String type;
    private Path fileSystemPath;

}
