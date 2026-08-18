package uk.gov.defra.trade.imports.ins.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.defra.trade.imports.ins.backend.domain.Example;
import uk.gov.defra.trade.imports.ins.backend.domain.repository.ExampleRepository;
import uk.gov.defra.trade.imports.ins.backend.exceptions.ConflictException;
import uk.gov.defra.trade.imports.ins.backend.exceptions.NotFoundException;

@ExtendWith(MockitoExtension.class)
class ExampleServiceTest {

    @Mock
    private ExampleRepository repository;

    @Mock
    private MeterRegistry meterRegistry;

    private ExampleService service;

    @BeforeEach
    void setUp() {
        service = new ExampleService(repository, meterRegistry);
    }

    @Test
    void create_savesEntitySetsCreatedTimestampAndReturnsResult() {
        Example entity = new Example("test-name", "test-value");
        Example saved = new Example("test-name", "test-value");
        saved.setId("id-123");
        Counter counter = mock(Counter.class);

        when(repository.findByName("test-name")).thenReturn(Optional.empty());
        when(repository.save(entity)).thenReturn(saved);
        when(meterRegistry.counter("example_created")).thenReturn(counter);

        Example result = service.create(entity);

        assertThat(result).isEqualTo(saved);
        assertThat(entity.getCreated()).isNotNull();
        verify(counter).increment();
    }

    @Test
    void create_throwsConflictExceptionWhenNameAlreadyExists() {
        Example entity = new Example("existing-name", "value");
        when(repository.findByName("existing-name")).thenReturn(Optional.of(new Example("existing-name", "other")));

        assertThatThrownBy(() -> service.create(entity))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void findAll_returnsAllExamplesFromRepository() {
        Example e1 = new Example("name-1", "value-1");
        Example e2 = new Example("name-2", "value-2");
        when(repository.findAll()).thenReturn(List.of(e1, e2));

        List<Example> result = service.findAll();

        assertThat(result).containsExactly(e1, e2);
    }

    @Test
    void findById_returnsExampleWhenFound() {
        Example example = new Example("name", "value");
        example.setId("id-123");
        when(repository.findById("id-123")).thenReturn(Optional.of(example));

        Example result = service.findById("id-123");

        assertThat(result).isEqualTo(example);
    }

    @Test
    void findById_throwsNotFoundExceptionWhenEntityDoesNotExist() {
        when(repository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("unknown-id"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_updatesFieldsAndReturnsUpdatedEntity() {
        Example existing = new Example("original-name", "original-value");
        existing.setId("id-123");
        Example request = new Example("new-name", "new-value");
        request.setCounter(5);
        Counter counter = mock(Counter.class);

        when(repository.findById("id-123")).thenReturn(Optional.of(existing));
        when(repository.findByName("new-name")).thenReturn(Optional.empty());
        when(repository.save(existing)).thenReturn(existing);
        when(meterRegistry.counter("example_updated")).thenReturn(counter);

        Example result = service.update("id-123", request);

        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getValue()).isEqualTo("new-value");
        assertThat(result.getCounter()).isEqualTo(5);
        verify(counter).increment();
    }

    @Test
    void update_throwsNotFoundExceptionWhenEntityDoesNotExist() {
        when(repository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("unknown-id", new Example("name", "value")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_throwsConflictExceptionWhenNewNameConflictsWithAnotherEntity() {
        Example existing = new Example("original-name", "value");
        existing.setId("id-123");
        Example conflicting = new Example("taken-name", "other");
        conflicting.setId("id-456");

        when(repository.findById("id-123")).thenReturn(Optional.of(existing));
        when(repository.findByName("taken-name")).thenReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> service.update("id-123", new Example("taken-name", "value")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_deletesEntityFromRepository() {
        Example existing = new Example("name", "value");
        existing.setId("id-123");
        Counter counter = mock(Counter.class);

        when(repository.findById("id-123")).thenReturn(Optional.of(existing));
        when(meterRegistry.counter("example_deleted")).thenReturn(counter);

        service.delete("id-123");

        verify(repository).deleteById("id-123");
        verify(counter).increment();
    }

    @Test
    void delete_throwsNotFoundExceptionWhenEntityDoesNotExist() {
        when(repository.findById("unknown-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("unknown-id"))
                .isInstanceOf(NotFoundException.class);
    }
}