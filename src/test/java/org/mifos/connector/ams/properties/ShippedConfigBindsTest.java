package org.mifos.connector.ams.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Every property these records ask for has to be there, or the connector must refuse to start and say which one is
 * missing. That is what the plain {@code @Value} declarations did before they were replaced, so these tests hold the
 * replacement to the same promise, and they read the real application.yml rather than a copy of it.
 */
class ShippedConfigBindsTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ AmsLocalProperties.class, MockServiceProperties.class, ZeebeProperties.class })
    static class AllRecords {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AmsLocalProperties.class)
    static class OnlyAmsLocal {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MockServiceProperties.class)
    static class OnlyMockService {}

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ZeebeProperties.class)
    static class OnlyZeebe {}

    /** One configuration class per record, keyed by the prefix that record binds. */
    private static final Map<String, Class<?>> ONE_RECORD_EACH = Map.of("ams.local", OnlyAmsLocal.class, "mock-service.local",
            OnlyMockService.class, "zeebe", OnlyZeebe.class);

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class));
    }

    private ApplicationContextRunner withShippedYaml() {
        return runner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=fin12,bb");
    }

    @Test
    void theShippedConfigurationFillsEveryField() {
        withShippedYaml().withUserConfiguration(AllRecords.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AmsLocalProperties.class).enabled()).isNotNull();
            assertThat(context.getBean(AmsLocalProperties.class).interop().quotesPath()).isNotBlank();
            assertThat(context.getBean(MockServiceProperties.class).loan().repaymentPath()).isNotBlank();
            assertThat(context.getBean(ZeebeProperties.class).broker().contactpoint()).isNotBlank();
            assertThat(context.getBean(ZeebeProperties.class).client().maxExecutionThreads()).isEqualTo(50);
        });
    }

    @Test
    void everyRecordRefusesToStartWhenItsSectionIsMissing() {
        ONE_RECORD_EACH.forEach((prefix, configuration) -> runner().withUserConfiguration(configuration).run(context -> {
            assertThat(context).as("context with nothing configured under '%s'", prefix).hasFailed();
            assertThat(context.getStartupFailure()).as("failure for '%s'", prefix).hasStackTraceContaining("BindValidationException")
                    .hasStackTraceContaining("Binding validation errors on " + prefix);
        }));
    }

    @Test
    void zeebeEnabledKeepsTheDefaultItHadBefore() {
        withShippedYaml().withUserConfiguration(OnlyZeebe.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ZeebeProperties.class).enabled()).isTrue();
        });
    }
}
