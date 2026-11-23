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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

/**
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Ken Krebs
 * @author Arjen Poutsma
 */
@Controller
class VetController {

	private static final int PAGE_SIZE = 5;

	private static final int MAX_LAST_NAME_LENGTH = 100;

	private final VetRepository vetRepository;

	private final SpecialtyRepository specialtyRepository;

	public VetController(VetRepository vetRepository, SpecialtyRepository specialtyRepository) {
		this.vetRepository = vetRepository;
		this.specialtyRepository = specialtyRepository;
	}

	@GetMapping("/vets.html")
	public String showVetList(@RequestParam(defaultValue = "1") int page,
			@RequestParam(required = false) String lastName, @RequestParam(required = false) String specialty,
			Model model, RedirectAttributes redirectAttributes) {
		String lastNameFilter = normalizeLastName(lastName);
		String specialtyFilter = trimToNull(specialty);
		Page<Vet> paginated = findPaginated(page, lastNameFilter, specialtyFilter);
		if (page < 1 || page > Math.max(paginated.getTotalPages(), 1)) {
			redirectAttributes.addAttribute("page", 1);
			if (lastNameFilter != null) {
				redirectAttributes.addAttribute("lastName", lastNameFilter);
			}
			if (specialtyFilter != null) {
				redirectAttributes.addAttribute("specialty", specialtyFilter);
			}
			return "redirect:/vets.html";
		}
		return addPaginationModel(page, paginated, lastNameFilter, specialtyFilter, model);
	}

	private String addPaginationModel(int page, Page<Vet> paginated, String lastName, String specialty,
			Model model) {
		List<Vet> listVets = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listVets", listVets);
		model.addAttribute("lastName", lastName != null ? lastName : "");
		model.addAttribute("specialty", specialty != null ? specialty : "");
		model.addAttribute("specialties", specialtyRepository.findAllByOrderByNameAsc());
		model.addAttribute("filterQuery", buildFilterQuery(lastName, specialty));
		model.addAttribute("noResults", paginated.getTotalElements() == 0);
		return "vets/vetList";
	}

	private Page<Vet> findPaginated(int page, String lastName, String specialty) {
		int safePage = Math.max(page, 1);
		Pageable pageable = PageRequest.of(safePage - 1, PAGE_SIZE);
		if (lastName != null && specialty != null) {
			return vetRepository.findByLastNameContainingAndSpecialtyName(lastName, specialty, pageable);
		}
		if (lastName != null) {
			return vetRepository.findByLastNameContaining(lastName, pageable);
		}
		if (specialty != null) {
			return vetRepository.findBySpecialtyName(specialty, pageable);
		}
		return vetRepository.findAll(pageable);
	}

	private static String normalizeLastName(String lastName) {
		String trimmed = trimToNull(lastName);
		if (trimmed != null && trimmed.length() > MAX_LAST_NAME_LENGTH) {
			trimmed = trimmed.substring(0, MAX_LAST_NAME_LENGTH);
		}
		return trimmed;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * Builds the URL-encoded query string fragment (starting with '&') that carries the
	 * active filters, so pagination links can preserve them.
	 */
	private static String buildFilterQuery(String lastName, String specialty) {
		StringBuilder query = new StringBuilder();
		if (lastName != null) {
			query.append("&lastName=").append(UriUtils.encodeQueryParam(lastName, StandardCharsets.UTF_8));
		}
		if (specialty != null) {
			query.append("&specialty=").append(UriUtils.encodeQueryParam(specialty, StandardCharsets.UTF_8));
		}
		return query.toString();
	}

	@GetMapping({ "/vets" })
	public @ResponseBody Vets showResourcesVetList() {
		// Here we are returning an object of type 'Vets' rather than a collection of Vet
		// objects so it is simpler for JSon/Object mapping
		Vets vets = new Vets();
		vets.getVetList().addAll(this.vetRepository.findAll());
		return vets;
	}

}
