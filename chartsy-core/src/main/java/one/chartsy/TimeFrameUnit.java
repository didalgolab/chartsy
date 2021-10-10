package one.chartsy;

public /*sealed*/ interface TimeFrameUnit /*permits StandardTimeFrameUnit, TimeFrameUnit.Custom*/ {

    boolean isEventBased();

    boolean isPriceBased();

    @Override
    String toString();

    /*non-sealed*/ interface Custom extends TimeFrameUnit {

    }
}
