package com.example.seatreservation;

import com.example.seatreservation.dto.CancelReservationResponse;
import com.example.seatreservation.dto.CreateShowRequest;
import com.example.seatreservation.dto.ErrorResponse;
import com.example.seatreservation.dto.ReservationResponse;
import com.example.seatreservation.dto.ReserveSeatsRequest;
import com.example.seatreservation.dto.ShowResponse;
import com.example.seatreservation.entity.ReservationStatus;
import com.example.seatreservation.entity.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ReservationIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("1. Create show successfully with seat inventory")
    void testCreateShow() {
        CreateShowRequest request = new CreateShowRequest("rock-concert", List.of("A1", "A2", "A3"), 50000L);
        ResponseEntity<ShowResponse> response = restTemplate.postForEntity("/shows", request, ShowResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("rock-concert", response.getBody().getName());
        assertEquals(3, response.getBody().getTotalSeats());
        assertEquals(3, response.getBody().getAvailableCount());
        assertEquals(0, response.getBody().getConfirmedCount());
    }

    @Test
    @DisplayName("2. Reserve available seat successfully")
    void testReserveAvailableSeat() {
        ShowResponse show = createTestShow("show-single-res", List.of("A1", "A2"));

        ReserveSeatsRequest reserveRequest = new ReserveSeatsRequest(List.of("A1"), "key-user1-001");
        HttpHeaders headers = createAuthHeaders("user-1");

        ResponseEntity<ReservationResponse> response = restTemplate.exchange(
                "/shows/" + show.getId() + "/reserve",
                HttpMethod.POST,
                new HttpEntity<>(reserveRequest, headers),
                ReservationResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("user-1", response.getBody().getUserId());
        assertEquals(List.of("A1"), response.getBody().getSeats());
        assertEquals(ReservationStatus.CONFIRMED, response.getBody().getStatus());
    }

    @Test
    @DisplayName("3. Reserve already taken seat returns 409 Conflict")
    void testReserveAlreadyTakenSeat() {
        ShowResponse show = createTestShow("show-already-taken", List.of("A1", "A2"));

        // User 1 reserves A1
        ReserveSeatsRequest req1 = new ReserveSeatsRequest(List.of("A1"), "key-user1-a1");
        restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(req1, createAuthHeaders("user-1")), ReservationResponse.class);

        // User 2 attempts to reserve A1
        ReserveSeatsRequest req2 = new ReserveSeatsRequest(List.of("A1"), "key-user2-a1");
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/shows/" + show.getId() + "/reserve",
                HttpMethod.POST,
                new HttpEntity<>(req2, createAuthHeaders("user-2")),
                ErrorResponse.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SEAT_ALREADY_TAKEN", response.getBody().getCode());
    }

    @Test
    @DisplayName("4. Idempotent retry returns original reservation response")
    void testIdempotentRetry() {
        ShowResponse show = createTestShow("show-idempotency", List.of("A1", "A2"));

        ReserveSeatsRequest req = new ReserveSeatsRequest(List.of("A1"), "idempotent-key-999");
        HttpHeaders headers = createAuthHeaders("user-1");

        // First attempt
        ResponseEntity<ReservationResponse> res1 = restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(req, headers), ReservationResponse.class);
        assertEquals(HttpStatus.CREATED, res1.getStatusCode());

        // Second attempt with exact same key & body
        ResponseEntity<ReservationResponse> res2 = restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(req, headers), ReservationResponse.class);
        assertEquals(HttpStatus.CREATED, res2.getStatusCode());
        assertNotNull(res2.getBody());
        assertEquals(res1.getBody().getReservationId(), res2.getBody().getReservationId());
    }

    @Test
    @DisplayName("5. Idempotency key reuse with different seats returns 409 Conflict")
    void testIdempotencyKeyConflict() {
        ShowResponse show = createTestShow("show-idempotency-conflict", List.of("A1", "A2"));

        HttpHeaders headers = createAuthHeaders("user-1");
        ReserveSeatsRequest req1 = new ReserveSeatsRequest(List.of("A1"), "same-key-123");
        restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(req1, headers), ReservationResponse.class);

        // Reuse same key with different seats payload ["A2"]
        ReserveSeatsRequest req2 = new ReserveSeatsRequest(List.of("A2"), "same-key-123");
        ResponseEntity<ErrorResponse> res2 = restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(req2, headers), ErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, res2.getStatusCode());
        assertNotNull(res2.getBody());
        assertEquals("IDEMPOTENCY_CONFLICT", res2.getBody().getCode());
    }

    @Test
    @DisplayName("6. Per-user seat limit (max 4 seats) is strictly enforced")
    void testPerUserSeatLimit() {
        ShowResponse show = createTestShow("show-user-limit", List.of("A1", "A2", "A3", "A4", "A5"));
        HttpHeaders headers = createAuthHeaders("user-limit-tester");

        // Reserve 3 seats
        ReserveSeatsRequest req1 = new ReserveSeatsRequest(List.of("A1", "A2", "A3"), "limit-key-1");
        restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(req1, headers), ReservationResponse.class);

        // Attempting to reserve 2 more seats (total 5 > 4 limit)
        ReserveSeatsRequest req2 = new ReserveSeatsRequest(List.of("A4", "A5"), "limit-key-2");
        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(req2, headers), ErrorResponse.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PER_USER_LIMIT_EXCEEDED", response.getBody().getCode());
    }

    @Test
    @DisplayName("7. Concurrent requests by same user cannot bypass seat limit")
    void testConcurrentUserSeatLimit() throws InterruptedException {
        ShowResponse show = createTestShow("show-concurrent-user-limit", List.of("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"));
        String userId = "concurrent-user-1";

        int numRequests = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numRequests);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger limitExceededCount = new AtomicInteger(0);

        for (int i = 1; i <= numRequests; i++) {
            final String seatNum = "S" + i;
            final String key = "user-limit-key-" + i;
            executor.submit(() -> {
                try {
                    latch.await();
                    ReserveSeatsRequest req = new ReserveSeatsRequest(List.of(seatNum), key);
                    ResponseEntity<String> res = restTemplate.exchange(
                            "/shows/" + show.getId() + "/reserve",
                            HttpMethod.POST,
                            new HttpEntity<>(req, createAuthHeaders(userId)),
                            String.class
                    );
                    if (res.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (res.getStatusCode() == HttpStatus.CONFLICT && res.getBody() != null && res.getBody().contains("PER_USER_LIMIT_EXCEEDED")) {
                        limitExceededCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // Fire all 8 threads simultaneously
        doneLatch.await();
        executor.shutdown();

        assertEquals(4, successCount.get(), "Exactly 4 reservations should succeed for max 4 seat limit");
        assertEquals(4, limitExceededCount.get(), "Remaining 4 concurrent requests must be rejected with PER_USER_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("8. Hot-seat concurrent reservation storm guarantees single winner")
    void testConcurrentHotSeatStorm() throws InterruptedException {
        ShowResponse show = createTestShow("show-hotseat", List.of("HOT_SEAT_1"));

        int concurrentUsers = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger seatTakenCount = new AtomicInteger(0);

        for (int i = 1; i <= concurrentUsers; i++) {
            final String userId = "user-storm-" + i;
            final String key = "key-storm-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ReserveSeatsRequest req = new ReserveSeatsRequest(List.of("HOT_SEAT_1"), key);
                    ResponseEntity<String> res = restTemplate.exchange(
                            "/shows/" + show.getId() + "/reserve",
                            HttpMethod.POST,
                            new HttpEntity<>(req, createAuthHeaders(userId)),
                            String.class
                    );
                    if (res.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (res.getStatusCode() == HttpStatus.CONFLICT && res.getBody() != null && res.getBody().contains("SEAT_ALREADY_TAKEN")) {
                        seatTakenCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all 10 concurrent requests at once
        endLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly 1 reservation must succeed for HOT_SEAT_1");
        assertEquals(9, seatTakenCount.get(), "9 concurrent attempts must fail with SEAT_ALREADY_TAKEN");
    }

    @Test
    @DisplayName("9. Multi-seat reservation all-or-nothing atomicity")
    void testMultiSeatAllOrNothing() {
        ShowResponse show = createTestShow("show-multi-seat", List.of("A12", "A13"));

        // User 1 reserves A12
        restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(new ReserveSeatsRequest(List.of("A12"), "k1"), createAuthHeaders("user-1")), ReservationResponse.class);

        // User 2 requests ["A12", "A13"]
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/shows/" + show.getId() + "/reserve",
                HttpMethod.POST,
                new HttpEntity<>(new ReserveSeatsRequest(List.of("A12", "A13"), "k2"), createAuthHeaders("user-2")),
                ErrorResponse.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("SEAT_ALREADY_TAKEN", response.getBody().getCode());

        // Verify A13 remains AVAILABLE
        ShowResponse state = restTemplate.getForObject("/shows/" + show.getId(), ShowResponse.class);
        assertEquals(1, state.getAvailableCount());
        assertTrue(state.getSeats().stream().anyMatch(s -> s.getSeatNumber().equals("A13") && s.getStatus() == SeatStatus.AVAILABLE));
    }

    @Test
    @DisplayName("10. Cancellation releases seats; cross-user cancellation returns 403 Forbidden")
    void testCancellationAndForbiddenCheck() {
        ShowResponse show = createTestShow("show-cancel", List.of("C1"));

        // User 1 reserves C1
        ResponseEntity<ReservationResponse> res = restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(new ReserveSeatsRequest(List.of("C1"), "cancel-k1"), createAuthHeaders("user-1")), ReservationResponse.class);
        UUID reservationId = res.getBody().getReservationId();

        // User 2 attempts to cancel User 1's reservation -> 403 Forbidden
        ResponseEntity<ErrorResponse> forbiddenRes = restTemplate.exchange(
                "/reservations/" + reservationId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(createAuthHeaders("user-2")),
                ErrorResponse.class
        );
        assertEquals(HttpStatus.FORBIDDEN, forbiddenRes.getStatusCode());

        // Owner User 1 cancels reservation -> 200 OK
        ResponseEntity<CancelReservationResponse> cancelRes = restTemplate.exchange(
                "/reservations/" + reservationId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(createAuthHeaders("user-1")),
                CancelReservationResponse.class
        );
        assertEquals(HttpStatus.OK, cancelRes.getStatusCode());
        assertEquals(ReservationStatus.CANCELLED, cancelRes.getBody().getStatus());

        // Verify C1 is AVAILABLE again
        ShowResponse showState = restTemplate.getForObject("/shows/" + show.getId(), ShowResponse.class);
        assertEquals(1, showState.getAvailableCount());
    }

    @Test
    @DisplayName("11. Show state reconciliation invariant available + held + confirmed == total")
    void testShowStateReconciliationInvariant() {
        ShowResponse show = createTestShow("show-invariant", List.of("X1", "X2", "X3", "X4"));

        // Reserve X1, X2
        restTemplate.exchange("/shows/" + show.getId() + "/reserve", HttpMethod.POST, new HttpEntity<>(new ReserveSeatsRequest(List.of("X1", "X2"), "inv-k1"), createAuthHeaders("user-1")), ReservationResponse.class);

        ShowResponse state = restTemplate.getForObject("/shows/" + show.getId(), ShowResponse.class);
        assertNotNull(state);
        assertEquals(4, state.getTotalSeats());
        assertEquals(2, state.getAvailableCount());
        assertEquals(0, state.getHeldCount());
        assertEquals(2, state.getConfirmedCount());

        // Invariant check: available + held + confirmed == total_seats
        assertEquals(state.getTotalSeats(), state.getAvailableCount() + state.getHeldCount() + state.getConfirmedCount());
    }

    private ShowResponse createTestShow(String name, List<String> seats) {
        CreateShowRequest request = new CreateShowRequest(name, seats, 25000L);
        return restTemplate.postForObject("/shows", request, ShowResponse.class);
    }

    private HttpHeaders createAuthHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId);
        return headers;
    }
}
