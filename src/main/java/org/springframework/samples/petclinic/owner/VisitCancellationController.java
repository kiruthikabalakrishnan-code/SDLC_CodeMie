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

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Cancels a booked {@link Visit}. Kept separate from {@link VisitController} because that
 * controller's {@code @ModelAttribute} methods add a new, blank visit to the pet and
 * throw for unknown owners or pets, which must not happen when cancelling.
 */
@Controller
class VisitCancellationController {

	private final OwnerRepository owners;

	public VisitCancellationController(OwnerRepository owners) {
		this.owners = owners;
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/visits/{visitId}/delete")
	public String cancelVisit(@PathVariable int ownerId, @PathVariable int petId, @PathVariable int visitId,
			RedirectAttributes redirectAttributes) {
		Optional<Owner> optionalOwner = owners.findById(ownerId);
		if (optionalOwner.isEmpty()) {
			redirectAttributes.addFlashAttribute("error", "Owner not found with id: " + ownerId + ".");
			return "redirect:/owners/{ownerId}";
		}

		Owner owner = optionalOwner.get();
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			redirectAttributes.addFlashAttribute("error",
					"Pet with id " + petId + " not found for owner with id " + ownerId + ".");
			return "redirect:/owners/{ownerId}";
		}

		boolean removed = pet.getVisits().removeIf(visit -> visit.getId() != null && visit.getId() == visitId);
		if (!removed) {
			redirectAttributes.addFlashAttribute("error",
					"Visit with id " + visitId + " not found for pet with id " + petId + ".");
			return "redirect:/owners/{ownerId}";
		}

		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "Visit cancelled successfully.");
		return "redirect:/owners/{ownerId}";
	}

}
