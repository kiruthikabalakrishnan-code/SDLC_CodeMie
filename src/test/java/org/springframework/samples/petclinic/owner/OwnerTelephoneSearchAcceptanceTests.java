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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * QA acceptance tests for EPMCDMETST-73769 (search owners by telephone). They implement
 * the scenarios of {@code features/owner-telephone-search.feature} against the full
 * application (real controller, repository, Thymeleaf views, H2 sample data). Each test
 * runs in a transaction that is rolled back, so owners created here never leak.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerTelephoneSearchAcceptanceTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OwnerRepository owners;

	private Owner saveOwner(String firstName, String lastName, String telephone) {
		Owner owner = new Owner();
		owner.setFirstName(firstName);
		owner.setLastName(lastName);
		owner.setAddress("1 Test Street");
		owner.setCity("Testville");
		owner.setTelephone(telephone);
		return this.owners.saveAndFlush(owner);
	}

	@Nested
	class Ac1ExactTelephone {

		@Test
		void singleMatchRedirectsToOwnerDetailsWhichShowTheTelephone() throws Exception {
			Owner owner = saveOwner("Anna", "Alpha", "5551234000");

			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "5551234000"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/" + owner.getId()));

			mockMvc.perform(get("/owners/" + owner.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("5551234000")));
		}

		@Test
		void surroundingWhitespaceIsIgnored() throws Exception {
			Owner owner = saveOwner("Anna", "Alpha", "5551234000");

			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "  5551234000 "))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/" + owner.getId()));
		}

		@Test
		void sampleDataOwnerIsFoundByFullTelephone() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "6085551023"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/1"));
		}

	}

	@Nested
	class Ac2PartialTelephone {

		@Test
		void prefixShowsListWithAllOwnersStartingWithTheDigitsAndNoOthers() throws Exception {
			saveOwner("Anna", "Alpha", "5551234000");
			saveOwner("Bob", "Beta", "5559999000");

			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "555"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/ownersList"))
				.andExpect(model().attribute("totalItems", 2L))
				.andExpect(content().string(containsString("Anna Alpha")))
				.andExpect(content().string(containsString("Bob Beta")))
				.andExpect(content().string(containsString("5551234000")))
				.andExpect(content().string(containsString("5559999000")))
				.andExpect(content().string(not(containsString("Franklin"))));
		}

		@Test
		void singleDigitPrefixMatchesAllSampleOwners() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "6"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/ownersList"))
				.andExpect(model().attribute("totalItems", 10L));
		}

		@Test
		void prefixMatchingIsNotContainsMatching() throws Exception {
			// 6085551023 contains 5551 but does not start with it
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "5551"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/findOwners"))
				.andExpect(content().string(containsString("has not been found")));
		}

	}

	@Nested
	class Ac3NoMatch {

		@Test
		void noMatchStaysOnFindOwnersWithNotFoundMessage() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "0000000"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/findOwners"))
				.andExpect(model().attributeHasFieldErrorCode("owner", "telephone", "notFound"))
				.andExpect(content().string(containsString("has not been found")));
		}

		@Test
		void lastNameNoMatchProducesTheSameKindOfOutcome() throws Exception {
			mockMvc.perform(get("/owners").param("lastName", "Nonexistent"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/findOwners"))
				.andExpect(model().attributeHasFieldErrorCode("owner", "lastName", "notFound"))
				.andExpect(content().string(containsString("has not been found")));
		}

		@Test
		void noMatchKeepsTelephoneModeSelectedAndEnteredValue() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "0000000"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("value=\"0000000\"")))
				.andExpect(content().string(containsString("id=\"searchByTelephone\"")));
		}

		@Test
		void outOfRangePageWithNoMatchRedirectsToFirstPage() throws Exception {
			mockMvc.perform(
					get("/owners").param("searchType", "telephone").param("telephone", "0000000").param("page", "2"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners?page=1&searchType=telephone&telephone=0000000"));
		}

	}

	@Nested
	class Ac4LastNameRegression {

		@Test
		void lastNamePrefixFindsSmith() throws Exception {
			Owner smith = saveOwner("Sam", "Smith", "5551234000");

			mockMvc.perform(get("/owners").param("lastName", "Smi"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/" + smith.getId()));
		}

		@Test
		void lastNamePrefixWithSeveralMatchesShowsList() throws Exception {
			mockMvc.perform(get("/owners").param("lastName", "Dav"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/ownersList"))
				.andExpect(content().string(containsString("Betty Davis")))
				.andExpect(content().string(containsString("Harold Davis")));
		}

		@Test
		void explicitLastNameSearchTypeBehavesLikeDefault() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "lastName").param("lastName", "Franklin"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/1"));
		}

		@Test
		void emptyLastNameStillReturnsAllOwnersPagedWithoutTelephoneParameters() throws Exception {
			mockMvc.perform(get("/owners"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/ownersList"))
				.andExpect(model().attribute("totalItems", 10L))
				.andExpect(content().string(containsString("/owners?page=2")))
				.andExpect(content().string(not(containsString("searchType"))))
				.andExpect(content().string(not(containsString("telephone="))));
		}

		@Test
		void lastNameOutOfRangePageRedirectKeepsLastNameOnly() throws Exception {
			mockMvc.perform(get("/owners").param("lastName", "Dav").param("page", "9"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners?page=1&lastName=Dav"));
		}

		@Test
		void telephoneParameterIsIgnoredWhenSearchTypeIsNotTelephone() throws Exception {
			mockMvc.perform(get("/owners").param("lastName", "Franklin").param("telephone", "0000000"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/1"));
		}

		@Test
		void lastNameParameterIsIgnoredInTelephoneMode() throws Exception {
			mockMvc
				.perform(get("/owners").param("searchType", "telephone")
					.param("telephone", "6085551023")
					.param("lastName", "Nonexistent"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners/1"));
		}

		@Test
		void findFormOffersBothSearchTypesWithLastNameSelectedByDefault() throws Exception {
			mockMvc.perform(get("/owners/find"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/findOwners"))
				.andExpect(content().string(containsString("id=\"searchByLastName\"")))
				.andExpect(content().string(containsString("id=\"searchByTelephone\"")))
				.andExpect(content().string(containsString("name=\"telephone\"")))
				.andExpect(content().string(containsString("name=\"lastName\"")));
		}

	}

	@Nested
	class Ac5EmptyAndInvalidInput {

		@Test
		void emptyTelephoneIsRejectedAndDoesNotReturnAllOwners() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/findOwners"))
				.andExpect(model().attributeHasFieldErrorCode("owner", "telephone", "required"))
				.andExpect(content().string(containsString("is required")));
		}

		@Test
		void missingTelephoneParameterIsRejected() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/findOwners"))
				.andExpect(model().attributeHasFieldErrorCode("owner", "telephone", "required"));
		}

		@Test
		void whitespaceOnlyTelephoneIsRejected() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "   "))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/findOwners"))
				.andExpect(model().attributeHasFieldErrorCode("owner", "telephone", "required"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "abc", "555-123", "55 5", "+15551234", "555.123", "%", "_", "5551234abc", "1e5" })
		void nonDigitTelephoneIsRejected(String input) throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", input))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/findOwners"))
				.andExpect(model().attributeHasFieldErrorCode("owner", "telephone", "nonNumeric"))
				.andExpect(content().string(containsString("must be all numeric")));
		}

		@Test
		void markupInTelephoneIsRejectedAndNotReflectedUnescaped() throws Exception {
			String markup = "<" + "script>alert(1)<" + "/script>";

			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", markup))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("must be all numeric")))
				.andExpect(content().string(not(containsString("<" + "script>alert(1)"))));
		}

		@Test
		void telephoneLongerThanTenDigitsFindsNothing() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "60855510230"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("owner", "telephone", "notFound"));
		}

		@Test
		void emptyLastNameKeepsReturningAllOwnersAsBefore() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "lastName").param("lastName", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/ownersList"))
				.andExpect(model().attribute("totalItems", 10L));
		}

	}

	@Nested
	class PaginationBehaviour {

		@Test
		void firstPageShowsFiveOwnersOutOfTenAndLinksKeepCriteria() throws Exception {
			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "6085"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("totalItems", 10L))
				.andExpect(model().attribute("listOwners", hasSize(5)))
				.andExpect(
						content().string(containsString("/owners?page=2&amp;searchType=telephone&amp;telephone=6085")));
		}

		@Test
		void secondPageKeepsCriteriaInBackLinks() throws Exception {
			mockMvc
				.perform(get("/owners").param("searchType", "telephone").param("telephone", "6085").param("page", "2"))
				.andExpect(status().isOk())
				.andExpect(view().name("owners/ownersList"))
				.andExpect(model().attribute("currentPage", 2))
				.andExpect(model().attribute("totalPages", 2))
				.andExpect(model().attribute("listOwners", hasSize(5)))
				.andExpect(
						content().string(containsString("/owners?page=1&amp;searchType=telephone&amp;telephone=6085")))
				.andExpect(content().string(not(containsString("/owners?page=1\""))));
		}

		@Test
		void telephoneResultsFittingOnOnePageShowNoPaginationLinks() throws Exception {
			saveOwner("Anna", "Alpha", "5551234000");
			saveOwner("Bob", "Beta", "5559999000");

			mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "555"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("searchType=telephone"))));
		}

		@Test
		void outOfRangePageRedirectsToFirstPageKeepingTelephoneCriteria() throws Exception {
			mockMvc
				.perform(get("/owners").param("searchType", "telephone").param("telephone", "6085").param("page", "99"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners?page=1&searchType=telephone&telephone=6085"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "0", "-1" })
		void nonPositivePageRedirectsToFirstPageKeepingTelephoneCriteria(String page) throws Exception {
			mockMvc
				.perform(get("/owners").param("searchType", "telephone").param("telephone", "6085").param("page", page))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/owners?page=1&searchType=telephone&telephone=6085"));
		}

	}

}
