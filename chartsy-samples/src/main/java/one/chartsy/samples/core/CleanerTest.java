/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.core;

import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CleanerTest {

    private static final Cleaner cleaner = Cleaner.create();

    public static void main(String[] args) throws IOException {
        Cleaner.Cleanable cc = test();

        System.gc();
        try {
            Thread.sleep(1L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static Cleaner.Cleanable test() throws IOException {
//        List<Object> objs = new LinkedList<>();
//        for (int i = 0; i < 200; i++) {
//            FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
//                    .newDataProvider(Path.of("C:/Downloads/d_pl_txt(4).zip"));
//
//            objs.add(dataProvider);
//            System.out.println(i + ".");
//        }
//        try {
//            synchronized (objs) {
//                objs.wait();
//            }
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Downloads/d_pl_txt(4).zip"));
        FileSystem fileSystem = dataProvider.getFileSystem();

        return cleaner.register(dataProvider, () -> {
            try {
                fileSystem.close();
                System.out.println("*** CLEANDED ***");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
//        return null;
    }
}
