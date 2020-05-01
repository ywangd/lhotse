package engineering.everest.lhotse.axon.config;

import engineering.everest.lhotse.axon.CommandValidatingMessageHandlerInterceptor;
import engineering.everest.lhotse.axon.LoggingMessageHandlerInterceptor;
import engineering.everest.lhotse.axon.replay.SwitchingEventProcessorBuilder;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.gateway.IntervalRetryScheduler;
import org.axonframework.commandhandling.gateway.RetryScheduler;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.EventProcessingModule;
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.modelling.command.AnnotationCommandTargetResolver;
import org.axonframework.spring.config.AxonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
@Configuration
public class AxonConfig {
    @Bean
    public RetryScheduler retryScheduler(@Value("${application.axon.retry.interval-milli-seconds}") int retryInterval,
                                         @Value("${application.axon.retry.max-count}") int retryMaxCount,
                                         @Value("${application.axon.retry.pool-size}") int retryPoolSize) {
        return IntervalRetryScheduler.builder()
                .retryInterval(retryInterval)
                .maxRetryCount(retryMaxCount)
                .retryExecutor(new ScheduledThreadPoolExecutor(retryPoolSize))
                .build();
    }

    @Bean
    public DefaultCommandGateway defaultCommandGateway(CommandBus commandBus,
                                                       RetryScheduler retryScheduler,
                                                       List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors) {
        return DefaultCommandGateway.builder()
                .commandBus(commandBus)
                .retryScheduler(retryScheduler)
                .dispatchInterceptors(dispatchInterceptors)
                .build();
    }

    @Bean
    public SimpleCommandBus commandBus(TransactionManager txManager,
                                       AxonConfiguration axonConfiguration,
                                       CommandValidatingMessageHandlerInterceptor commandValidatingMessageHandlerInterceptor,
                                       LoggingMessageHandlerInterceptor loggingMessageHandlerInterceptor) {
        var simpleCommandBus = SimpleCommandBus.builder()
                .transactionManager(txManager)
                .messageMonitor(axonConfiguration.messageMonitor(CommandBus.class, "commandBus"))
                .build();
        simpleCommandBus.registerHandlerInterceptor(new CorrelationDataInterceptor<>(axonConfiguration.correlationDataProviders()));
        simpleCommandBus.registerHandlerInterceptor(commandValidatingMessageHandlerInterceptor);
        simpleCommandBus.registerHandlerInterceptor(loggingMessageHandlerInterceptor);
        return simpleCommandBus;
    }

    @Autowired
    public void configure(AxonConfiguration axonConfiguration,
                          EventProcessingModule eventProcessingModule,
                          @Value("${application.axon.event-processor.type:switching}") EventProcessorType eventProcessorType) {

        eventProcessingModule.byDefaultAssignTo("default");
        switch (eventProcessorType) {
            case SUBSCRIBING:
                eventProcessingModule.usingSubscribingEventProcessors();
                break;
            case TRACKING:
                eventProcessingModule.usingTrackingEventProcessors();
                eventProcessingModule.registerTrackingEventProcessorConfiguration((configuration) ->
                        TrackingEventProcessorConfiguration.forSingleThreadedProcessing());
                break;
            case SWITCHING:
                eventProcessingModule.registerEventProcessorFactory(
                        new SwitchingEventProcessorBuilder(axonConfiguration, eventProcessingModule));
                break;
            default:
                throw new IllegalArgumentException(String.format("Unrecognised event processor type: %s", eventProcessorType));
        }
    }

    @Bean
    public AnnotationCommandTargetResolver annotationCommandTargetResolver() {
        return AnnotationCommandTargetResolver.builder().build();
    }

    public enum EventProcessorType {
        SUBSCRIBING,
        TRACKING,
        SWITCHING,
    }
}
