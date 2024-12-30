package one.chartsy.api.messages;

import lombok.Getter;
import lombok.experimental.Accessors;
import one.chartsy.api.messages.handlers.ShutdownRequestHandler;
import one.chartsy.api.messages.handlers.ShutdownResponseHandler;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageType;

import java.util.function.BiConsumer;

@Getter
@Accessors(fluent = true)
public enum StandardMessageType implements MessageType {
    SHUTDOWN_REQUEST(ShutdownRequestHandler.class, ((ShutdownRequestHandler handler, ShutdownRequest message) -> handler.onShutdownRequest(message))),
    SHUTDOWN_RESPONSE(ShutdownResponseHandler.class, ((ShutdownResponseHandler handler, ShutdownResponse message) -> handler.onShutdownResponse(message)))
    ;

    private final Class<?> handlerType;
    private final BiConsumer<Object, Message> handlerFunction;

    <T> StandardMessageType(Class<T> handlerType, BiConsumer<T, ? extends Message> handlerFunction) {
        this.handlerType = handlerType;
        this.handlerFunction = (BiConsumer) handlerFunction;
    }
}
