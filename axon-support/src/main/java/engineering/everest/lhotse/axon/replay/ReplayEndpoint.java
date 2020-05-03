package engineering.everest.lhotse.axon.replay;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.core.task.TaskExecutor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;

@Slf4j
@Component("replay-callback")
@Endpoint(id = "replay")
public class ReplayEndpoint implements MonitorCallback {

    private final AxonConfiguration axonConfiguration;
    private final List<ReplayCompletionAware> resetCompletionAwares;
    private final TaskExecutor taskExecutor;
    private volatile List<ReplayableEventProcessor> replayingProcessors = emptyList();

    @Autowired
    public ReplayEndpoint(AxonConfiguration axonConfiguration,
                          List<ReplayCompletionAware> resetCompletionAwares,
                          TaskExecutor taskExecutor) {
        this.axonConfiguration = axonConfiguration;
        this.resetCompletionAwares = resetCompletionAwares;
        this.taskExecutor = taskExecutor;
    }

    @ReadOperation
    public Map<String, Object> status() {
        var statusMap = new HashMap<String, Object>();
        statusMap.put("switchingEventProcessors", getReplayableEventProcessors().size());
        statusMap.put("isReplaying", !replayingProcessors.isEmpty());
        return statusMap;
    }

    @WriteOperation
    public synchronized void startReplay(@Nullable Set<String> processingGroups,
                                         @Nullable OffsetDateTime startTime) {
        if (!replayingProcessors.isEmpty()) {
            throw new IllegalStateException("Cannot start replay while an existing one is running");
        }
        var processors = processingGroups == null
                ? getReplayableEventProcessors() : getReplayableEventProcessors(processingGroups);

        if (processors.isEmpty()) {
            throw new IllegalStateException("No matching SwitchingEventProcessor");
        }
        replayingProcessors = processors;

        EventStore eventStore = axonConfiguration.eventStore();
        var trackingToken = startTime == null
                ? eventStore.createTailToken() : eventStore.createTokenAt(startTime.toInstant());

        processors.forEach(p -> p.startReplay(trackingToken));
        axonConfiguration.eventGateway().publish(new ReplayMarkerEvent(randomUUID()));
    }

    @Override
    public void reportSuccess() {
        LOGGER.info("reportSuccess");
        if (!replayingProcessors.isEmpty()) {
            maybeStopReplay();
        }
    }

    @Override
    public void reportFailure(Throwable cause) {
        LOGGER.info("reportFailure");
        if (!replayingProcessors.isEmpty()) {
            maybeStopReplay();
        }
    }

    @Override
    public void reportIgnored() {
        LOGGER.info("reportIgnored");
        if (!replayingProcessors.isEmpty()) {
            maybeStopReplay();
        }
    }

    private synchronized void maybeStopReplay() {
        if (replayingProcessors.isEmpty()) {
            return;
        }

        if (replayingProcessors.stream().noneMatch(ReplayableEventProcessor::isRelaying)) {
            LOGGER.info("Replay completed, stopping ...");
            try {
                replayingProcessors.forEach(ReplayableEventProcessor::stopReplay);
                resetCompletionAwares.forEach(ReplayCompletionAware::replayCompleted);
            } finally {
                replayingProcessors = emptyList();
            }
        }
    }

    private List<ReplayableEventProcessor> getReplayableEventProcessors() {
        return axonConfiguration.eventProcessingConfiguration().eventProcessors().values().stream()
                .filter(e -> e instanceof ReplayableEventProcessor)
                .map(e -> (ReplayableEventProcessor) e)
                .collect(toList());
    }

    private List<ReplayableEventProcessor> getReplayableEventProcessors(Set<String> processingGroups) {
        return processingGroups.stream()
                .map(e -> axonConfiguration.eventProcessingConfiguration()
                        .eventProcessorByProcessingGroup(e, ReplayableEventProcessor.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }
}
