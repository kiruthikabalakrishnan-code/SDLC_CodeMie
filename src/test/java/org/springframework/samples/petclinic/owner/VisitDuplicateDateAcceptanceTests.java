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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * QA acceptance tests for EPMCDMETST-73807 (prevent duplicate visit dates for the same
 * pet). They implement the scenarios of {@code features/visit-duplicate-date.feature}
 * against the full application (real controller, JPA repository, Thymeleaf view, message
 * bundle, H2). Unlike {@link VisitControllerTests} nothing is mocked, so persistence of
 * (or the absence of) the visit is verified in the database.
 * <p>
 * The tests are deliberately not transactional: with {@code open-in-view=false} every
 * request loads the owner afresh, exactly as in production. Every test creates its own
 * owner and removes it afterwards, so the sample data is never touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VisitDuplicateDateAcceptanceTests {

	private static final String VISIT_URL = "/owners/{ownerId}/pets/{petId}/visits/new";

	private static final String DUPLICATE_MESSAGE = "This pet already has a visit booked for this date";

	private static final String PAST_DATE_MESSAGE = "Visit date must be in the future";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OwnerRepository owners;

	private final List<Integer> createdOwnerIds = new ArrayList<>();

	@AfterEach
	void removeCreatedOwners() {
		this.createdOwnerIds.forEach(id -> this.owners.findById(id).ifPresent(this.owners::delete));
		this.createdOwnerIds.clear();
	}

	/** Creates an owner with one pet per given name; returns the persisted owner. */
	private Owner createOwnerWithPets(String... petNames) {
		PetType type = this.owners.findById(1).orElseThrow().getPet("Leo").getType();
		Owner owner = new Owner();
		owner.setFirstName("Visit");
		owner.setLastName("Tester-" + UUID.randomUUID().toString().substring(0, 8));
		owner.setAddress("1 Test Street");
		owner.setCity("Testville");
		owner.setTelephone("5557370000");
		for (String petName : petNames) {
			Pet pet = new Pet();
			pet.setName(petName);
			pet.setBirthDate(LocalDate.of(2020, 1, 1));
			pet.setType(type);
			owner.addPet(pet);
		}
		Owner saved = this.owners.save(owner);
		this.createdOwnerIds.add(saved.getId());
		return saved;
	}

	private Owner reload(Owner owner) {
		return this.owners.findById(owner.getId()).orElseThrow();
	}

	private int petId(Owner owner, String petName) {
		return reload(owner).getPet(petName).getId();
	}

	private List<LocalDate> visitDates(Owner owner, String petName) {
		return reload(owner).getPet(petName).getVisits().stream().map(Visit::getDate).toList();
	}

	private ResultActions bookVisit(Owner owner, String petName, LocalDate date, String description) throws Exception {
		return this.mockMvc.perform(post(VISIT_URL, owner.getId(), petId(owner, petName)).param("date", date.toString())
			.param("description", description));
	}

	private void book(Owner owner, String petName, LocalDate date, String description) throws Exception {
		bookVisit(owner, petName, date, description).andExpect(status().is3xxRedirection());
	}

	private static LocalDate futureDate() {
		return LocalDate.now().plusDays(30);
	}

	@Nested
	class Ac1UniqueFutureDate {

		@Test
		void visitOnUniqueFutureDateIsSavedAndListedOnOwnerPage() throws Exception {
			Owner owner = createOwnerWithPets("Solo");
			LocalDate date = futureDate();

			bookVisit(owner, "Solo", date, "annual checkup").andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/" + owner.getId()))
				.andExpect(flash().attribute("message", "Your visit has been booked"));

			assertThat(visitDates(owner, "Solo")).containsExactly(date);
			mockMvc.perform(get("/owners/{id}", owner.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(date.toString())))
				.andExpect(content().string(containsString("annual checkup")));
		}

		@Test
		void secondVisitOnADifferentFutureDateIsAlsoSaved() throws Exception {
			Owner owner = createOwnerWithPets("Solo");
			LocalDate first = futureDate();
			LocalDate second = first.plusDays(1);

			book(owner, "Solo", first, "first");
			book(owner, "Solo", second, "second");

			assertThat(visitDates(owner, "Solo")).containsExactly(first, second);
		}

	}

	@Nested
	class Ac2DuplicateDateForSamePet {

		@Test
		void secondVisitOnSameDateIsRejectedWithVisibleFieldErrorAndNotPersisted() throws Exception {
			Owner owner = createOwnerWithPets("Solo");
			LocalDate date = futureDate();
			book(owner, "Solo", date, "first");

			bookVisit(owner, "Solo", date, "second").andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdateVisitForm"))
				.andExpect(model().attributeHasFieldErrorCode("visit", "date", "duplicate.visitDate"))
				.andExpect(model().attributeHasFieldErrors("visit", "date"))
				.andExpect(content().string(containsString(DUPLICATE_MESSAGE)))
				.andExpect(content().string(containsString("has-error")))
				.andExpect(content().string(not(containsString(PAST_DATE_MESSAGE))));

			assertThat(visitDates(owner, "Solo")).containsExactly(date);
			assertThat(reload(owner).getPet("Solo").getVisits()).extracting(Visit::getDescription)
				.containsExactly("first");
		}

		@Test
		void rejectedFormKeepsTheSubmittedValues() throws Exception {
			Owner owner = createOwnerWithPets("Solo");
			LocalDate date = futureDate();
			book(owner, "Solo", date, "first");

			bookVisit(owner, "Solo", date, "second attempt").andExpect(status().isOk())
				.andExpect(content().string(containsString("value=\"" + date + "\"")))
				.andExpect(content().string(containsString("second attempt")));
		}

		@Test
		void duplicateIsStillRejectedAfterAnEarlierRejection() throws Exception {
			Owner owner = createOwnerWithPets("Solo");
			LocalDate date = futureDate();
			book(owner, "Solo", date, "first");

			bookVisit(owner, "Solo", date, "second").andExpect(status().isOk());
			bookVisit(owner, "Solo", date, "third").andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("visit", "date", "duplicate.visitDate"));

			assertThat(visitDates(owner, "Solo")).containsExactly(date);
		}

		@Test
		void descriptionErrorIsReportedTogetherWithTheDuplicateDateError() throws Exception {
			Owner owner = createOwnerWithPets("Solo");
			LocalDate date = futureDate();
			book(owner, "Solo", date, "first");

			bookVisit(owner, "Solo", date, "").andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("visit", "date", "duplicate.visitDate"))
				.andExpect(model().attributeHasFieldErrors("visit", "description"));

			assertThat(visitDates(owner, "Solo")).containsExactly(date);
		}

	}

	@Nested
	class Ac3SameDateAcrossDifferentPets {

		@Test
		void sameDateCanBeUsedByAnotherPetOfTheSameOwner() throws Exception {
			Owner owner = createOwnerWithPets("PetA", "PetB");
			LocalDate date = futureDate();
			book(owner, "PetA", date, "for A");

			bookVisit(owner, "PetB", date, "for B").andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/" + owner.getId()));

			assertThat(visitDates(owner, "PetA")).containsExactly(date);
			assertThat(visitDates(owner, "PetB")).containsExactly(date);
		}

		@Test
		void sameDateCanBeUsedByAPetOfAnotherOwner() throws Exception {
			Owner first = createOwnerWithPets("PetA");
			Owner second = createOwnerWithPets("PetA");
			LocalDate date = futureDate();
			book(first, "PetA", date, "first owner");

			book(second, "PetA", date, "second owner");

			assertThat(visitDates(first, "PetA")).containsExactly(date);
			assertThat(visitDates(second, "PetA")).containsExactly(date);
		}

		@Test
		void duplicateRuleStillAppliesPerPetWhenAnotherPetSharesTheDate() throws Exception {
			Owner owner = createOwnerWithPets("PetA", "PetB");
			LocalDate date = futureDate();
			book(owner, "PetA", date, "for A");
			book(owner, "PetB", date, "for B");

			bookVisit(owner, "PetB", date, "for B again").andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("visit", "date", "duplicate.visitDate"));

			assertThat(visitDates(owner, "PetA")).containsExactly(date);
			assertThat(visitDates(owner, "PetB")).containsExactly(date);
		}

	}

	@Nested
	class Ac4TodayOrPastDateStillRejected {

		@ParameterizedTest
		@ValueSource(ints = { 0, -1, -400 })
		void todayOrPastDateIsRejectedWithTheExistingErrorAndNotPersisted(int daysFromToday) throws Exception {
			Owner owner = createOwnerWithPets("Solo");
			LocalDate date = LocalDate.now().plusDays(daysFromToday);

			bookVisit(owner, "Solo", date, "too early").andExpect(status().isOk())
				.andExpect(view().name("pets/createOrUpdateVisitForm"))
				.andExpect(model().attributeHasFieldErrorCode("visit", "date", "typeMismatch.visitDate"))
				.andExpect(content().string(containsString(PAST_DATE_MESSAGE)))
				.andExpect(content().string(not(containsString(DUPLICATE_MESSAGE))));

			assertThat(visitDates(owner, "Solo")).isEmpty();
		}

		@Test
		void pastDateOfAnExistingVisitReportsOnlyTheExistingErrorNotTheDuplicate() throws Exception {
			// Sample data: pet 7 (Samantha, owner 6) already has a visit on 2013-01-01.
			// The request is rejected before anything is saved, so the data is unchanged.
			int ownerId = 6;
			int petId = 7;
			int visitsBefore = owners.findById(ownerId).orElseThrow().getPet(petId).getVisits().size();

			mockMvc.perform(post(VISIT_URL, ownerId, petId).param("date", "2013-01-01").param("description", "again"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("visit", "date", "typeMismatch.visitDate"))
				.andExpect(content().string(containsString(PAST_DATE_MESSAGE)))
				.andExpect(content().string(not(containsString(DUPLICATE_MESSAGE))));

			assertThat(owners.findById(ownerId).orElseThrow().getPet(petId).getVisits()).hasSize(visitsBefore);
		}

	}

}
