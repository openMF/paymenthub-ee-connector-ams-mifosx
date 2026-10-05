package org.mifos.connector.ams.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Everything under {@code ams.local}, except the tenant list, which keeps its own class because it is a list of beans
 * rather than a fixed set of values.
 *
 * <p>
 * The property names are exactly the ones that were on the {@code @Value} annotations before, because the operator sets
 * some of them as environment variables. Renaming one would silently break a deployment.
 *
 * <p>
 * Nothing here carries a default, because nothing here had one. {@code ams.local.enabled} needs a word: the baseline
 * read it twice and disagreed with itself, as a bare {@code @Value("${ams.local.enabled}")} in {@code AmsCommonService}
 * and as {@code @Value("${ams.local.enabled:false}")} in {@code ZeebeeWorkers}. The bare one wins, because it decided
 * what the application did: with the property absent the context failed to start, so the {@code false} in the other
 * class could never be reached. Required here is therefore what the application already did.
 *
 * <p>
 * Three fields are deliberately left optional: {@code account.instances-path}, {@code account.definitons-path} and the
 * whole {@code auth} group. They are read only by {@code AmsFinCNService}, which exists only when
 * {@code ams.local.version} is {@code cn}, and only {@code application-fincn.yml} sets them. The deployment runs with
 * {@code fin12,bb}, where that bean is never created and those properties are never read, so requiring them here would
 * stop a pod that starts today.
 */
@Validated
@ConfigurationProperties(prefix = "ams.local")
public record AmsLocalProperties(@NotNull String version, @NotNull Boolean enabled, @NotNull String keystorePath,
        @NotNull String keystorePassword, @NotNull Boolean serverCertCheck, @NotNull @Valid Interop interop,
        @NotNull @Valid Customer customer, @NotNull @Valid Account account, @Valid Auth auth, @NotNull @Valid Loan loan) {

    public record Interop(@NotNull String quotesPath, @NotNull String partiesPath, @NotNull String transfersPath,
            @NotNull String accountsPath) {
    }

    public record Customer(@NotNull String path, @NotNull String image) {
    }

    public record Account(@NotNull String savingsaccountsPath, String instancesPath, String definitonsPath) {
    }

    public record Auth(String path) {
    }

    public record Loan(@NotNull String repaymentPath) {
    }
}
