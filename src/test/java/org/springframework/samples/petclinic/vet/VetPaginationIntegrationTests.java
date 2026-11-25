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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-context MockMvc tests for the optional pagination of {@code GET /vets}
 * (EPMCDMETST-73705) against the seeded H2 database (6 vets). Gherkin counterpart:
 * {@code features/vets-api-pagination.feature}.
 */
@SpringBootTest(properties = "management.endpoints.access.default=none")
@AutoConfigureMockMvc
class VetPaginationIntegrationTests {

	private static final int SEEDED_VETS = 6;

	private static final String SIZE_ERROR = "Invalid 'size' parameter: must be an integer between 1 and 100";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("AC1: page=0&size=2 on the seeded database returns 2 vets and metadata for 3 pages")
	void ac1FirstPage() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.metadata.page").value(0))
			.andExpect(jsonPath("$.metadata.size").value(2))
			.andExpect(jsonPath("$.metadata.totalItems").value(SEEDED_VETS))
			.andExpect(jsonPath("$.metadata.totalPages").value(3));
	}

	@Test
	@DisplayName("AC1: page=0&size=10 returns all seeded vets with page, size, totalItems and totalPages")
	void ac1SinglePageWithAllVets() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(SEEDED_VETS))
			.andExpect(jsonPath("$.metadata.page").value(0))
			.andExpect(jsonPath("$.metadata.size").value(10))
			.andExpect(jsonPath("$.metadata.totalItems").value(SEEDED_VETS))
			.andExpect(jsonPath("$.metadata.totalPages").value(1));
	}

	@Test
	@DisplayName("AC1: the last partial page and the default size are served from the database")
	void ac1LastPageAndDefaultSize() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "1").param("size", "4"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.metadata.totalPages").value(2));
		this.mockMvc.perform(get("/vets").param("page", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.metadata.size").value(10));
	}

	@Test
	@DisplayName("AC2: no parameters returns the legacy vetList containing all 6 seeded vets")
	void ac2LegacyListContainsAllVets() throws Exception {
		this.mockMvc.perform(get("/vets"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.vetList.length()").value(SEEDED_VETS))
			.andExpect(jsonPath("$.items").doesNotExist())
			.andExpect(jsonPath("$.metadata").doesNotExist());
	}

	@Test
	@DisplayName("AC2: a size without page is ignored and all seeded vets are returned")
	void ac2SizeWithoutPage() throws Exception {
		this.mockMvc.perform(get("/vets").param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.vetList.length()").value(SEEDED_VETS));
	}

	@Test
	@DisplayName("AC3: invalid page, size and non-numeric values each return 400 with an error message")
	void ac3ValidationErrors() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "-1").param("size", "10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("Invalid 'page' parameter: must be a non-negative integer"));
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(SIZE_ERROR));
		this.mockMvc.perform(get("/vets").param("page", "abc").param("size", "10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error")
				.value("Invalid pagination parameters: 'page' and 'size' must be numeric (integer) values"));
	}

	@Test
	@DisplayName("AC3: size=101 exceeds the maximum of 100 and returns 400")
	void ac3SizeAboveMaximum() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(SIZE_ERROR));
	}

	@Test
	@DisplayName("AC1: size=100 is accepted")
	void ac1SizeAtMaximum() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(SEEDED_VETS))
			.andExpect(jsonPath("$.metadata.size").value(100));
	}

	@Test
	@DisplayName("AC4: page=9999&size=10 returns 200, an empty list, the requested page and totalPages")
	void ac4PageBeyondAvailableResults() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "9999").param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.metadata.page").value(9999))
			.andExpect(jsonPath("$.metadata.totalItems").value(SEEDED_VETS))
			.andExpect(jsonPath("$.metadata.totalPages").value(1));
	}

}
