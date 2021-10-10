package one.chartsy;

public class InvalidTimeFrameException extends RuntimeException {

    public InvalidTimeFrameException() {}

    public InvalidTimeFrameException(String message) {
        super(message);
    }
}
