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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for the optional pagination of {@code GET /vets} (EPMCDMETST-73705). The
 * repository is mocked with 25 vets and honours the requested {@link Pageable}. Gherkin
 * counterpart: {@code features/vets-api-pagination.feature}.
 */
@WebMvcTest(VetController.class)
@DisabledInNativeImage
@DisabledInAotMode
class VetControllerPaginationTests {

	private static final int TOTAL_VETS = 25;

	private static final String PAGE_ERROR = "Invalid 'page' parameter: must be a non-negative integer";

	private static final String SIZE_ERROR = "Invalid 'size' parameter: must be an integer between 1 and 100";

	private static final String NUMERIC_ERROR = "Invalid pagination parameters: 'page' and 'size' must be numeric (integer) values";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VetRepository vets;

	@BeforeEach
	void setup() {
		List<Vet> all = new ArrayList<>();
		for (int i = 1; i <= TOTAL_VETS; i++) {
			Vet vet = new Vet();
			vet.setId(i);
			vet.setFirstName("First" + i);
			vet.setLastName("Last" + i);
			all.add(vet);
		}
		given(this.vets.findAll()).willReturn(all);
		given(this.vets.findAll(any(Pageable.class))).willAnswer(invocation -> {
			Pageable pageable = invocation.getArgument(0);
			int from = (int) Math.min(pageable.getOffset(), all.size());
			int to = Math.min(from + pageable.getPageSize(), all.size());
			List<Vet> content = (from >= all.size()) ? Collections.emptyList() : all.subList(from, to);
			return new PageImpl<>(content, pageable, all.size());
		});
	}

	@Test
	@DisplayName("AC1: page=0&size=10 returns at most 10 vets with page, size, totalItems and totalPages")
	void ac1PaginatedResponseShape() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "10").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.items.length()").value(10))
			.andExpect(jsonPath("$.items[0].id").value(1))
			.andExpect(jsonPath("$.metadata.page").value(0))
			.andExpect(jsonPath("$.metadata.size").value(10))
			.andExpect(jsonPath("$.metadata.totalItems").value(TOTAL_VETS))
			.andExpect(jsonPath("$.metadata.totalPages").value(3))
			.andExpect(jsonPath("$.vetList").doesNotExist());
	}

	@Test
	@DisplayName("AC1: a later page returns the next slice and the last partial page has the remainder")
	void ac1SecondAndLastPage() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "1").param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(10))
			.andExpect(jsonPath("$.items[0].id").value(11))
			.andExpect(jsonPath("$.metadata.page").value(1));
		this.mockMvc.perform(get("/vets").param("page", "2").param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[0].id").value(21));
	}

	@Test
	@DisplayName("AC1: page without size uses the default size of 10")
	void ac1DefaultSizeIsTen() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(10))
			.andExpect(jsonPath("$.metadata.size").value(10))
			.andExpect(jsonPath("$.metadata.totalPages").value(3));
	}

	@Test
	@DisplayName("AC1: the maximum size of 100 is accepted")
	void ac1MaxSizeAccepted() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(TOTAL_VETS))
			.andExpect(jsonPath("$.metadata.size").value(100))
			.andExpect(jsonPath("$.metadata.totalPages").value(1));
	}

	@Test
	@DisplayName("AC2: no pagination parameters returns the legacy vetList with all vets")
	void ac2LegacyFormatWithoutParameters() throws Exception {
		this.mockMvc.perform(get("/vets").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.vetList.length()").value(TOTAL_VETS))
			.andExpect(jsonPath("$.vetList[0].id").value(1))
			.andExpect(jsonPath("$.vetList[24].id").value(TOTAL_VETS))
			.andExpect(jsonPath("$.items").doesNotExist())
			.andExpect(jsonPath("$.metadata").doesNotExist());
	}

	@ParameterizedTest(name = "size={0}")
	@ValueSource(strings = { "5", "0", "-1", "101", "abc" })
	@DisplayName("AC2: a size without page is ignored and the legacy response is returned")
	void ac2SizeWithoutPageIsIgnored(String size) throws Exception {
		this.mockMvc.perform(get("/vets").param("size", size))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.vetList.length()").value(TOTAL_VETS))
			.andExpect(jsonPath("$.metadata").doesNotExist());
	}

	@Test
	@DisplayName("AC3: page=-1&size=10 is a 400 saying the page parameter is invalid")
	void ac3NegativePage() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "-1").param("size", "10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(PAGE_ERROR));
	}

	@Test
	@DisplayName("AC3: page=0&size=0 is a 400 saying the size parameter is invalid")
	void ac3ZeroSize() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(SIZE_ERROR));
	}

	@Test
	@DisplayName("AC3: page=abc&size=10 is a 400 saying the parameters must be numeric")
	void ac3NonNumericPage() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "abc").param("size", "10"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(NUMERIC_ERROR));
	}

	@ParameterizedTest(name = "size={0}")
	@ValueSource(strings = { "-5", "101", "1000" })
	@DisplayName("AC3: size below 1 or above the maximum of 100 is a 400")
	void ac3SizeOutOfRange(String size) throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", size))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(SIZE_ERROR));
	}

	@Test
	@DisplayName("AC3: non-numeric size is a 400 saying the parameters must be numeric")
	void ac3NonNumericSize() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "0").param("size", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(NUMERIC_ERROR));
	}

	@ParameterizedTest(name = "page={0}")
	@ValueSource(strings = { "1.5", "", "99999999999", "0x1" })
	@DisplayName("AC3: decimal, empty and int-overflowing page values are rejected as non-numeric")
	void ac3OtherNonNumericPages(String page) throws Exception {
		this.mockMvc.perform(get("/vets").param("page", page))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(NUMERIC_ERROR));
	}

	@Test
	@DisplayName("AC3: with both page and size invalid the page error is reported")
	void ac3PageValidatedBeforeSize() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "-1").param("size", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value(PAGE_ERROR));
	}

	@Test
	@DisplayName("AC4: page=9999&size=10 returns 200, an empty list and the requested page with totalPages")
	void ac4PageBeyondAvailableResults() throws Exception {
		this.mockMvc.perform(get("/vets").param("page", "9999").param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.metadata.page").value(9999))
			.andExpect(jsonPath("$.metadata.size").value(10))
			.andExpect(jsonPath("$.metadata.totalItems").value(TOTAL_VETS))
			.andExpect(jsonPath("$.metadata.totalPages").value(3));
	}

}
