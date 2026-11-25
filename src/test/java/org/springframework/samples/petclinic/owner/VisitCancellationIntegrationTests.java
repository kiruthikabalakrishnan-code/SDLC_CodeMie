/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Backend tests for cancelling a visit (EPMCDMETST-73697) against the real application
 * context and the default H2 database. They verify that the {@code visits} row is really
 * deleted ({@code Pet.visits} uses {@code orphanRemoval = true}) and that failure paths
 * leave the table untouched. The tests deliberately do not use {@code @Transactional}, so
 * that the request commits like it would in production. Every visit created here is
 * removed again in {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisitCancellationIntegrationTests {

	// Owner 6 (Jean Coleman) owns pet 7 (Samantha) and pet 8 (Max); owner 1 owns pet 1
	private static final int OWNER_ID = 6;

	private static final int PET_ID = 7;

	private static final int OTHER_PET_ID = 8;

	private static final String MARKER = "qa-73697-";

	private static final String DELETE_URL = "/owners/{ownerId}/pets/{petId}/visits/{visitId}/delete";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private JdbcTemplate jdbc;

	private int visitId;

	private int otherPetVisitId;

	@BeforeEach
	void bookVisits() {
		visitId = book(OWNER_ID, PET_ID, MARKER + "own");
		otherPetVisitId = book(OWNER_ID, OTHER_PET_ID, MARKER + "other-pet");
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM visits WHERE description LIKE ?", MARKER + "%");
	}

	private int book(int ownerId, int petId, String description) {
		Owner owner = owners.findById(ownerId).orElseThrow();
		Visit visit = new Visit();
		visit.setDescription(description);
		owner.getPet(petId).addVisit(visit);
		owners.save(owner);
		return jdbc.queryForObject("SELECT id FROM visits WHERE description = ?", Integer.class, description);
	}

	private boolean rowExists(int id) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM visits WHERE id = ?", Integer.class, id) == 1;
	}

	private List<Integer> allVisitIds() {
		return jdbc.queryForList("SELECT id FROM visits ORDER BY id", Integer.class);
	}

	@Test
	@DisplayName("AC1: cancelVisitDeletesTheRowThroughOrphanRemoval")
	void cancelVisitDeletesTheRowThroughOrphanRemoval() throws Exception {
		assertThat(rowExists(visitId)).isTrue();
		List<Integer> before = allVisitIds();

		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, visitId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("message", "Visit cancelled successfully."));

		assertThat(rowExists(visitId)).isFalse();
		// exactly one row is gone: all seeded visits and the other pet's visit remain
		List<Integer> expected = before.stream().filter(id -> id != visitId).toList();
		assertThat(allVisitIds()).isEqualTo(expected);
		assertThat(rowExists(otherPetVisitId)).isTrue();
		assertThat(owners.findById(OWNER_ID).orElseThrow().getPet(PET_ID).getVisits()).extracting(Visit::getId)
			.doesNotContain(visitId);
	}

	@Test
	@DisplayName("AC3: cancelVisitTwiceFailsGracefullyTheSecondTime")
	void cancelVisitTwiceFailsGracefullyTheSecondTime() throws Exception {
		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, visitId)).andExpect(status().is3xxRedirection());

		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, visitId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("error", "Visit with id " + visitId + " not found for pet with id 7."));

		assertThat(rowExists(otherPetVisitId)).isTrue();
	}

	@Test
	@DisplayName("AC3: cancelVisitOfUnknownOwnerDeletesNothing")
	void cancelVisitOfUnknownOwnerDeletesNothing() throws Exception {
		List<Integer> before = allVisitIds();

		mockMvc.perform(post(DELETE_URL, 9999, PET_ID, visitId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/9999"))
			.andExpect(flash().attribute("error", "Owner not found with id: 9999."));

		assertThat(allVisitIds()).isEqualTo(before);
	}

	@Test
	@DisplayName("AC3: cancelVisitOfUnknownPetDeletesNothing")
	void cancelVisitOfUnknownPetDeletesNothing() throws Exception {
		List<Integer> before = allVisitIds();

		mockMvc.perform(post(DELETE_URL, OWNER_ID, 9999, visitId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("error", "Pet with id 9999 not found for owner with id 6."));

		assertThat(allVisitIds()).isEqualTo(before);
	}

	@Test
	@DisplayName("AC3: cancelUnknownVisitDeletesNothing")
	void cancelUnknownVisitDeletesNothing() throws Exception {
		List<Integer> before = allVisitIds();

		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, 9999))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("error", "Visit with id 9999 not found for pet with id 7."));

		assertThat(allVisitIds()).isEqualTo(before);
	}

	@Test
	@DisplayName("AC4: cancelVisitThatBelongsToAnotherOwnersPetDeletesNothing")
	void cancelVisitThatBelongsToAnotherOwnersPetDeletesNothing() throws Exception {
		// the seed data has no visits for pets 1 or 3, so book a real one for pet 1
		// (owner 1)
		int otherOwnerVisitId = book(1, 1, MARKER + "other-owner");
		List<Integer> before = allVisitIds();

		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, otherOwnerVisitId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(
					flash().attribute("error", "Visit with id " + otherOwnerVisitId + " not found for pet with id 7."));

		assertThat(rowExists(otherOwnerVisitId)).isTrue();
		assertThat(allVisitIds()).isEqualTo(before);
	}

	@Test
	@DisplayName("AC4: cancelVisitThroughPetThatDoesNotOwnItDeletesNothing")
	void cancelVisitThroughPetThatDoesNotOwnItDeletesNothing() throws Exception {
		List<Integer> before = allVisitIds();

		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, otherPetVisitId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(
					flash().attribute("error", "Visit with id " + otherPetVisitId + " not found for pet with id 7."));

		assertThat(allVisitIds()).isEqualTo(before);
		assertThat(rowExists(otherPetVisitId)).isTrue();
	}

	@Test
	@DisplayName("AC4: cancelVisitThroughPetOfAnotherOwnerDeletesNothing")
	void cancelVisitThroughPetOfAnotherOwnerDeletesNothing() throws Exception {
		List<Integer> before = allVisitIds();

		mockMvc.perform(post(DELETE_URL, OWNER_ID, 1, visitId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("error", "Pet with id 1 not found for owner with id 6."));

		assertThat(allVisitIds()).isEqualTo(before);
	}

	@Test
	@DisplayName("AC4: cancelVisitThroughOwnerThatDoesNotOwnThePetDeletesNothing")
	void cancelVisitThroughOwnerThatDoesNotOwnThePetDeletesNothing() throws Exception {
		List<Integer> before = allVisitIds();

		mockMvc.perform(post(DELETE_URL, 1, PET_ID, visitId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/1"))
			.andExpect(flash().attribute("error", "Pet with id 7 not found for owner with id 1."));

		assertThat(allVisitIds()).isEqualTo(before);
	}

}
