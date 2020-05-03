package engineering.everest.lhotse.axon.replay;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventTrackerStatus;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventhandling.tokenstore.TokenStore;

@Slf4j
public class SwitchingAwareTrackingEventProcessor implements ReplayableEventProcessor {

    @Delegate(excludes = Resettable.class)
    private final TrackingEventProcessor delegate;
    private final TransactionManager transactionManager;
    private final TokenStore tokenStore;
    private final int initialSegmentsCount;

    public SwitchingAwareTrackingEventProcessor(TrackingEventProcessor delegate,
                                                TransactionManager transactionManager,
                                                TokenStore tokenStore,
                                                int initialSegmentsCount) {
        this.delegate = delegate;
        this.transactionManager = transactionManager;
        this.tokenStore = tokenStore;
        this.initialSegmentsCount = initialSegmentsCount;
    }

    /**
     * Ensure token is at the end of the event stream before actual reset for replay.
     */
    public void resetTokens(TrackingToken startPosition) {
        TrackingToken headToken = getMessageSource().createHeadToken();
        transactionManager.executeInTransaction(() -> {
            int[] segments = tokenStore.fetchSegments(getName());
            if (segments.length > 0) {
                for (int segment : segments) {
                    tokenStore.storeToken(headToken, getName(), segment);
                }
            } else {
                tokenStore.initializeTokenSegments(getName(), initialSegmentsCount, headToken);
            }
        });
        delegate.resetTokens(startPosition);
    }

    @Override
    public void startReplay(TrackingToken startPosition) {
        LOGGER.info("Start replay {}", this);
        delegate.shutDown();
        resetTokens(startPosition);
        delegate.start();
    }

    @Override
    public void stopReplay() {

    }

    @Override
    public boolean isRelaying() {
        return delegate.processingStatus().values().stream().anyMatch(EventTrackerStatus::isReplaying);
    }

    private interface Resettable {
        void resetTokens(TrackingToken startPosition);
    }
}
