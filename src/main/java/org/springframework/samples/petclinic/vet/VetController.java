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

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Ken Krebs
 * @author Arjen Poutsma
 */
@Controller
class VetController {

	private static final int DEFAULT_API_PAGE_SIZE = 10;

	private static final int MAX_API_PAGE_SIZE = 100;

	private final VetRepository vetRepository;

	public VetController(VetRepository vetRepository) {
		this.vetRepository = vetRepository;
	}

	@GetMapping("/vets.html")
	public String showVetList(@RequestParam(defaultValue = "1") int page, Model model,
			RedirectAttributes redirectAttributes) {
		Page<Vet> paginated = findPaginated(page);
		if (page < 1 || page > Math.max(paginated.getTotalPages(), 1)) {
			redirectAttributes.addAttribute("page", 1);
			return "redirect:/vets.html";
		}
		return addPaginationModel(page, paginated, model);
	}

	private String addPaginationModel(int page, Page<Vet> paginated, Model model) {
		List<Vet> listVets = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listVets", listVets);
		return "vets/vetList";
	}

	private Page<Vet> findPaginated(int page) {
		int pageSize = 5;
		int safePage = Math.max(page, 1);
		Pageable pageable = PageRequest.of(safePage - 1, pageSize);
		return vetRepository.findAll(pageable);
	}

	/**
	 * JSON endpoint listing vets. Without the {@code page} parameter the full list is
	 * returned as a {@link Vets} wrapper (legacy behavior). With {@code page} (0-based)
	 * and an optional {@code size}, a {@link PaginatedVetsResponse} is returned. A
	 * {@code size} without {@code page} is ignored.
	 */
	@GetMapping({ "/vets" })
	public @ResponseBody ResponseEntity<Object> showResourcesVetList(@RequestParam(required = false) String page,
			@RequestParam(required = false) String size) {
		if (page == null) {
			// Here we are returning an object of type 'Vets' rather than a collection of
			// Vet objects so it is simpler for JSon/Object mapping
			Vets vets = new Vets();
			vets.getVetList().addAll(this.vetRepository.findAll());
			return ResponseEntity.ok(vets);
		}

		int pageNumber;
		int pageSize;
		try {
			pageNumber = Integer.parseInt(page.trim());
			pageSize = (size == null) ? DEFAULT_API_PAGE_SIZE : Integer.parseInt(size.trim());
		}
		catch (NumberFormatException ex) {
			return badRequest("Invalid pagination parameters: 'page' and 'size' must be numeric (integer) values");
		}
		if (pageNumber < 0) {
			return badRequest("Invalid 'page' parameter: must be a non-negative integer");
		}
		if (pageSize < 1 || pageSize > MAX_API_PAGE_SIZE) {
			return badRequest("Invalid 'size' parameter: must be an integer between 1 and " + MAX_API_PAGE_SIZE);
		}

		Page<Vet> paginated = this.vetRepository.findAll(PageRequest.of(pageNumber, pageSize));
		PaginationMetadata metadata = new PaginationMetadata(pageNumber, pageSize, paginated.getTotalElements(),
				paginated.getTotalPages());
		return ResponseEntity.ok(new PaginatedVetsResponse(paginated.getContent(), metadata));
	}

	private static ResponseEntity<Object> badRequest(String message) {
		return ResponseEntity.badRequest().body(Map.of("error", message));
	}

}
