package engineering.everest.lhotse.axon.replay;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.SubscribingEventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.lifecycle.ShutdownHandler;
import org.axonframework.lifecycle.StartHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.axonframework.lifecycle.Phase.LOCAL_MESSAGE_HANDLER_REGISTRATIONS;

@Slf4j
public class SwitchingEventProcessor implements ReplayableEventProcessor {

    private final SubscribingEventProcessor subscribingEventProcessor;
    private final SwitchingAwareTrackingEventProcessor trackingEventProcessor;

    private final AtomicReference<EventProcessor> currentEventProcessor;

    public SwitchingEventProcessor(SubscribingEventProcessor subscribingEventProcessor,
                                   SwitchingAwareTrackingEventProcessor trackingEventProcessor) {
        this.subscribingEventProcessor = subscribingEventProcessor;
        this.trackingEventProcessor = trackingEventProcessor;
        this.currentEventProcessor = new AtomicReference<>(subscribingEventProcessor);
    }

    @Override
    public void startReplay(TrackingToken startPosition) {
        if (currentEventProcessor.compareAndSet(subscribingEventProcessor, trackingEventProcessor)) {
            LOGGER.info(String.format("Starting replay and switching to %s",
                    TrackingEventProcessor.class.getSimpleName()));
            subscribingEventProcessor.shutDown();
            trackingEventProcessor.resetTokens(startPosition);
            start();
            LOGGER.info("Started replay");
        } else {
            LOGGER.info("A previous replay is still running");
        }
    }

    @Override
    public void stopReplay() {
        if (currentEventProcessor.compareAndSet(trackingEventProcessor, subscribingEventProcessor)) {
            LOGGER.info(String.format("Stopping replay and switching to %s",
                    SubscribingEventProcessor.class.getSimpleName()));
            trackingEventProcessor.shutDown();
            start();
            LOGGER.info("Stopped replay");
        } else {
            LOGGER.info("No replay is running");
        }
    }

    @Override
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public boolean isRelaying() {
        return currentEventProcessor.get() == trackingEventProcessor;
    }

    @Override
    public String getName() {
        return currentEventProcessor.get().getName();
    }

    @Override
    public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
        return currentEventProcessor.get().getHandlerInterceptors();
    }

    @Override
    @StartHandler(phase = LOCAL_MESSAGE_HANDLER_REGISTRATIONS)
    public void start() {
        currentEventProcessor.get().start();
    }

    @Override
    @ShutdownHandler(phase = LOCAL_MESSAGE_HANDLER_REGISTRATIONS)
    public void shutDown() {
        currentEventProcessor.get().shutDown();
    }

    @Override
    public Registration registerHandlerInterceptor(MessageHandlerInterceptor<? super EventMessage<?>> handlerInterceptor) {
        return currentEventProcessor.get().registerHandlerInterceptor(handlerInterceptor);
    }
}
