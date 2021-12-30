package one.chartsy.kernel;

import one.chartsy.persistence.domain.model.SymbolGroupRepository;
import one.chartsy.persistence.domain.SymbolGroupData;

public class KernelLaunchExample {

    public static void main(String[] args) throws InterruptedException {
        Kernel kernel = new Kernel();
        System.out.println(kernel);
        var repo = kernel.getApplicationContext().getBean(SymbolGroupRepository.class);
        System.out.println("> " + repo);
        SymbolGroupData book = new SymbolGroupData();
        book = repo.save(book);
        System.out.println(">ID: " + book.getId());

        Thread.sleep(1000000000L);
        //SpringFactoriesLoader.loadFactories(EnableAutoConfiguration.class, null);
    }
}
