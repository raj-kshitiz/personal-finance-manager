package com.pfm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Full-stack integration test against a real embedded servlet container, the real Spring Security
 * filter chain and a real H2 database, driven over HTTP with a real {@code JSESSIONID} cookie —
 * exactly how the official {@code financial_manager_tests.sh} script exercises the API.
 *
 * <p>Covers the register &rarr; login &rarr; transact &rarr; goal &rarr; report &rarr; logout
 * journey, rejection of a cookie reused after logout, data isolation between two users, and the
 * guarantee that no known bad input produces a 5xx.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndToEndFlowTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;

    /** A logged-in session, identified solely by its {@code Set-Cookie} value. */
    private record Session(String cookie) {
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "." + System.nanoTime() + "@example.com";
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String body, Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (session != null) {
            headers.add(HttpHeaders.COOKIE, session.cookie());
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, Session session) {
        return call(HttpMethod.GET, path, null, session);
    }

    private static JsonNode json(ResponseEntity<String> response) {
        try {
            return MAPPER.readTree(response.getBody());
        } catch (Exception ex) {
            throw new AssertionError("response body was not JSON: " + response.getBody(), ex);
        }
    }

    private static int status(ResponseEntity<String> response) {
        return response.getStatusCode().value();
    }

    private Session registerAndLogin(String email) {
        ResponseEntity<String> registered = call(HttpMethod.POST, "/api/auth/register", """
                {"username":"%s","password":"securePassword123",
                 "fullName":"Test User","phoneNumber":"+1234567890"}
                """.formatted(email), null);
        assertThat(status(registered)).isEqualTo(201);
        assertThat(json(registered).get("message").asText()).isEqualTo("User registered successfully");
        assertThat(json(registered).get("userId").isNumber()).isTrue();

        ResponseEntity<String> loggedIn = call(HttpMethod.POST, "/api/auth/login",
                "{\"username\":\"%s\",\"password\":\"securePassword123\"}".formatted(email), null);
        assertThat(status(loggedIn)).isEqualTo(200);
        assertThat(json(loggedIn).get("message").asText()).isEqualTo("Login successful");

        List<String> cookies = loggedIn.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("login must issue a session cookie").isNotNull().isNotEmpty();
        String sessionCookie = cookies.stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no JSESSIONID in " + cookies));
        assertThat(sessionCookie).contains("HttpOnly");
        return new Session(sessionCookie.substring(0, sessionCookie.indexOf(';')));
    }

    @Test
    @DisplayName("register, login, transact, goal, report, logout, old cookie rejected")
    void fullJourney() {
        Session session = registerAndLogin(uniqueEmail("journey"));

        // Default categories are visible under both property spellings.
        ResponseEntity<String> categories = get("/api/categories", session);
        assertThat(status(categories)).isEqualTo(200);
        assertThat(categories.getBody()).contains("\"isCustom\":false").contains("\"custom\":false");
        assertThat(categories.getBody()).contains("Salary").contains("Food").contains("Rent");

        // Create one income and one expense transaction.
        ResponseEntity<String> income = call(HttpMethod.POST, "/api/transactions", """
                {"amount":5000.00,"date":"2024-01-15","category":"Salary","description":"January Salary"}
                """, session);
        assertThat(status(income)).isEqualTo(201);
        assertThat(income.getBody()).contains("\"amount\":5000.00").contains("\"type\":\"INCOME\"");
        assertThat(json(income).get("date").asText()).isEqualTo("2024-01-15");

        ResponseEntity<String> expense = call(HttpMethod.POST, "/api/transactions", """
                {"amount":1000.00,"date":"2024-01-20","category":"Food","description":"Groceries"}
                """, session);
        assertThat(status(expense)).isEqualTo(201);
        assertThat(json(expense).get("type").asText()).isEqualTo("EXPENSE");
        long expenseId = json(expense).get("id").asLong();

        // The date is immutable: sending it is accepted (200) but ignored.
        ResponseEntity<String> updated = call(HttpMethod.PUT, "/api/transactions/" + expenseId,
                "{\"date\":\"2024-02-02\",\"amount\":1200.00}", session);
        assertThat(status(updated)).isEqualTo(200);
        assertThat(json(updated).get("date").asText()).isEqualTo("2024-01-20");
        assertThat(updated.getBody()).contains("\"amount\":1200.00");

        // A body containing only `date` is still a 200 no-op.
        ResponseEntity<String> dateOnly = call(HttpMethod.PUT, "/api/transactions/" + expenseId,
                "{\"date\":\"2024-03-03\"}", session);
        assertThat(status(dateOnly)).isEqualTo(200);
        assertThat(json(dateOnly).get("date").asText()).isEqualTo("2024-01-20");

        // Exactly two transactions, newest first.
        ResponseEntity<String> list = get("/api/transactions", session);
        assertThat(status(list)).isEqualTo(200);
        assertThat(countOccurrences(list.getBody(), "\"id\":")).isEqualTo(2);
        assertThat(json(list).get("transactions").get(0).get("date").asText()).isEqualTo("2024-01-20");

        // Filtering by category name and by date range.
        assertThat(countOccurrences(get("/api/transactions?category=Salary", session).getBody(), "\"id\":"))
                .isEqualTo(1);
        assertThat(countOccurrences(
                get("/api/transactions?startDate=2024-01-15&endDate=2024-01-15", session).getBody(), "\"id\":"))
                .isEqualTo(1);
        assertThat(countOccurrences(get("/api/transactions?type=EXPENSE", session).getBody(), "\"id\":"))
                .isEqualTo(1);

        // Goal progress: 5000 income - 1200 expense = 3800 of 10000 => 38.0 %.
        ResponseEntity<String> goal = call(HttpMethod.POST, "/api/goals", """
                {"goalName":"Emergency Fund","targetAmount":10000.00,
                 "targetDate":"2030-01-01","startDate":"2024-01-01"}
                """, session);
        assertThat(status(goal)).isEqualTo(201);
        assertThat(goal.getBody())
                .contains("\"currentProgress\":3800.00")
                .contains("\"progressPercentage\":38.0")
                .contains("\"remainingAmount\":6200.00");

        // A goal whose window contains nothing renders an exact zero as `0`, not `0.00`.
        ResponseEntity<String> emptyGoal = call(HttpMethod.POST, "/api/goals", """
                {"goalName":"Future Fund","targetAmount":5000.00,
                 "targetDate":"2030-01-01","startDate":"2025-01-01"}
                """, session);
        assertThat(status(emptyGoal)).isEqualTo(201);
        assertThat(emptyGoal.getBody())
                .contains("\"currentProgress\":0,")
                .contains("\"progressPercentage\":0.0");

        // Monthly and yearly reports agree with the transactions.
        ResponseEntity<String> monthly = get("/api/reports/monthly/2024/1", session);
        assertThat(status(monthly)).isEqualTo(200);
        JsonNode monthlyBody = json(monthly);
        assertThat(monthlyBody.get("month").asInt()).isEqualTo(1);
        assertThat(monthlyBody.get("year").asInt()).isEqualTo(2024);
        assertThat(monthlyBody.get("totalIncome").get("Salary").decimalValue())
                .isEqualByComparingTo("5000.00");
        assertThat(monthlyBody.get("totalExpenses").get("Food").decimalValue())
                .isEqualByComparingTo("1200.00");
        assertThat(monthly.getBody())
                .contains("\"Salary\":5000.00")
                .contains("\"Food\":1200.00")
                .contains("\"netSavings\":3800.00");

        assertThat(get("/api/reports/yearly/2024", session).getBody()).contains("\"netSavings\":3800.00");
        assertThat(get("/api/reports/monthly/2024/12", session).getBody()).contains("\"netSavings\":0");
        assertThat(get("/api/reports/yearly/2023", session).getBody()).contains("\"netSavings\":0");

        // Deleting a transaction removes it from both goals and reports.
        ResponseEntity<String> deleted = call(HttpMethod.DELETE, "/api/transactions/" + expenseId, null, session);
        assertThat(status(deleted)).isEqualTo(200);
        assertThat(json(deleted).get("message").asText()).isEqualTo("Transaction deleted successfully");

        assertThat(get("/api/reports/monthly/2024/1", session).getBody())
                .doesNotContain("Food")
                .contains("\"netSavings\":5000.00");
        assertThat(get("/api/goals", session).getBody()).contains("\"currentProgress\":5000.00");

        // Logout invalidates the session; the old cookie is worthless afterwards.
        ResponseEntity<String> loggedOut = call(HttpMethod.POST, "/api/auth/logout", null, session);
        assertThat(status(loggedOut)).isEqualTo(200);

        for (String path : new String[]{"/api/categories", "/api/transactions", "/api/goals",
                "/api/reports/yearly/2024"}) {
            ResponseEntity<String> afterLogout = get(path, session);
            assertThat(status(afterLogout)).as("%s after logout", path).isEqualTo(401);
            assertThat(json(afterLogout).get("status").asInt()).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("users cannot see or touch each other's data")
    void dataIsolation() {
        Session alice = registerAndLogin(uniqueEmail("alice"));
        Session bob = registerAndLogin(uniqueEmail("bob"));

        String categoryName = "Consulting" + System.nanoTime();
        ResponseEntity<String> created = call(HttpMethod.POST, "/api/categories",
                "{\"name\":\"%s\",\"type\":\"INCOME\"}".formatted(categoryName), alice);
        assertThat(status(created)).isEqualTo(201);
        assertThat(created.getBody()).contains("\"isCustom\":true").contains("\"custom\":true");

        ResponseEntity<String> aliceTx = call(HttpMethod.POST, "/api/transactions", """
                {"amount":900.00,"date":"2024-03-01","category":"%s","description":"Alice only"}
                """.formatted(categoryName), alice);
        assertThat(status(aliceTx)).isEqualTo(201);
        long aliceTxId = json(aliceTx).get("id").asLong();

        ResponseEntity<String> aliceGoal = call(HttpMethod.POST, "/api/goals", """
                {"goalName":"Alice Goal","targetAmount":1000.00,"targetDate":"2030-06-01"}
                """, alice);
        assertThat(status(aliceGoal)).isEqualTo(201);
        long aliceGoalId = json(aliceGoal).get("id").asLong();
        assertThat(json(aliceGoal).get("startDate").asText())
                .as("startDate defaults to today")
                .isIn(LocalDate.now().toString(), LocalDate.now().minusDays(1).toString(),
                        LocalDate.now().plusDays(1).toString());

        // Bob sees none of it.
        assertThat(get("/api/categories", bob).getBody()).doesNotContain(categoryName);
        assertThat(get("/api/transactions", bob).getBody()).contains("\"transactions\":[]");
        assertThat(get("/api/goals", bob).getBody()).contains("\"goals\":[]");

        // Another user's transaction is indistinguishable from a missing one (404).
        assertThat(status(call(HttpMethod.PUT, "/api/transactions/" + aliceTxId,
                "{\"amount\":1.00}", bob))).isEqualTo(404);
        assertThat(status(call(HttpMethod.DELETE, "/api/transactions/" + aliceTxId, null, bob))).isEqualTo(404);

        // Another user's goal is 403 per the assignment.
        assertThat(status(get("/api/goals/" + aliceGoalId, bob))).isEqualTo(403);
        assertThat(status(call(HttpMethod.PUT, "/api/goals/" + aliceGoalId,
                "{\"targetAmount\":1.00}", bob))).isEqualTo(403);
        assertThat(status(call(HttpMethod.DELETE, "/api/goals/" + aliceGoalId, null, bob))).isEqualTo(403);

        // Bob may reuse the same custom category name for himself.
        assertThat(status(call(HttpMethod.POST, "/api/categories",
                "{\"name\":\"%s\",\"type\":\"INCOME\"}".formatted(categoryName), bob))).isEqualTo(201);

        // Alice is unaffected.
        assertThat(get("/api/transactions", alice).getBody()).contains("Alice only");
        assertThat(get("/api/goals/" + aliceGoalId, alice).getBody()).contains("Alice Goal");
    }

    @Test
    @DisplayName("every protected endpoint rejects an anonymous caller with a JSON 401")
    void anonymousAccessIsRejected() {
        for (String path : new String[]{"/api/categories", "/api/transactions", "/api/goals",
                "/api/goals/1", "/api/reports/monthly/2024/1", "/api/reports/yearly/2024"}) {
            ResponseEntity<String> response = get(path, null);
            assertThat(status(response)).as(path).isEqualTo(401);
            assertThat(json(response).get("status").asInt()).isEqualTo(401);
            assertThat(json(response).get("path").asText()).isEqualTo(path);
            assertThat(response.getBody()).doesNotContain("Exception").doesNotContain("\tat ");
        }
        assertThat(status(call(HttpMethod.POST, "/api/auth/logout", null, null))).isEqualTo(401);
        assertThat(status(call(HttpMethod.POST, "/api/transactions",
                "{\"amount\":1,\"date\":\"2024-01-01\",\"category\":\"Food\"}", null))).isEqualTo(401);
    }

    @Test
    @DisplayName("known bad input never produces a 5xx and never leaks a stack trace")
    void knownBadInputStaysIn4xx() {
        Session session = registerAndLogin(uniqueEmail("fuzz"));
        String future = LocalDate.now().plusYears(5).toString();

        record Case(HttpMethod method, String path, String body, int expected) {
        }

        List<Case> cases = List.of(
                new Case(HttpMethod.POST, "/api/transactions", "{", 400),
                new Case(HttpMethod.POST, "/api/transactions", "", 400),
                new Case(HttpMethod.POST, "/api/transactions", "{}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":100,\"date\":\"01-17-2024\",\"category\":\"Food\"}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":-100,\"date\":\"2024-01-01\",\"category\":\"Food\"}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":0,\"date\":\"2024-01-01\",\"category\":\"Food\"}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":1.234,\"date\":\"2024-01-01\",\"category\":\"Food\"}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":\"lots\",\"date\":\"2024-01-01\",\"category\":\"Food\"}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":99999999999999999999,\"date\":\"2024-01-01\",\"category\":\"Food\"}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":10,\"date\":\"" + future + "\",\"category\":\"Food\"}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":10,\"date\":\"2024-01-01\",\"category\":\"NoSuchCategory\"}", 400),
                new Case(HttpMethod.POST, "/api/transactions",
                        "{\"amount\":10,\"date\":\"2024-01-01\",\"category\":\"' OR 1=1 --\"}", 400),
                new Case(HttpMethod.POST, "/api/categories", "{\"name\":\"X\",\"type\":\"SIDEWAYS\"}", 400),
                new Case(HttpMethod.POST, "/api/categories", "{\"name\":\"\",\"type\":\"INCOME\"}", 400),
                new Case(HttpMethod.POST, "/api/categories", "{\"name\":\"Food\",\"type\":\"EXPENSE\"}", 409),
                new Case(HttpMethod.POST, "/api/categories", "{\"name\":\"food\",\"type\":\"EXPENSE\"}", 409),
                new Case(HttpMethod.POST, "/api/goals",
                        "{\"goalName\":\"G\",\"targetAmount\":100,\"targetDate\":\"2000-01-01\"}", 400),
                new Case(HttpMethod.POST, "/api/goals",
                        "{\"goalName\":\"G\",\"targetAmount\":0,\"targetDate\":\"2030-01-01\"}", 400),
                new Case(HttpMethod.POST, "/api/goals",
                        "{\"goalName\":\"G\",\"targetAmount\":-5,\"targetDate\":\"2030-01-01\"}", 400),
                new Case(HttpMethod.POST, "/api/goals", "{\"goalName\":\"G\",\"targetAmount\":100,"
                        + "\"targetDate\":\"2030-01-01\",\"startDate\":\"2031-01-01\"}", 400),
                new Case(HttpMethod.PUT, "/api/goals/999999", "{\"targetAmount\":10}", 404),
                new Case(HttpMethod.PUT, "/api/transactions/999999", "{\"amount\":10}", 404),
                new Case(HttpMethod.DELETE, "/api/transactions/999999", null, 404),
                new Case(HttpMethod.DELETE, "/api/transactions/abc", null, 400),
                new Case(HttpMethod.DELETE, "/api/goals/abc", null, 400),
                new Case(HttpMethod.PUT, "/api/transactions/abc", "{\"amount\":10}", 400),
                new Case(HttpMethod.DELETE, "/api/goals/999999", null, 404),
                new Case(HttpMethod.DELETE, "/api/categories/Food", null, 403),
                new Case(HttpMethod.DELETE, "/api/categories/NoSuchCategory", null, 404),
                new Case(HttpMethod.POST, "/api/auth/register",
                        "{\"username\":\"not-an-email\",\"password\":\"password123\","
                                + "\"fullName\":\"X\",\"phoneNumber\":\"+1234567890\"}", 400),
                new Case(HttpMethod.POST, "/api/auth/register",
                        "{\"username\":\"a@b.com\",\"password\":\"short\","
                                + "\"fullName\":\"X\",\"phoneNumber\":\"+1234567890\"}", 400),
                new Case(HttpMethod.POST, "/api/auth/register",
                        "{\"username\":\"a@b.com\",\"password\":\"password123\","
                                + "\"fullName\":\"X\",\"phoneNumber\":\"abc\"}", 400),
                new Case(HttpMethod.POST, "/api/auth/login",
                        "{\"username\":\"nobody@example.com\",\"password\":\"password123\"}", 401));

        for (Case testCase : cases) {
            ResponseEntity<String> response = call(testCase.method(), testCase.path(), testCase.body(), session);
            assertThat(status(response))
                    .as("%s %s with body %s", testCase.method(), testCase.path(), testCase.body())
                    .isEqualTo(testCase.expected());
            assertThat(response.getBody())
                    .as("no stack trace must leak for %s", testCase.path())
                    .doesNotContain("\tat ")
                    .doesNotContain("java.lang.")
                    .doesNotContain("org.hibernate");
        }

        // Bad path and query parameter types.
        record Query(String path, int expected) {
        }
        List<Query> queries = List.of(
                new Query("/api/transactions?startDate=not-a-date", 400),
                new Query("/api/transactions?endDate=2024-13-45", 400),
                new Query("/api/transactions?startDate=2024-02-01&endDate=2024-01-01", 400),
                new Query("/api/transactions?categoryId=abc", 400),
                new Query("/api/transactions?categoryId=999999", 404),
                new Query("/api/transactions?type=TRANSFER", 400),
                new Query("/api/reports/monthly/2024/13", 400),
                new Query("/api/reports/monthly/2024/0", 400),
                new Query("/api/reports/monthly/2024/abc", 400),
                new Query("/api/reports/yearly/abcd", 400),
                new Query("/api/reports/yearly/1969", 400),
                new Query("/api/reports/yearly/2101", 400),
                new Query("/api/goals/abc", 400),
                new Query("/api/goals/999999", 404));
        for (Query query : queries) {
            ResponseEntity<String> response = get(query.path(), session);
            assertThat(status(response)).as(query.path()).isEqualTo(query.expected());
        }

        // An oversized description is rejected rather than truncated or exploding.
        assertThat(status(call(HttpMethod.POST, "/api/transactions",
                "{\"amount\":10,\"date\":\"2024-01-01\",\"category\":\"Food\",\"description\":\""
                        + "x".repeat(600) + "\"}", session))).isEqualTo(400);
    }

    @Test
    @DisplayName("actuator health is public and reports UP")
    void healthIsPublic() {
        ResponseEntity<String> health = get("/actuator/health", null);
        assertThat(status(health)).isEqualTo(200);
        assertThat(json(health).get("status").asText()).isEqualTo("UP");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
