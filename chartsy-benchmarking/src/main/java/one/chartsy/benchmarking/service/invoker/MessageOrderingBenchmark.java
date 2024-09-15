package one.chartsy.benchmarking.service.invoker;

import one.chartsy.core.event.AbstractInvoker;
import one.chartsy.data.stream.AbstractMessage;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageType;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class MessageOrderingBenchmark {
    enum MessageTypes implements MessageType {
        MSG1((h, m) -> ((MessageType1Handler) h).handleMessage1((Entity1) m)),
        MSG2((h, m) -> ((MessageType2Handler) h).handleMessage2((Entity2) m)),
        MSG3((h, m) -> ((MessageType3Handler) h).handleMessage3((Entity3) m)),
        MSG4((h, m) -> ((MessageType4Handler) h).handleMessage4((Entity4) m)),
        MSG5((h, m) -> ((MessageType5Handler) h).handleMessage5((Entity5) m)),
        MSG6((h, m) -> ((MessageType6Handler) h).handleMessage6((Entity6) m)),
        MSG7((h, m) -> ((MessageType7Handler) h).handleMessage7((Entity7) m)),
        MSG8((h, m) -> ((MessageType8Handler) h).handleMessage8((Entity8) m)),
        MSG9((h, m) -> ((MessageType9Handler) h).handleMessage9((Entity9) m));

        BiConsumer<Object, one.chartsy.data.stream.Message> handlerFunction;

        MessageTypes(BiConsumer<Object, one.chartsy.data.stream.Message> handlerFunction) {
            this.handlerFunction = handlerFunction;
        }

        @Override
        public Class<?> handlerType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BiConsumer<Object, one.chartsy.data.stream.Message> handlerFunction() {
            return handlerFunction;
        }
    }

    public static interface Message extends one.chartsy.data.stream.Message, Comparable<Message> {
        @Override
        default int compareTo(Message o) {
            return Long.compare(time(), o.time());
        }
    }

    public static /*sealed*/ abstract class ConcreteMessage
            extends AbstractMessage
            implements Message
            /*permits Entity1, Entity2, Entity3, Entity4, Entity5, Entity6, Entity7, Entity8, Entity9*/
    {
        private final MessageTypes type;

        protected ConcreteMessage(long time, MessageTypes type) {
            super(time);
            this.type = type;
        }

        @Override
        public MessageType type() {
            //return this.getClass().getAnnotation(MessageTypeAnnotation.class).value();
            return type;
        }
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface MessageTypeAnnotation {
        MessageTypes value();
    }

    public record Record1(long time, MessageType type) implements Message {}
    public record Record2(long time, MessageType type) implements Message {}
    public record Record3(long time, MessageType type) implements Message {}
    public record Record4(long time, MessageType type) implements Message {}
    public record Record5(MessageType type, long time) implements Message {}
    public record Record6(long time, MessageType type) implements Message {}
    public record Record7(MessageType type, long time) implements Message {}
    public record Record8(long time, MessageType type) implements Message {}
    public record Record9(MessageType type, long time) implements Message {}

    interface MessageType1Handler { void handleMessage1(Entity1 entity); }
    interface MessageType2Handler { void handleMessage2(Entity2 entity); }
    interface MessageType3Handler { void handleMessage3(Entity3 entity); }
    interface MessageType4Handler { void handleMessage4(Entity4 entity); }
    interface MessageType5Handler { void handleMessage5(Entity5 entity); }
    interface MessageType6Handler { void handleMessage6(Entity6 entity); }
    interface MessageType7Handler { void handleMessage7(Entity7 entity); }
    interface MessageType8Handler { void handleMessage8(Entity8 entity); }
    interface MessageType9Handler { void handleMessage9(Entity9 entity); }

    @MessageTypeAnnotation(MessageTypes.MSG1)
    public static final class Entity1 extends ConcreteMessage {
        protected Entity1(long time) { super(time, MessageTypes.MSG1); }
    }
    @MessageTypeAnnotation(MessageTypes.MSG2)
    public static final class Entity2 extends ConcreteMessage {
        protected Entity2(long time) { super(time, MessageTypes.MSG2); }
    }
    @MessageTypeAnnotation(MessageTypes.MSG3)
    public static final class Entity3 extends ConcreteMessage {
        protected Entity3(long time) { super(time, MessageTypes.MSG3); }
    }
    @MessageTypeAnnotation(MessageTypes.MSG4)
    public static final class Entity4 extends ConcreteMessage {
        protected Entity4(long time) { super(time, MessageTypes.MSG4); }
    }
    @MessageTypeAnnotation(MessageTypes.MSG5)
    public static final class Entity5 extends ConcreteMessage {
        protected Entity5(long time) { super(time, MessageTypes.MSG5); }
    }
    @MessageTypeAnnotation(MessageTypes.MSG6)
    public static final class Entity6 extends ConcreteMessage {
        protected Entity6(long time) { super(time, MessageTypes.MSG6); }
    }
    @MessageTypeAnnotation(MessageTypes.MSG7)
    public static final class Entity7 extends ConcreteMessage {
        protected Entity7(long time) { super(time, MessageTypes.MSG7); }
    }
    @MessageTypeAnnotation(MessageTypes.MSG8)
    public static final class Entity8 extends ConcreteMessage {
        protected Entity8(long time) { super(time, MessageTypes.MSG8); }
    }
    @MessageTypeAnnotation(MessageTypes.MSG9)
    public static final class Entity9 extends ConcreteMessage {
        protected Entity9(long time) { super(time, MessageTypes.MSG9); }
    }


    @State(Scope.Benchmark)
    public static class BenchmarkState {
        public final ThreadLocalRandom random = ThreadLocalRandom.current();
        public List<Message> messages;
        public List<Message> messages2;
        public Handler handler;

        @Setup(Level.Trial)
        public void setup() {
            handler = new Handler();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            
            messages = new ArrayList<>(1_000_000);
            for (int i = 0; i < 1_000_000; i++) {
                int type = random.nextInt(9) + 1;
                long timestamp = random.nextLong();
                Message message = switch (type) {
                    case 1 -> new Record1(timestamp, MessageTypes.MSG1);
                    case 2 -> new Record2(timestamp, MessageTypes.MSG2);
                    case 3 -> new Record3(timestamp, MessageTypes.MSG3);
                    case 4 -> new Record4(timestamp, MessageTypes.MSG4);
                    case 5 -> new Record5(MessageTypes.MSG5, timestamp);
                    case 6 -> new Record6(timestamp, MessageTypes.MSG6);
                    case 7 -> new Record7(MessageTypes.MSG7, timestamp);
                    case 8 -> new Record8(timestamp, MessageTypes.MSG8);
                    case 9 -> new Record9(MessageTypes.MSG9, timestamp);
                    default -> throw new IllegalStateException("Unexpected value: " + type);
                };
                messages.add(message);
            }

            messages2 = new ArrayList<>(1_000_000);
            for (int i = 0; i < 1_000_000; i++) {
                int type = random.nextInt(9) + 1;
                long timestamp = random.nextLong();
                Message message = switch (type) {
                    case 1 -> new Entity1(timestamp);
                    case 2 -> new Entity2(timestamp);
                    case 3 -> new Entity3(timestamp);
                    case 4 -> new Entity4(timestamp);
                    case 5 -> new Entity5(timestamp);
                    case 6 -> new Entity6(timestamp);
                    case 7 -> new Entity7(timestamp);
                    case 8 -> new Entity8(timestamp);
                    case 9 -> new Entity9(timestamp);
                    default -> throw new IllegalStateException("Unexpected value: " + type);
                };
                messages2.add(message);
            }
        }
    }

//    @Benchmark
//    @BenchmarkMode(Mode.AverageTime)
//    @OutputTimeUnit(TimeUnit.MILLISECONDS)
//    public void benchmarkMessageSortingRecords(BenchmarkState state) {
//        Collections.sort(state.messages);
//    }
//
//    @Benchmark
//    @BenchmarkMode(Mode.AverageTime)
//    @OutputTimeUnit(TimeUnit.MILLISECONDS)
//    public void benchmarkMessageSortingHierarchy(BenchmarkState state) {
//        Collections.sort(state.messages2);
//    }

//    @Benchmark
//    @BenchmarkMode(Mode.AverageTime)
//    @OutputTimeUnit(TimeUnit.MICROSECONDS)
//    public void benchmarkGetMessageTypeFromRecord(BenchmarkState state, Blackhole blackhole) {
//        for (int i = 0; i < 1000; i++) {
//            blackhole.consume(state.messages.get(state.random.nextInt(state.messages.size())).type());
//        }
//    }

    static class Handler implements MessageType1Handler, MessageType2Handler, MessageType3Handler, MessageType4Handler, MessageType5Handler, MessageType6Handler, MessageType7Handler, MessageType8Handler, MessageType9Handler {
        final AtomicInteger value = new AtomicInteger();
        @Override public void handleMessage1(Entity1 entity) { value.set(1); }
        @Override public void handleMessage2(Entity2 entity) { value.set(2); }
        @Override public void handleMessage3(Entity3 entity) { value.set(3); }
        @Override public void handleMessage4(Entity4 entity) { value.set(4); }
        @Override public void handleMessage5(Entity5 entity) { value.set(5); }
        @Override public void handleMessage6(Entity6 entity) { value.set(6); }
        @Override public void handleMessage7(Entity7 entity) { value.set(7); }
        @Override public void handleMessage8(Entity8 entity) { value.set(8); }
        @Override public void handleMessage9(Entity9 entity) { value.set(9); }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void benchmarkGetMessageTypeFromClasses(BenchmarkState state, Blackhole blackhole) {
        one.chartsy.data.stream.Message msg = state.messages2.get(state.random.nextInt(state.messages2.size()));
        msg.type().handlerFunction().accept(state.handler, msg);
        blackhole.consume(state.handler.value);
    }

//    public static class Main {
        public static void main(String[] args) throws RunnerException {
            Options opt = new OptionsBuilder()
                    // Specify which benchmarks to run.
                    // You can be more specific if you'd like to run only one benchmark per test.
                    .include(MessageOrderingBenchmark.class.getName() + ".*")
                    // Set the following options as needed
                    .warmupTime(TimeValue.seconds(1))
                    .warmupIterations(3)
                    .measurementTime(TimeValue.seconds(3))
                    .measurementIterations(10)
                    .threads(1)
                    .forks(1)
                    .shouldFailOnError(true)
                    .shouldDoGC(true)
                    //.addProfiler(JavaFlightRecorderProfiler.class)
                    .build();
            new Runner(opt).run();
        }
//    }
}