package test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xq.jvmtestkit.junit.Xq;
import com.xq.jvmtestkit.junit.XqTest;
import com.xq.jvmtestkit.rest.RestApiConfig;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@XqTest
@Tag("e2e")
class ExerciseBusinessE2ETest {
    @BeforeEach
    void configureTestKit() {
        Xq.rest(RestApiConfig.at(URI.create(baseUri())));
    }

    @Test
    void createsReadsAndListsDistinctExercise() {
        String exerciseName = "Bench Press E2E " + UUID.randomUUID();
        RestResponse created = createLog(exerciseName, 60, 10);
        String logId = jsonString(created.bodyUtf8(), "id");

        Xq.rest().get("/api/v1/exercise-logs/" + logId,
                        RestRequest.builder().build())
                .should().status(200)
                .matchJson("{\"id\":%s,\"exerciseName\":\"%s\",\"highestSetVolumeKg\":600.00}"
                        .formatted(logId, exerciseName));

        RestResponse distinct = Xq.rest().get("/api/v1/exercise-logs/exercises");
        distinct.should().status(200);
        assertTrue(distinct.bodyUtf8().contains("\"exerciseName\":\"%s\"".formatted(exerciseName)));
    }

    @Test
    void returnsTopTwoLogsByHighestSingleSetVolume() {
        String exerciseName = "Squat-E2E-" + UUID.randomUUID();
        createLog(exerciseName, 60, 10); // 600
        createLog(exerciseName, 80, 8);  // 640
        createLog(exerciseName, 100, 5); // 500

        RestResponse response = Xq.rest().get("/api/v1/exercise-logs?exerciseName=" + exerciseName
                + "&sort=highestSetVolume&limit=2");
        response.should().status(200);
        String body = response.bodyUtf8();
        assertTrue(body.indexOf("\"highestSetVolumeKg\":640.00")
                < body.indexOf("\"highestSetVolumeKg\":600.00"));
        assertTrue(!body.contains("\"highestSetVolumeKg\":500.00"));
    }

    private RestResponse createLog(String exerciseName, int weight, int reps) {
        RestResponse response = Xq.rest().post("/api/v1/exercise-logs",
                RestRequest.builder().jsonBody(Map.of(
                        "exerciseName", exerciseName,
                        "sets", java.util.List.of(Map.of(
                                "setNumber", 1,
                                "weightKg", weight,
                                "reps", reps)))).build());
        response.should().status(201);
        return response;
    }

    private String baseUri() {
        return System.getProperty("xqorb.base-uri",
                System.getenv().getOrDefault("XQORB_BASE_URI", "http://localhost:8080"));
    }

    private String jsonString(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing JSON field: " + field);
        int valueStart = start + marker.length();
        if (json.charAt(valueStart) == '"') {
            valueStart++;
            int valueEnd = json.indexOf('"', valueStart);
            if (valueEnd < 0) throw new AssertionError("Unterminated JSON field: " + field);
            return json.substring(valueStart, valueEnd);
        }
        int valueEnd = json.indexOf(',', valueStart);
        if (valueEnd < 0) valueEnd = json.indexOf('}', valueStart);
        if (valueEnd < 0) throw new AssertionError("Unterminated JSON field: " + field);
        return json.substring(valueStart, valueEnd).trim();
    }
}
