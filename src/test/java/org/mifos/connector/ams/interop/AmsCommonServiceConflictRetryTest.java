package org.mifos.connector.ams.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.mifos.connector.ams.camel.cxfrs.CxfrsUtil;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Fineract answers 409 error.msg.platform.service.query.conflict when concurrent transfers touch the same account and
 * asks the caller to try again. Before PHEE-452 the connector failed the transfer on the first one.
 */
class AmsCommonServiceConflictRetryTest {

    private static final String CONFLICT_BODY = "{\"httpStatusCode\":\"409\",\"userMessageGlobalisationCode\":\""
            + AmsCommonService.FINERACT_CONFLICT_CODE + "\",\"defaultUserMessage\":\"The server is currently unable to handle "
            + "the request due to concurrent modification of the resource. Please try again.\"}";

    /** Answers each send with the next queued (status, body) pair and records the request body it was given. */
    static class ScriptedCxfrsUtil extends CxfrsUtil {

        final Deque<Object[]> responses = new ConcurrentLinkedDeque<>();
        final List<Object> requestBodies = new ArrayList<>();

        ScriptedCxfrsUtil respond(int status, String body) {
            responses.add(new Object[] { status, body });
            return this;
        }

        /** Like the real cxfrs producer (InOut): the request is on IN, the response goes to the OUT message. */
        @Override
        @SuppressWarnings("deprecation")
        public void sendInOut(String endpoint, Exchange ex, Map<String, Object> headers, Object body) {
            ex.getIn().setBody(body);
            ex.getIn().removeHeaders("*");
            ex.getIn().setHeaders(headers);
            requestBodies.add(ex.getIn().getBody());
            assertFalse(ex.hasOut(), "a retry must not start with the previous attempt's response");
            Object[] response = responses.removeFirst();
            ex.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, response[0]);
            ex.getOut().setBody(response[1]);
        }
    }

    private static AmsCommonService serviceWith(CxfrsUtil cxfrsUtil) {
        AmsCommonService service = new AmsCommonService();
        ReflectionTestUtils.setField(service, "cxfrsUtil", cxfrsUtil);
        return service;
    }

    private static Exchange exchange() {
        return new DefaultExchange(new DefaultCamelContext());
    }

    @Test
    void retriesConflictUntilFineractAccepts() {
        ScriptedCxfrsUtil fineract = new ScriptedCxfrsUtil().respond(409, CONFLICT_BODY).respond(409, CONFLICT_BODY).respond(200,
                "{\"transferCode\":\"t1\"}");
        Exchange e = exchange();

        serviceWith(fineract).sendRetryingOnConflict("cxfrs:bean:ams.local.interop", e, Map.of("Fineract-Platform-TenantId", "greenbank"),
                "{\"amount\":10}");

        assertEquals(3, fineract.requestBodies.size());
        assertTrue(fineract.requestBodies.stream().allMatch("{\"amount\":10}"::equals), "every attempt resends the original request");
        assertEquals(200, e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertEquals("{\"transferCode\":\"t1\"}", e.getMessage().getBody(String.class));
    }

    @Test
    void givesUpAfterMaxRetriesAndLeavesTheConflictForTheCaller() {
        ScriptedCxfrsUtil fineract = new ScriptedCxfrsUtil();
        for (int i = 0; i <= AmsCommonService.CONFLICT_MAX_RETRIES; i++) {
            fineract.respond(409, CONFLICT_BODY);
        }
        Exchange e = exchange();

        serviceWith(fineract).sendRetryingOnConflict("cxfrs:bean:ams.local.interop", e, Map.of(), "{}");

        assertEquals(AmsCommonService.CONFLICT_MAX_RETRIES + 1, fineract.requestBodies.size());
        assertEquals(409, e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
        assertTrue(fineract.responses.isEmpty());
    }

    @Test
    void doesNotRetryOtherFailures() {
        ScriptedCxfrsUtil fineract = new ScriptedCxfrsUtil().respond(403,
                "{\"userMessageGlobalisationCode\":\"error.msg.savingsaccount.transaction.insufficient.account.balance\"}");
        Exchange e = exchange();

        serviceWith(fineract).sendRetryingOnConflict("cxfrs:bean:ams.local.interop", e, Map.of(), "{}");

        assertEquals(1, fineract.requestBodies.size());
        assertEquals(403, e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
    }

    @Test
    void onlyTheOptimisticLockingConflictCounts() {
        Exchange other409 = exchange();
        other409.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 409);
        other409.getIn().setBody("{\"userMessageGlobalisationCode\":\"error.msg.interop.transfer.already.exists\"}");
        assertFalse(AmsCommonService.isFineractConflict(other409));

        Exchange conflict = exchange();
        conflict.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 409);
        conflict.getIn().setBody(CONFLICT_BODY);
        assertTrue(AmsCommonService.isFineractConflict(conflict));
    }
}
