package org.mifos.connector.ams.interop;

import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_PATH;
import static org.mifos.connector.ams.camel.config.CamelProperties.TRANSFER_ACTION;
import static org.mifos.connector.ams.camel.cxfrs.HeaderBasedInterceptor.CXF_TRACE_HEADER;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.ACCOUNT_HOLDING_INSTITUTION_ID;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.ACCOUNT_NUMBER;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.PARTY_ID;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.PARTY_ID_TYPE;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.TENANT_ID;
import static org.mifos.connector.ams.zeebe.ZeebeVariables.TRANSACTION_ID;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.mifos.connector.ams.camel.cxfrs.CxfrsUtil;
import org.mifos.connector.ams.properties.AmsLocalProperties;
import org.mifos.connector.ams.properties.MockServiceProperties;
import org.mifos.connector.ams.tenant.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
// @ConditionalOnExpression("${ams.local.enabled}")
public class AmsCommonService {

    @Autowired
    protected AmsLocalProperties amsLocalProperties;

    @Autowired
    protected MockServiceProperties mockServiceProperties;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private CxfrsUtil cxfrsUtil;
    @Autowired
    RestTemplate restTemplate;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String APPLICATION_TYPE = "application/json";

    static final String FINERACT_CONFLICT_CODE = "error.msg.platform.service.query.conflict";
    static final int CONFLICT_MAX_RETRIES = 3;
    static final long CONFLICT_BACKOFF_MS = 200;

    public void getLocalQuote(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "POST");
        headers.put(HTTP_PATH, amsLocalProperties.interop().quotesPath());
        headers.put("Content-Type", "application/json");
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, e.getIn().getBody());
    }

    public void getExternalAccount(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "GET");
        headers.put(HTTP_PATH, amsLocalProperties.interop().partiesPath().replace("{idType}", e.getProperty(PARTY_ID_TYPE, String.class))
                .replace("{idValue}", e.getProperty(PARTY_ID, String.class)));
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        if (amsLocalProperties.enabled()) {
            cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, null);
        } else {
            logger.info("-------------- Calling Mock external Account API --------------");
            headers.put(HTTP_PATH, mockServiceProperties.interop().partiesPath());
            cxfrsUtil.sendInOut("cxfrs:bean:mock-service.local.interop", e, headers, null);
        }
        // cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, null);
    }

    public void sendTransfer(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "POST");
        headers.put(HTTP_PATH, amsLocalProperties.interop().transfersPath());
        logger.info("Send Transfer Body: {}", e.getIn().getBody());
        Map<String, String> queryMap = new LinkedHashMap<>();
        queryMap.put("action", e.getProperty(TRANSFER_ACTION, String.class));
        headers.put(CxfConstants.CAMEL_CXF_RS_QUERY_MAP, queryMap);
        headers.put("Content-Type", "application/json");
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        if (amsLocalProperties.enabled()) {
            sendRetryingOnConflict("cxfrs:bean:ams.local.interop", e, headers, e.getIn().getBody());
        } else {
            logger.info("-------------- Calling Mock transfers APIs --------------");
            headers.put(HTTP_PATH, mockServiceProperties.interop().transfersPath());
            cxfrsUtil.sendInOut("cxfrs:bean:mock-service.local.interop", e, headers, e.getIn().getBody().toString());
        }
    }

    public void repayLoan(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "POST");
        headers.put(HTTP_PATH,
                amsLocalProperties.loan().repaymentPath().replace("{accountNumber}", e.getProperty(ACCOUNT_NUMBER, String.class)));
        logger.debug("Loan Repayment Body: {}", e.getIn().getBody());
        headers.put("Content-Type", APPLICATION_TYPE);
        Map<String, Object> variables = e.getProperty("zeebeVariables", Map.class);
        String accountHoldingInstitutionId = (String) variables.get(ACCOUNT_HOLDING_INSTITUTION_ID);
        headers.putAll(tenantService.getHeaders(accountHoldingInstitutionId));
        if (amsLocalProperties.enabled()) {
            sendRetryingOnConflict("cxfrs:bean:ams.local.loan", e, headers, e.getIn().getBody());
        } else {
            logger.info("-------------- Calling Mock Loan repayment APIs --------------");
            headers.put(HTTP_PATH, mockServiceProperties.loan().repaymentPath());
            cxfrsUtil.sendInOut("cxfrs:bean:mock-service.local.loan", e, headers, e.getIn().getBody().toString());
        }
        // cxfrsUtil.sendInOut("cxfrs:bean:ams.local.loan", e, headers, e.getIn().getBody());
    }

    /**
     * Sends a Fineract write, retrying while Fineract rejects it with 409 error.msg.platform.service.query.conflict.
     * That is Fineract's optimistic-locking failure: the whole command is rolled back and Fineract asks the caller to
     * try again, which happens when concurrent transfers touch the same account. Any other response is returned at
     * once.
     */
    @SuppressWarnings("deprecation") // hasOut/setOut: the cxfrs producer answers on the OUT message
    void sendRetryingOnConflict(String endpoint, Exchange e, Map<String, Object> headers, Object body) {
        for (int attempt = 1;; attempt++) {
            if (e.hasOut()) {
                // drop the previous attempt's response, so the retry is not read back as this attempt's answer
                e.setOut(null);
            }
            // sendInOut installs the map as the IN headers, so give each attempt its own copy
            cxfrsUtil.sendInOut(endpoint, e, new HashMap<>(headers), body);
            if (attempt > CONFLICT_MAX_RETRIES || !isFineractConflict(e)) {
                return;
            }
            long backoff = CONFLICT_BACKOFF_MS * attempt + ThreadLocalRandom.current().nextLong(CONFLICT_BACKOFF_MS);
            logger.warn("Fineract reported a concurrent modification for transaction {}, retry {}/{} in {} ms",
                    e.getProperty(TRANSACTION_ID), attempt, CONFLICT_MAX_RETRIES, backoff);
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static boolean isFineractConflict(Exchange e) {
        // getMessage(): the response is on the OUT message right after the send; Camel only moves it to IN between
        // route steps, which is where the rest of the route reads it
        Integer status = e.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        if (status == null || status != 409) {
            return false;
        }
        String responseBody = e.getMessage().getBody(String.class);
        return responseBody != null && responseBody.contains(FINERACT_CONFLICT_CODE);
    }

    public boolean sendCallback(String callbackURL, String body) {
        logger.info("Sending Callback...");
        ResponseEntity responseEntity;
        try {
            responseEntity = restTemplate.postForEntity(callbackURL, body, null);
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                logger.info("Callback sent");
                return true;
            } else {
                logger.info("Callback failed!!!");
                return false;
            }
        } catch (Exception exception) {
            logger.warn("Callback to {} failed", callbackURL, exception);
        }
        return false;
    }

    public void registerInteropIdentifier(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "POST");
        headers.put(HTTP_PATH, amsLocalProperties.interop().partiesPath().replace("{idType}", e.getProperty(PARTY_ID_TYPE, String.class))
                .replace("{idValue}", e.getProperty(PARTY_ID, String.class)));
        headers.put("Content-Type", "application/json");
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, e.getIn().getBody());
    }

    public void removeInteropIdentifier(Exchange e) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CXF_TRACE_HEADER, true);
        headers.put(HTTP_METHOD, "DELETE");
        headers.put(HTTP_PATH, amsLocalProperties.interop().partiesPath().replace("{idType}", e.getProperty(PARTY_ID_TYPE, String.class))
                .replace("{idValue}", e.getProperty(PARTY_ID, String.class)));
        headers.put("Content-Type", "application/json");
        headers.putAll(tenantService.getHeaders(e.getProperty(TENANT_ID, String.class)));
        e.getIn().setBody(null);
        cxfrsUtil.sendInOut("cxfrs:bean:ams.local.interop", e, headers, null);
    }
}
