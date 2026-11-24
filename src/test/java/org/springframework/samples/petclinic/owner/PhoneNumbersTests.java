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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test class for {@link PhoneNumbers}
 */
class PhoneNumbersTests {

	@ParameterizedTest
	@ValueSource(strings = { "(555) 123-4567", "555 123 4567", "555-123-4567", "555.123.4567", "5551234567",
			"  (555)123-4567  " })
	void normalizeStripsNonDigits(String input) {
		assertThat(PhoneNumbers.normalize(input)).isEqualTo("5551234567");
	}

	@Test
	void normalizeReturnsEmptyForNullBlankOrPunctuationOnly() {
		assertThat(PhoneNumbers.normalize(null)).isEmpty();
		assertThat(PhoneNumbers.normalize("")).isEmpty();
		assertThat(PhoneNumbers.normalize("   ")).isEmpty();
		assertThat(PhoneNumbers.normalize("() - ")).isEmpty();
	}

}
