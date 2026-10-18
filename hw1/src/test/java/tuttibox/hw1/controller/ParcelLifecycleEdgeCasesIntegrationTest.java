package tuttibox.hw1.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tuttibox.hw1.TestcontainersConfiguration;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.repository.ParcelRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class ParcelLifecycleEdgeCasesIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ParcelRepository parcelRepository;

    @BeforeEach
    void setUp() {
        parcelRepository.deleteAll();
    }

    @Test
    @DisplayName("partner can cancel a parcel while in CREATED state")
    void testCancelParcel() throws Exception {
        Parcel parcel = new Parcel("TRK-CANCEL-01", "client@example.com", "S");
        parcelRepository.save(parcel);

        mockMvc.perform(post("/api/parcels/TRK-CANCEL-01/cancel")
                .param("actor", "PARTNER_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("CANCELLED"));

        Parcel updated = parcelRepository.findByTrackingCode("TRK-CANCEL-01").orElseThrow();
        assertThat(updated.getCurrentState()).isEqualTo(ParcelState.CANCELLED);
    }

    @Test
    @DisplayName("parcel can expire and then be returned to partner")
    void testExpireAndReturnParcel() throws Exception {
        Parcel parcel = new Parcel("TRK-EXPIRE-01", "client@example.com", "L");
        parcel.markAsReadyForPickup("COURIER_01");
        parcelRepository.save(parcel);

        // Expire
        mockMvc.perform(post("/api/parcels/TRK-EXPIRE-01/expire")
                .param("actor", "SYSTEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("EXPIRED"));

        // Return
        mockMvc.perform(post("/api/parcels/TRK-EXPIRE-01/return")
                .param("actor", "COURIER_02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("RETURNED"));
    }
}
