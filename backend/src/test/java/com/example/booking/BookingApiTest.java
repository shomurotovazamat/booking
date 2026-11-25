package com.example.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingApiTest {

    private static final LocalDate DAY = LocalDate.now().plusDays(7);

    @Autowired
    private MockMvc mvc;

    private ResultActions book(long resourceId, int fromHour, int toHour, String email) throws Exception {
        String body = """
                {"resourceId": %d, "customerName": "Иван Петров", "customerEmail": "%s",
                 "startTime": "%sT%02d:00:00", "endTime": "%sT%02d:00:00"}
                """.formatted(resourceId, email, DAY, fromHour, DAY, toHour);
        return mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void resourcesAreSeeded() throws Exception {
        mvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void overlappingBookingIsRejected() throws Exception {
        book(1, 10, 12, "a@example.com").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        book(1, 11, 13, "b@example.com").andExpect(status().isConflict());
        // Соседний интервал не пересекается
        book(1, 12, 13, "b@example.com").andExpect(status().isCreated());

        mvc.perform(get("/api/resources/1/availability").param("date", DAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(12))
                .andExpect(jsonPath("$.slots[0].free").value(true))   // 09:00
                .andExpect(jsonPath("$.slots[1].free").value(false))  // 10:00
                .andExpect(jsonPath("$.slots[3].free").value(false))  // 12:00
                .andExpect(jsonPath("$.slots[4].free").value(true));  // 13:00
    }

    @Test
    void cancelFreesTheSlot() throws Exception {
        book(2, 14, 15, "c@example.com").andExpect(status().isCreated());

        mvc.perform(get("/api/bookings").param("email", "c@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        String json = mvc.perform(get("/api/bookings").param("email", "c@example.com"))
                .andReturn().getResponse().getContentAsString();
        Number bookingId = JsonPath.read(json, "$[0].id");

        mvc.perform(delete("/api/bookings/" + bookingId).param("email", "other@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/bookings/" + bookingId).param("email", "c@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        book(2, 14, 15, "d@example.com").andExpect(status().isCreated());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        book(3, 7, 9, "e@example.com").andExpect(status().isBadRequest());   // вне рабочих часов
        book(3, 12, 11, "e@example.com").andExpect(status().isBadRequest()); // конец раньше начала
        book(3, 10, 11, "not-an-email").andExpect(status().isBadRequest());
        book(999, 10, 11, "e@example.com").andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointWorks() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
