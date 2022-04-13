package one.chartsy.kernel;

import org.openide.util.Cancellable;

public class NonInteractiveFrontEnd implements FrontEndInterface {

    @Override
    public ProgressHandle createProgressHandle(String displayName, Cancellable allowToCancel) {
        return new ConsoleProgressHandle(displayName, allowToCancel);
    }

    @Override
    public void foregroundAction(String title, Runnable work) {
        work.run();
    }

    public static class ConsoleProgressHandle implements ProgressHandle {
        private final String displayName;
        private String startMessageFormat = "%1$s: Work started";
        private String progressMessageFormat = "%1$s: %2$s: Progress %3$s";
        private String finishMessageFormat = "%1$s: Work done";
        private int workTotal;

        public ConsoleProgressHandle(String displayName, Cancellable allowToCancel) {
            this.displayName = displayName;
        }

        protected ConsoleProgressHandle(String displayName, String startMessageFormat, String progressMessageFormat, String finishMessageFormat) {
            this.displayName = displayName;
            this.startMessageFormat = startMessageFormat;
            this.progressMessageFormat = progressMessageFormat;
            this.finishMessageFormat = finishMessageFormat;
        }

        protected void println(String text) {
            System.out.println(text);
        }

        @Override
        public void start(int workTotal) {
            this.workTotal = workTotal;
            println(String.format(startMessageFormat, displayName, workTotal));
        }

        @Override
        public void progress(String message, int workDone) {
            int percentDone = 100 * workTotal / workDone;
            println(String.format(progressMessageFormat, displayName, message, percentDone, workDone));
        }

        @Override
        public void finish() {
            println(String.format(finishMessageFormat, displayName));
        }
    }

    public static class MutedProgressHandle implements ProgressHandle {
        @Override public void start(int workLeft) { }
        @Override public void progress(String message, int workDone) { }
        @Override public void finish() { }
    }
}
