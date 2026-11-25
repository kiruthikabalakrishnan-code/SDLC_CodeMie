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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test class for {@link VisitCancellationController} (EPMCDMETST-73697). Scenarios are
 * described in {@code src/test/resources/features/cancel-visit.feature}.
 */
@WebMvcTest(VisitCancellationController.class)
@DisabledInNativeImage
@DisabledInAotMode
class VisitCancellationControllerTests {

	private static final int OWNER_ID = 6;

	private static final int PET_ID = 7;

	private static final int OTHER_PET_ID = 8;

	private static final int VISIT_ID = 70;

	private static final int OTHER_PET_VISIT_ID = 80;

	private static final String DELETE_URL = "/owners/{ownerId}/pets/{petId}/visits/{visitId}/delete";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository owners;

	private Owner owner;

	private Pet pet;

	private Pet otherPet;

	private Visit visit;

	private Visit otherPetVisit;

	@BeforeEach
	void init() {
		owner = new Owner();
		owner.setId(OWNER_ID);
		pet = pet(PET_ID, "Samantha");
		otherPet = pet(OTHER_PET_ID, "Max");
		visit = visit(VISIT_ID, "annual checkup");
		otherPetVisit = visit(OTHER_PET_VISIT_ID, "neutered");
		pet.addVisit(visit);
		otherPet.addVisit(otherPetVisit);
		owner.getPets().add(pet);
		owner.getPets().add(otherPet);
		given(this.owners.findById(OWNER_ID)).willReturn(Optional.of(owner));
		given(this.owners.findById(9999)).willReturn(Optional.empty());
	}

	private static Pet pet(int id, String name) {
		Pet pet = new Pet();
		pet.setId(id);
		pet.setName(name);
		return pet;
	}

	private static Visit visit(int id, String description) {
		Visit visit = new Visit();
		visit.setId(id);
		visit.setDescription(description);
		return visit;
	}

	@Test
	void cancelExistingVisitRemovesItSavesOwnerAndRedirectsWithSuccessMessage() throws Exception {
		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, VISIT_ID))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("message", "Visit cancelled successfully."))
			.andExpect(flash().attributeCount(1));

		assertThat(pet.getVisits()).doesNotContain(visit).isEmpty();
		verify(this.owners).save(owner);
	}

	@Test
	void cancelExistingVisitLeavesOtherVisitsOfOtherPetsUntouched() throws Exception {
		Visit second = visit(71, "vaccination");
		pet.addVisit(second);

		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, VISIT_ID)).andExpect(status().is3xxRedirection());

		assertThat(pet.getVisits()).containsExactly(second);
		assertThat(otherPet.getVisits()).containsExactly(otherPetVisit);
	}

	@Test
	void cancelVisitOfUnknownOwnerDeletesNothingAndRedirectsWithError() throws Exception {
		mockMvc.perform(post(DELETE_URL, 9999, PET_ID, VISIT_ID))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/9999"))
			.andExpect(flash().attribute("error", "Owner not found with id: 9999."))
			.andExpect(flash().attributeCount(1));

		verify(this.owners, never()).save(any());
		assertThat(pet.getVisits()).containsExactly(visit);
	}

	@Test
	void cancelVisitOfUnknownPetDeletesNothingAndRedirectsWithError() throws Exception {
		mockMvc.perform(post(DELETE_URL, OWNER_ID, 9999, VISIT_ID))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("error", "Pet with id 9999 not found for owner with id 6."))
			.andExpect(flash().attributeCount(1));

		verify(this.owners, never()).save(any());
		assertThat(pet.getVisits()).containsExactly(visit);
	}

	@Test
	void cancelUnknownVisitDeletesNothingAndRedirectsWithError() throws Exception {
		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, 9999))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("error", "Visit with id 9999 not found for pet with id 7."))
			.andExpect(flash().attributeCount(1));

		verify(this.owners, never()).save(any());
		assertThat(pet.getVisits()).containsExactly(visit);
	}

	@Test
	void cancelVisitThroughPetThatDoesNotOwnItDeletesNothing() throws Exception {
		mockMvc.perform(post(DELETE_URL, OWNER_ID, PET_ID, OTHER_PET_VISIT_ID))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("error", "Visit with id 80 not found for pet with id 7."))
			.andExpect(flash().attributeCount(1));

		verify(this.owners, never()).save(any());
		assertThat(pet.getVisits()).containsExactly(visit);
		assertThat(otherPet.getVisits()).containsExactly(otherPetVisit);
	}

	@Test
	void cancelVisitThroughPetOfAnotherOwnerDeletesNothing() throws Exception {
		// pet 1 exists in the database but belongs to owner 1, not to owner 6
		mockMvc.perform(post(DELETE_URL, OWNER_ID, 1, VISIT_ID))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/" + OWNER_ID))
			.andExpect(flash().attribute("error", "Pet with id 1 not found for owner with id 6."));

		verify(this.owners, never()).save(any());
		assertThat(pet.getVisits()).containsExactly(visit);
	}

	@Test
	void cancelVisitThroughOwnerThatDoesNotOwnThePetDeletesNothing() throws Exception {
		Owner other = new Owner();
		other.setId(1);
		given(this.owners.findById(1)).willReturn(Optional.of(other));

		mockMvc.perform(post(DELETE_URL, 1, PET_ID, VISIT_ID))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/owners/1"))
			.andExpect(flash().attribute("error", "Pet with id 7 not found for owner with id 1."));

		verify(this.owners, never()).save(any());
		assertThat(pet.getVisits()).containsExactly(visit);
	}

	@Test
	void cancelVisitOnlyAcceptsPost() throws Exception {
		mockMvc.perform(get(DELETE_URL, OWNER_ID, PET_ID, VISIT_ID)).andExpect(status().isMethodNotAllowed());

		verify(this.owners, never()).save(any());
		assertThat(pet.getVisits()).containsExactly(visit);
	}

}
