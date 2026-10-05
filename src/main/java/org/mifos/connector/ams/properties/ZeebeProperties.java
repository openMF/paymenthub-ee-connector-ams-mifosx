package org.mifos.connector.ams.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The Zeebe gateway address and client tuning.
 *
 * <p>
 * {@code zeebe.client.evenly-allocated-max-jobs} is deliberately not here: its value can be a SpEL expression
 * ({@code #{...}}, see the commented-out block in application.yml) and only {@code @Value} evaluates those.
 *
 * <p>
 * {@code zeebe.enabled} keeps a default of true, and it is the only default in these records, because it is the only
 * property that had one on both of the {@code @Value} declarations it replaces. The rest had none.
 */
@Validated
@ConfigurationProperties(prefix = "zeebe")
public record ZeebeProperties(@DefaultValue("true") boolean enabled, @NotNull @Valid Broker broker, @NotNull @Valid Client client) {

    public record Broker(@NotNull String contactpoint) {
    }

    public record Client(@NotNull Integer maxExecutionThreads, @NotNull Integer pollInterval) {
    }
}
