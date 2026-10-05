package org.mifos.connector.ams.zeebe;

import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import org.mifos.connector.ams.properties.ZeebeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression("${zeebe.enabled:true}")
public class ZeebeClientConfiguration {

    private final ZeebeProperties zeebeProperties;

    public ZeebeClientConfiguration(ZeebeProperties zeebeProperties) {
        this.zeebeProperties = zeebeProperties;
    }

    @Bean
    public ZeebeClient setup() {
        return ZeebeClient.newClientBuilder().gatewayAddress(zeebeProperties.broker().contactpoint()).usePlaintext()
                .defaultJobPollInterval(Duration.ofMillis(zeebeProperties.client().pollInterval())).defaultJobWorkerMaxJobsActive(2000)
                .numJobWorkerExecutionThreads(zeebeProperties.client().maxExecutionThreads()).build();
    }
}
