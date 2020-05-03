package engineering.everest.lhotse.axon.replay;

import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitorCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class MarkerEventMessageMonitor implements MessageMonitor<Message<?>> {

    private final MonitorCallback monitorCallback;

    @Autowired
    public MarkerEventMessageMonitor(@Qualifier("replay-callback") MonitorCallback monitorCallback) {
        this.monitorCallback = monitorCallback;
    }

    @Override
    public MonitorCallback onMessageIngested(Message<?> message) {
        if (ReplayMarkerEvent.class.isAssignableFrom(message.getPayloadType())) {
            return monitorCallback;
        } else {
            return NoOpMessageMonitorCallback.INSTANCE;
        }
    }

}
