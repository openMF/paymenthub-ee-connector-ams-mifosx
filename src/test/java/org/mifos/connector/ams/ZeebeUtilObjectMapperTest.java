package org.mifos.connector.ams;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.mifos.connector.ams.zeebe.ZeebeUtil;
import org.mifos.connector.common.gsma.dto.GsmaTransfer;
import org.mifos.connector.common.gsma.dto.Party;
import org.mifos.connector.common.mojaloop.dto.FspMoneyData;

/**
 * ZeebeUtil used to build its own {@code new ObjectMapper()} instead of the one configured in AmsConnectorApplication.
 * These two tests show what that changed. Both of them fail if ZeebeUtil goes back to a private mapper.
 */
class ZeebeUtilObjectMapperTest {

    /** The same mapper AmsConnectorApplication publishes as a bean. */
    private static ObjectMapper configuredMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private static ZeebeUtil zeebeUtil(ObjectMapper mapper) {
        return new ZeebeUtil(mapper, List.of("S", "L"));
    }

    private static GsmaTransfer gsmaTransfer() {
        Party payer = new Party();
        payer.setPartyIdType("MSISDN");
        payer.setPartyIdIdentifier("27713803912");
        Party payee = new Party();
        payee.setPartyIdType("ACCOUNT_ID");
        payee.setPartyIdIdentifier("S000000001");

        GsmaTransfer transfer = new GsmaTransfer();
        transfer.setPayer(List.of(payer));
        transfer.setPayee(List.of(payee));
        transfer.setAmount("100");
        transfer.setCurrency("USD");
        return transfer;
    }

    /**
     * The channelRequest string this method produces is stored as a Zeebe variable and read by other components. With
     * the configured mapper the null fields are dropped, as they are everywhere else in this connector; with a plain
     * mapper they are all written out.
     */
    @Test
    void channelRequestIsWrittenWithTheConfiguredMapper() throws Exception {
        String withConfigured = zeebeUtil(configuredMapper()).convertGsmaTransfertoTransactionChannel(gsmaTransfer(), "000000001");
        String withPlain = zeebeUtil(new ObjectMapper()).convertGsmaTransfertoTransactionChannel(gsmaTransfer(), "000000001");

        assertFalse(withConfigured.contains(":null"), "configured mapper must not write null fields, got: " + withConfigured);
        assertTrue(withPlain.contains(":null"), "a plain mapper does write them, which is the difference this test is about");
    }

    /**
     * zeebeVariable deserialises a Zeebe variable into a DTO. The configured mapper ignores unknown properties; a plain
     * mapper throws, and the throw happens inside a Zeebe job handler, where it leaves the process instance hanging.
     */
    @Test
    void unknownVariableFieldsAreIgnoredWithTheConfiguredMapper() {
        Map<String, Object> amount = new HashMap<>();
        amount.put("amount", BigDecimal.TEN);
        amount.put("currency", "USD");
        amount.put("aFieldThisDtoDoesNotHave", "x");

        Map<String, Object> variables = new HashMap<>();
        variables.put("amount", amount);
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("zeebeVariables", variables);

        assertDoesNotThrow(() -> zeebeUtil(configuredMapper()).zeebeVariable(exchange, "amount", FspMoneyData.class));
        assertThrows(Exception.class, () -> zeebeUtil(new ObjectMapper()).zeebeVariable(exchange, "amount", FspMoneyData.class));
    }
}
