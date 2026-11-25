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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * QA repository-level tests for {@link OwnerRepository#findByTelephoneStartingWith}
 * (EPMCDMETST-73769). They run against the H2 sample data (ten owners whose telephones
 * all start with 6085) plus a few owners created per test inside the test transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class OwnerTelephoneSearchRepositoryTests {

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

	@Test
	void fullTelephoneReturnsExactlyTheMatchingOwner() {
		Page<Owner> page = this.owners.findByTelephoneStartingWith("6085551023", Pageable.ofSize(5));

		assertThat(page.getTotalElements()).isEqualTo(1);
		assertThat(page.getContent()).extracting(Owner::getLastName).containsExactly("Franklin");
	}

	@Test
	void prefixReturnsAllOwnersWhoseTelephoneStartsWithTheDigits() {
		saveOwner("Anna", "Alpha", "5551234000");
		saveOwner("Bob", "Beta", "5559999000");
		saveOwner("Cid", "Gamma", "4445551234");

		Page<Owner> page = this.owners.findByTelephoneStartingWith("555", Pageable.ofSize(5));

		assertThat(page.getContent()).extracting(Owner::getLastName).containsExactlyInAnyOrder("Alpha", "Beta");
	}

	@Test
	void matchingIsPrefixOnlyAndNotContains() {
		// 6085551023 contains "5551" but does not start with it
		Page<Owner> page = this.owners.findByTelephoneStartingWith("5551", Pageable.ofSize(5));

		assertThat(page.getContent()).isEmpty();
	}

	@Test
	void unknownTelephoneReturnsEmptyPage() {
		Page<Owner> page = this.owners.findByTelephoneStartingWith("0000000", Pageable.ofSize(5));

		assertThat(page.isEmpty()).isTrue();
		assertThat(page.getTotalElements()).isZero();
	}

	@Test
	void resultsArePagedWithTotalsAcrossPages() {
		Page<Owner> first = this.owners.findByTelephoneStartingWith("6085", PageRequest.of(0, 5));
		Page<Owner> second = this.owners.findByTelephoneStartingWith("6085", PageRequest.of(1, 5));
		Page<Owner> beyond = this.owners.findByTelephoneStartingWith("6085", PageRequest.of(2, 5));

		assertThat(first.getTotalElements()).isEqualTo(10);
		assertThat(first.getTotalPages()).isEqualTo(2);
		assertThat(first.getContent()).hasSize(5);
		assertThat(second.getContent()).hasSize(5);
		assertThat(first.getContent()).doesNotContainAnyElementsOf(second.getContent());
		assertThat(beyond.getContent()).isEmpty();
	}

	@Test
	void likeWildcardCharactersAreTreatedAsLiterals() {
		assertThat(this.owners.findByTelephoneStartingWith("%", Pageable.ofSize(5)).isEmpty()).isTrue();
		assertThat(this.owners.findByTelephoneStartingWith("_", Pageable.ofSize(5)).isEmpty()).isTrue();
		assertThat(this.owners.findByTelephoneStartingWith("6085%", Pageable.ofSize(5)).isEmpty()).isTrue();
	}

	@Test
	void emptyPrefixMatchesEveryOwnerSoTheControllerMustGuardBlankInput() {
		long total = this.owners.count();

		Page<Owner> page = this.owners.findByTelephoneStartingWith("", Pageable.ofSize(5));

		assertThat(page.getTotalElements()).isEqualTo(total);
	}

	@Test
	void lastNameQueryIsNotAffectedByTelephoneQuery() {
		saveOwner("Sam", "Smith", "5551234000");

		assertThat(this.owners.findByLastNameStartingWith("Smi", Pageable.ofSize(5)).getContent())
			.extracting(Owner::getLastName)
			.containsExactly("Smith");
		assertThat(this.owners.findByLastNameStartingWith("555", Pageable.ofSize(5)).isEmpty()).isTrue();
	}

}
