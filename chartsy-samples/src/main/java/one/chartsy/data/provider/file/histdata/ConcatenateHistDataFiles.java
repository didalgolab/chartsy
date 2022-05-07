package one.chartsy.data.provider.file.histdata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * An example script concatenating specified multiple data files downloaded from histdata.com site into a single file.
 *
 * @author Mariusz Bernacki
 *
 */
public class ConcatenateHistDataFiles {
    static final Logger log = LogManager.getLogger(ConcatenateHistDataFiles.class);
    static final Path SOURCE_FOLDER = Path.of(System.getProperty("user.home"), "Downloads");
    static final List<String> FILES = List.of(
            "HISTDATA_COM_ASCII_EURUSD_M12000.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12001.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12002.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12003.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12004.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12005.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12006.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12007.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12008.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12009.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12010.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12011.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12012.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12013.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12014.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12015.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12016.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12017.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12018.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12019.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12020.zip",
            "HISTDATA_COM_ASCII_EURUSD_M12021.zip",
            "HISTDATA_COM_ASCII_EURUSD_M1202201.zip"
    );

    public static void main(String[] args) throws IOException {

        Path outFile = SOURCE_FOLDER.resolve("EURUSD.zip");

        long[] lineLengthMinMax = new long[] {Long.MAX_VALUE, Long.MIN_VALUE};
        try (ZipOutputStream zipDest = new ZipOutputStream(Files.newOutputStream(outFile))) {
            zipDest.putNextEntry(new ZipEntry("EURUSD.csv"));

            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(zipDest))) {
                for (String filename : FILES) {
                    try (ZipFile zip = new ZipFile(SOURCE_FOLDER.resolve(filename).toFile())) {
                        List<? extends ZipEntry> zipEntries = zip.stream()
                                .filter(entry -> entry.getName().endsWith(".csv"))
                                .toList();
                        if (zipEntries.size() != 1)
                            throw new AssertionError("ZipFile CSV entries: " + zipEntries);

                        try (BufferedReader in = new BufferedReader(new InputStreamReader(zip.getInputStream(zipEntries.get(0))))) {
                            in.lines().forEach(line -> {
                                try {
                                    line = line.replace("    ", "");

                                    out.append(line).append("\n");
                                    lineLengthMinMax[0] = Math.min(lineLengthMinMax[0], line.length());
                                    lineLengthMinMax[1] = Math.max(lineLengthMinMax[1], line.length());
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                        }
                    }
                }
            }
        }
        log.info("CSV lines min length: {}", lineLengthMinMax[0]);
        log.info("CSV lines max length: {}", lineLengthMinMax[1]);
    }
}
