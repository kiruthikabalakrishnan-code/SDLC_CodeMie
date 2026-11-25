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

/**
 * Pagination metadata returned together with a page of vets by the paginated mode of the
 * <code>/vets</code> JSON endpoint.
 *
 * @param page the requested (0-based) page index
 * @param size the page size used for the query
 * @param totalItems the total number of vets across all pages
 * @param totalPages the total number of pages available
 */
public record PaginationMetadata(int page, int size, long totalItems, int totalPages) {

}
