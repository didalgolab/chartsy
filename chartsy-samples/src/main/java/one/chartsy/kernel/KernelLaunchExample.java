/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.springframework.expression.ExpressionParser;
import org.springframework.scheduling.TaskScheduler;

public class KernelLaunchExample {

    public static void main(String[] args) throws InterruptedException {
        Kernel kernel = new Kernel();
        System.out.println(kernel);
        var repo = kernel.getApplicationContext().getBean(SymbolGroupRepository.class);
        System.out.println("> " + repo);
        SymbolGroupAggregateData book = new SymbolGroupAggregateData();
        book = repo.save(book);
        System.out.println(">ID: " + book.getId());
        System.out.println(kernel.getApplicationContext().getBean(ExpressionParser.class));
        System.out.println(kernel.getApplicationContext().getBean(TaskScheduler.class));

        System.out.println(kernel.getApplicationContext().getBean("Candlestick Chart"));
        System.out.println(kernel.getApplicationContext().getBean("candlestickChart"));


        System.out.println("\n\n");
        //Thread.sleep(1000000000L);
        //SpringFactoriesLoader.loadFactories(EnableAutoConfiguration.class, null);
    }
}
