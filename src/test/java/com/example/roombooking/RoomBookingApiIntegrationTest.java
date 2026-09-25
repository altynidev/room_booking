package com.example.roombooking;

import com.example.roombooking.entity.Booking;
import com.example.roombooking.entity.BookingStatus;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.RoomRepository;
import com.example.roombooking.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoomBookingApiIntegrationTest {

    /** A whole day in the future, at 00:00 UTC, so tests never collide with "now". */
    private static final OffsetDateTime DAY = OffsetDateTime.now(ZoneOffset.UTC)
            .plusDays(10).truncatedTo(ChronoUnit.DAYS);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookingRepository bookingRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired UserRepository userRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !u.getUsername().equals("admin"))
                .forEach(userRepository::delete);
        adminToken = login("admin", "admin123");
    }

    // ---------- auth ----------

    @Test
    void registerReturnsUserWithoutPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice", "password", "secret123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        register("alice");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice", "password", "secret123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordReturns401WithErrorBody() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "nope"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mvc.perform(get("/api/rooms").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- rooms ----------

    @Test
    void userCannotCreateRoom() throws Exception {
        String token = registerAndLogin("alice");
        mvc.perform(post("/api/rooms").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Orion", "capacity", 8, "floor", 2))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminManagesRooms() throws Exception {
        long id = createRoom("Orion");

        mvc.perform(post("/api/rooms").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Orion", "capacity", 4, "floor", 1))))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/rooms/" + id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Orion XL", "capacity", 20, "floor", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Orion XL"))
                .andExpect(jsonPath("$.capacity").value(20));

        String userToken = registerAndLogin("alice");
        mvc.perform(get("/api/rooms/" + id).header("Authorization", bearer(userToken)))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/rooms/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/rooms/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void invalidRoomReturns400() throws Exception {
        mvc.perform(post("/api/rooms").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "", "capacity", 0, "floor", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void roomWithBookingsCannotBeDeleted() throws Exception {
        long roomId = createRoom("Orion");
        String token = registerAndLogin("alice");
        book(token, roomId, DAY.plusHours(9), DAY.plusHours(10)).andExpect(status().isCreated());

        mvc.perform(delete("/api/rooms/" + roomId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    // ---------- bookings ----------

    @Test
    void overlappingBookingReturns409ButAdjacentIsAllowed() throws Exception {
        long roomId = createRoom("Orion");
        String alice = registerAndLogin("alice");
        String bob = registerAndLogin("bob");

        book(alice, roomId, DAY.plusHours(9), DAY.plusHours(10)).andExpect(status().isCreated());
        book(bob, roomId, DAY.plusHours(9).plusMinutes(30), DAY.plusHours(11))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        book(bob, roomId, DAY.plusHours(8), DAY.plusHours(12)).andExpect(status().isConflict());
        book(bob, roomId, DAY.plusHours(10), DAY.plusHours(11)).andExpect(status().isCreated());
        book(bob, roomId, DAY.plusHours(8), DAY.plusHours(9)).andExpect(status().isCreated());
    }

    @Test
    void cancelledBookingFreesTheSlot() throws Exception {
        long roomId = createRoom("Orion");
        String alice = registerAndLogin("alice");

        long bookingId = id(book(alice, roomId, DAY.plusHours(9), DAY.plusHours(10)));
        mvc.perform(patch("/api/bookings/" + bookingId + "/cancel").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        book(alice, roomId, DAY.plusHours(9), DAY.plusHours(10)).andExpect(status().isCreated());
    }

    @Test
    void invalidTimesReturn400() throws Exception {
        long roomId = createRoom("Orion");
        String alice = registerAndLogin("alice");

        book(alice, roomId, DAY.plusHours(10), DAY.plusHours(9)).andExpect(status().isBadRequest());
        book(alice, roomId, DAY.plusHours(10), DAY.plusHours(10)).andExpect(status().isBadRequest());
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        book(alice, roomId, past, past.plusHours(1)).andExpect(status().isBadRequest());
    }

    @Test
    void bookingUnknownRoomReturns404() throws Exception {
        String alice = registerAndLogin("alice");
        book(alice, 999_999L, DAY.plusHours(9), DAY.plusHours(10)).andExpect(status().isNotFound());
    }

    @Test
    void usersCanCancelOnlyOwnBookingsAdminCanCancelAny() throws Exception {
        long roomId = createRoom("Orion");
        String alice = registerAndLogin("alice");
        String bob = registerAndLogin("bob");

        long aliceBooking = id(book(alice, roomId, DAY.plusHours(9), DAY.plusHours(10)));
        long aliceBooking2 = id(book(alice, roomId, DAY.plusHours(11), DAY.plusHours(12)));

        mvc.perform(patch("/api/bookings/" + aliceBooking + "/cancel").header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mvc.perform(patch("/api/bookings/" + aliceBooking + "/cancel").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/bookings/" + aliceBooking + "/cancel").header("Authorization", bearer(alice)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/bookings/my").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].id").value(aliceBooking2));
        mvc.perform(get("/api/bookings/my").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void availabilityListsBookedAndFreeSlots() throws Exception {
        long roomId = createRoom("Orion");
        String alice = registerAndLogin("alice");
        book(alice, roomId, DAY.plusHours(9), DAY.plusHours(10)).andExpect(status().isCreated());
        book(alice, roomId, DAY.plusHours(14), DAY.plusHours(15)).andExpect(status().isCreated());

        mvc.perform(get("/api/rooms/" + roomId + "/availability")
                        .param("date", DAY.toLocalDate().toString())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("Orion"))
                .andExpect(jsonPath("$.bookedSlots", hasSize(2)))
                .andExpect(jsonPath("$.freeSlots", hasSize(3)));

        mvc.perform(get("/api/rooms/" + roomId + "/availability")
                        .param("date", "not-a-date")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseRejectsOverlappingActiveBookings() throws Exception {
        long roomId = createRoom("Orion");
        String alice = registerAndLogin("alice");
        book(alice, roomId, DAY.plusHours(9), DAY.plusHours(10)).andExpect(status().isCreated());

        Booking duplicate = Booking.builder()
                .room(roomRepository.findById(roomId).orElseThrow())
                .user(userRepository.findByUsername("alice").orElseThrow())
                .startTime(DAY.plusHours(9).plusMinutes(15))
                .endTime(DAY.plusHours(9).plusMinutes(45))
                .status(BookingStatus.ACTIVE)
                .build();
        assertThatThrownBy(() -> bookingRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private void register(String username) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "secret123"))))
                .andExpect(status().isCreated());
    }

    private String registerAndLogin(String username) throws Exception {
        register(username);
        return login(username, "secret123");
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private long createRoom(String name) throws Exception {
        String body = mvc.perform(post("/api/rooms").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "capacity", 8, "floor", 2))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private ResultActions book(String token, long roomId, OffsetDateTime start, OffsetDateTime end) throws Exception {
        return mvc.perform(post("/api/bookings").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("roomId", roomId, "startTime", start.toString(), "endTime", end.toString()))));
    }

    private long id(ResultActions result) throws Exception {
        JsonNode node = objectMapper.readTree(result.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
