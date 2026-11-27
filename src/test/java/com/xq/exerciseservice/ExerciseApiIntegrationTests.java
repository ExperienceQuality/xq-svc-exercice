package com.xq.exerciseservice;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
class ExerciseApiIntegrationTests {
    private static final String XQ_DB_IMAGE = loadXqDbImage();
    private static final String APP_USER = "exercise_app";
    private static final String APP_PASSWORD = "exercise_app_test";
    private static final String APP_DATABASE = "xq";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
                    DockerImageName.parse(XQ_DB_IMAGE).asCompatibleSubstituteFor("postgres"))
            // Image init creates role exercise_app; POSTGRES_USER=exercise_app would conflict.
            // Keep Testcontainers default bootstrap user; withPassword sets POSTGRES_PASSWORD.
            .withDatabaseName(APP_DATABASE)
            .withPassword(APP_PASSWORD)
            .withEnv("POSTGRES_PASSWORD", APP_PASSWORD);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:postgresql://%s:%d/%s"
                        .formatted(postgres.getHost(), postgres.getFirstMappedPort(), APP_DATABASE));
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> "exercise");
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        // exercise_app has DELETE, not TRUNCATE, on xq-db tables.
        jdbc.sql("delete from exercise_sets").update();
        jdbc.sql("delete from exercise_logs").update();
    }

    @Test
    void createsLogAndCalculatesHighestSingleSetVolume() throws Exception {
        mvc.perform(post("/api/v1/exercise-logs")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"exerciseName":"Bench Press","sets":[
                                  {"setNumber":1,"weightKg":60.00,"reps":10},
                                  {"setNumber":2,"weightKg":65.00,"reps":8}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exerciseName").value("Bench Press"))
                .andExpect(jsonPath("$.sets.length()").value(2))
                .andExpect(jsonPath("$.sets[0].volumeKg").value(600.00))
                .andExpect(jsonPath("$.highestSetVolumeKg").value(600.00));
    }

    @Test
    void listsTopLogsByHighestVolumeAndDistinctExercises() throws Exception {
        createLog("Bench Press", 60, 10);
        createLog("Bench Press", 70, 8);
        createLog("Bench Press", 50, 5);
        createLog("Squat", 100, 5);

        mvc.perform(get("/api/v1/exercise-logs")
                        .param("exerciseName", "bench press")
                        .param("sort", "highestSetVolume")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].highestSetVolumeKg").value(600.00))
                .andExpect(jsonPath("$[1].highestSetVolumeKg").value(560.00));

        mvc.perform(get("/api/v1/exercise-logs/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].exerciseName").value("Bench Press"))
                .andExpect(jsonPath("$[0].logCount").value(3))
                .andExpect(jsonPath("$[1].exerciseName").value("Squat"));
    }

    @Test
    void rejectsInvalidSets() throws Exception {
        mvc.perform(post("/api/v1/exercise-logs")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"exerciseName":"Squat","sets":[
                                  {"setNumber":1,"weightKg":-1,"reps":0}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void createLog(String name, int weight, int reps) throws Exception {
        mvc.perform(post("/api/v1/exercise-logs")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"exerciseName":"%s","sets":[
                                  {"setNumber":1,"weightKg":%d,"reps":%d}
                                ]}
                                """.formatted(name, weight, reps)))
                .andExpect(status().isCreated());
    }

    private static String loadXqDbImage() {
        try (InputStream in = ExerciseApiIntegrationTests.class.getResourceAsStream("/xq-db.properties")) {
            if (in == null) {
                throw new IllegalStateException("classpath:/xq-db.properties not found");
            }
            Properties properties = new Properties();
            properties.load(in);
            String image = properties.getProperty("xq.db.postgres.image");
            if (image == null || image.isBlank()) {
                throw new IllegalStateException("xq.db.postgres.image missing from xq-db.properties");
            }
            return image.trim();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load xq-db.properties", ex);
        }
    }
}
