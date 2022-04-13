package one.chartsy.kernel;

public interface ProgressHandle {

    void start(int workTotal);

    void progress(String message, int workDone);

    void finish();
}
