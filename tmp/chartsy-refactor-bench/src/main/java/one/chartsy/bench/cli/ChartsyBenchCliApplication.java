package one.chartsy.bench.cli;

public final class ChartsyBenchCliApplication {

    private ChartsyBenchCliApplication() {
    }

    public static void main(String[] args) {
        System.exit(new ChartsyBenchCli(System.out, System.err).run(args));
    }
}
