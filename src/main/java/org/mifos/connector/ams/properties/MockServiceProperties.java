package org.mifos.connector.ams.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The paths of the bundled Fineract mock, used when {@code ams.local.enabled} is false. Same property names as before,
 * and no defaults, because the {@code @Value} annotations they replace had none.
 */
@Validated
@ConfigurationProperties(prefix = "mock-service.local")
public record MockServiceProperties(@NotNull @Valid Interop interop, @NotNull @Valid Loan loan) {

    public record Interop(@NotNull String transfersPath, @NotNull String partiesPath) {
    }

    public record Loan(@NotNull String repaymentPath) {
    }
}
