package de.servicehealth.epa4all.server.rest.consent;

import de.servicehealth.api.ConsentDecisionsApi;
import de.servicehealth.api.epa4all.EpaAPI;
import de.servicehealth.api.epa4all.EpaConfig;
import de.servicehealth.api.epa4all.EpaMultiService;
import de.servicehealth.api.epa4all.EpaNotFoundException;
import de.servicehealth.epa4all.server.pnw.ConsentException;
import de.servicehealth.model.GetConsentDecisionInformation200Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class ConsentValidator {

    private static final Logger log = LoggerFactory.getLogger(ConsentValidator.class.getName());

    private static final String PERMIT = "permit";

    @Inject
    EpaConfig epaConfig;

    @Inject
    protected EpaMultiService epaMultiService;
    
    public void validate(String insurantId, ConsentFunction function) throws EpaNotFoundException, ConsentException {
        EpaAPI epaApi = epaMultiService.findEpaAPI(insurantId);
        ConsentDecisionsApi decisionApi = epaApi.getContentDecisionAPI();
        List<Decision> decisions;
        try {
            GetConsentDecisionInformation200Response decisionsResponse = decisionApi.getConsentDecisionInformation(
                insurantId, epaConfig.getEpaUserAgent()
            );
            decisions = fromResponse(decisionsResponse);
        } catch (Exception e) {
            log.error("Error while call getConsentDecisionInformation", e);
            throw new ConsentException(insurantId, e.getMessage());
        }
        Optional<Decision> medicationDecisionOpt = decisions.stream()
            .filter(d -> d.consentFunction().equals(function))
            .findFirst();
        if (medicationDecisionOpt.isPresent() && !medicationDecisionOpt.get().permitted()) {
            String msg = "[%s] Function '%s' is not permitted".formatted(insurantId, function.getFunction());
            throw new ConsentException(insurantId, msg);
        }
    }

    private static List<Decision> fromResponse(GetConsentDecisionInformation200Response response) {
        return response.getData().stream()
            .map(cd -> {
                ConsentFunction consentFunction = ConsentFunction.from(cd.getFunctionId());
                if (consentFunction == null) {
                    log.warn("Unknown consent functionId: " + cd.getFunctionId());
                    return null;
                } else {
                    return new Decision(consentFunction, PERMIT.equalsIgnoreCase(cd.getDecision()));
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }
}