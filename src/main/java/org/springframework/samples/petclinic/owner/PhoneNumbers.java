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

/**
 * Helper for normalizing user-entered phone numbers before they are used as search
 * criteria.
 */
final class PhoneNumbers {

	private PhoneNumbers() {
	}

	/**
	 * Strip every character that is not a digit, so that "(555) 123-4567", "555-123-4567"
	 * and "555 123 4567" all become "5551234567".
	 * @param input the raw user input, may be {@code null}
	 * @return the digits-only value, or an empty string if there are no digits
	 */
	static String normalize(String input) {
		if (input == null) {
			return "";
		}
		StringBuilder digits = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c >= '0' && c <= '9') {
				digits.append(c);
			}
		}
		return digits.toString();
	}

}
