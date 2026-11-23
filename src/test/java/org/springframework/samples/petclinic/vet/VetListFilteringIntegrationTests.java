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
package org.springframework.samples.petclinic.vet;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests (real H2 data, real repositories, rendered Thymeleaf view) for
 * EPMCDMETST-73424 vets list filtering. Seed data: Carter (none), Leary (radiology),
 * Douglas (surgery, dentistry), Ortega (surgery), Stevens (radiology), Jenkins (none).
 */
@SpringBootTest
@AutoConfigureMockMvc
class VetListFilteringIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void noFiltersShowsAllVets() throws Exception {
		mockMvc.perform(get("/vets.html"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 6L))
			.andExpect(model().attribute("noResults", false));
	}

	@Test
	void lastNameFilterIsCaseInsensitiveSubstring() throws Exception {
		mockMvc.perform(get("/vets.html").param("lastName", "OU"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 1L))
			.andExpect(content().string(containsString("Linda Douglas")))
			.andExpect(content().string(not(containsString("James Carter"))));
	}

	@Test
	void specialtyFilterReturnsOnlyVetsWithSpecialty() throws Exception {
		mockMvc.perform(get("/vets.html").param("specialty", "dentistry"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 1L))
			.andExpect(content().string(containsString("Linda Douglas")));
	}

	@Test
	void specialtyFilterWithMultipleVets() throws Exception {
		mockMvc.perform(get("/vets.html").param("specialty", "surgery"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 2L));
	}

	@Test
	void specialtyFilterIsCaseInsensitive() throws Exception {
		mockMvc.perform(get("/vets.html").param("specialty", "RADIOLOGY"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 2L));
	}

	@Test
	void combinedFilterMatchesBothCriteria() throws Exception {
		mockMvc.perform(get("/vets.html").param("lastName", "ou").param("specialty", "dentistry"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 1L));
	}

	@Test
	void combinedFilterUsesAnd() throws Exception {
		mockMvc.perform(get("/vets.html").param("lastName", "carter").param("specialty", "dentistry"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 0L))
			.andExpect(model().attribute("noResults", true));
	}

	@Test
	void noMatchesShowsEmptyStateMessage() throws Exception {
		mockMvc.perform(get("/vets.html").param("lastName", "zzz"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("noResults", true))
			.andExpect(content().string(containsString("id=\"noVetsMessage\"")));
	}

	@Test
	void allVetsViewHasNoEmptyStateMessage() throws Exception {
		mockMvc.perform(get("/vets.html")).andExpect(content().string(not(containsString("id=\"noVetsMessage\""))));
	}

	@Test
	void filterFormReflectsQueryParametersAndOffersClearLink() throws Exception {
		mockMvc.perform(get("/vets.html").param("lastName", "ou").param("specialty", "dentistry"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"ou\"")))
			.andExpect(content().string(containsString("id=\"clearFilters\"")))
			.andExpect(content().string(containsString("selected")));
	}

	@Test
	void blankFiltersTreatedAsNoFilter() throws Exception {
		mockMvc.perform(get("/vets.html").param("lastName", "   ").param("specialty", ""))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 6L));
	}

	/**
	 * The percent sign and underscore are SQL LIKE wildcards. A literal search for them
	 * should arguably not match every vet. If this fails it is a minor implementation
	 * defect (unescaped LIKE wildcards).
	 */
	@Test
	void likeWildcardsInLastNameAreNotTreatedAsPatterns() throws Exception {
		mockMvc.perform(get("/vets.html").param("lastName", "%"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("totalItems", 0L));
	}

	@Test
	void outOfBoundsPageRedirectPreservesFilters() throws Exception {
		mockMvc.perform(get("/vets.html").param("page", "2").param("lastName", "r"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/vets.html?page=1&lastName=r"));
	}

	@Test
	void overlongLastNameIsTruncatedAndDoesNotFail() throws Exception {
		mockMvc.perform(get("/vets.html").param("lastName", "a".repeat(500)))
			.andExpect(status().isOk())
			.andExpect(model().attribute("noResults", true));
	}

	@Test
	void jsonEndpointIgnoresFilters() throws Exception {
		mockMvc.perform(get("/vets").param("lastName", "zzz").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Carter")))
			.andExpect(content().string(containsString("Jenkins")));
	}

}
