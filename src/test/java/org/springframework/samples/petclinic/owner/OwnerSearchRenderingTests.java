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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renders the Find Owners and Owners List views against the sample data to verify the
 * telephone search end to end, including the pagination links.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OwnerSearchRenderingTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void telephonePrefixSearchRendersPaginationLinksKeepingSearchCriteria() throws Exception {
		mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "6085"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/owners?page=2&amp;searchType=telephone&amp;telephone=6085")));
	}

	@Test
	void lastNamePaginationLinksDoNotCarryTelephoneCriteria() throws Exception {
		mockMvc.perform(get("/owners"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/owners?page=2")))
			.andExpect(content().string(not(containsString("searchType"))));
	}

	@Test
	void telephoneNoMatchShowsNotFoundErrorOnTelephoneField() throws Exception {
		mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "999"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("has not been found")))
			.andExpect(content().string(containsString("name=\"searchType\"")));
	}

	@Test
	void telephoneNonNumericShowsValidationError() throws Exception {
		mockMvc.perform(get("/owners").param("searchType", "telephone").param("telephone", "abc"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("must be all numeric")));
	}

}
