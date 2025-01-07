package de.servicehealth.epa4all.integration.bc.epa;

import de.servicehealth.epa4all.common.profile.ProxyEpaTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static de.servicehealth.vau.VauClient.X_KONNEKTOR;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(ProxyEpaTestProfile.class)
public class MultiForwardEpaIT {

    private static class GetInfo {
        int statusCode;
        String insurantId;
        String response;

        public GetInfo(int statusCode, String insurantId, String response) {
            this.statusCode = statusCode;
            this.insurantId = insurantId;
            this.response = response;
        }
    }

    @Test
    public void multipleRequestsFetchMedicationsBundles() {
        Random random = new SecureRandom();
        List<String> patients = List.of("X110485291"/*, "X110486750"*/);

        int cnt = 10;
        List<Future<GetInfo>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(cnt)) {
            for (int i = 0; i < cnt; i++) {
                futures.add(executor.submit(() -> {
                    String insurantId = random.nextBoolean() ? patients.getFirst() : patients.getLast();
                    String query = String.format(
                        "fhir/Medication?_count=10&_offset=0&_total=none&x-insurantid=%s&x-konnektor=localhost", insurantId
                    );
                    Response response = given()
                        .queryParams(Map.of(X_KONNEKTOR, "localhost"))
                        .when()
                        .get(query);

                    return new GetInfo(response.getStatusCode(), insurantId, response.getBody().asString());
                }));
            }
        }
        List<GetInfo> results = futures.stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return new GetInfo(
                    500, "unknown", e.getMessage()
                );
            }
        }).toList();

        for (GetInfo result : results) {
            String insurantId = result.insurantId;
            String bundle = result.response;
            assertNotNull(bundle);
            System.out.printf("[%s] RESPONSE -> %s%n", insurantId, bundle.substring(0, Math.min(100, bundle.length())));
        }
    }
}
