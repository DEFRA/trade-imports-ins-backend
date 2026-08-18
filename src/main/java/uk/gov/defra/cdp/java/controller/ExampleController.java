package uk.gov.defra.cdp.java.controller;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.defra.cdp.java.domain.Example;
import uk.gov.defra.cdp.java.service.ExampleService;

/**
 * REST API for Example CRUD operations.
 *
 * Demonstrates CDP compliance:
 * - REST endpoints with proper HTTP methods and status codes
 * - Bean Validation on request bodies
 * - OpenAPI/Swagger documentation
 * - Request tracing via MDC (automatic via RequestTracingFilter)
 * - Structured logging in ECS format
 */
@RestController
@RequestMapping("/example")
@Tag(name = "Example API", description = "CRUD operations for examples (CDP compliance demonstration)")
@Slf4j
@AllArgsConstructor
public class ExampleController {

    private final ExampleService exampleService;

    /**
     * Create a new example.
     *
     * @param entity the example to create (validated)
     * @return the created example with generated ID
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create example", description = "Creates a new example with unique name")
    @Timed("controller.postExampleEntity.time")
    public Example create(@Valid @RequestBody Example entity) {
        log.info("POST /example - Creating example with name: {}", entity.getName());
        return exampleService.create(entity);
    }

    /**
     * Get all examples.
     *
     * @return list of all examples
     */
    @GetMapping
    @Operation(summary = "List examples", description = "Returns all examples")
    @Timed("controller.getAllExampleEntities.time")
    @Counted(value="controller.getAllExampleEntities.count", description = "Total number of examples fetched")
    public List<Example> findAll() {
        log.debug("GET /example - Fetching all examples");
        return exampleService.findAll();
    }

    /**
     * Get an example by ID.
     *
     * @param id the example ID
     * @return the example
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get example by ID", description = "Returns a single example by ID")
    @Timed("controller.getExampleEntity.time")
    public Example findById(@PathVariable String id) {
        log.debug("GET /example/{} - Fetching example", id);
        return exampleService.findById(id);
    }

    /**
     * Update an existing example.
     *
     * @param id     the example ID
     * @param entity the updated example data (validated)
     * @return the updated example
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update example", description = "Updates an existing example")
    @Timed("controller.putExampleEntity.time")
    public Example update(@PathVariable String id, @Valid @RequestBody Example entity) {
        log.info("PUT /example/{} - Updating example", id);
        return exampleService.update(id, entity);
    }

    /**
     * Delete an example.
     *
     * @param id the example ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete example", description = "Deletes an example by ID")
    @Timed("controller.deleteExampleEntity.time")
    public void delete(@PathVariable String id) {
        log.info("DELETE /example/{} - Deleting example", id);
        exampleService.delete(id);
    }
}
