package uk.gov.defra.trade.imports.ins.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.defra.trade.imports.ins.backend.domain.Example;
import uk.gov.defra.trade.imports.ins.backend.service.ExampleService;

@ExtendWith(MockitoExtension.class)
class ExampleControllerTest {

    @Mock
    private ExampleService exampleService;

    @InjectMocks
    private ExampleController controller;

    @Test
    void findAll_returnsServiceResult() {
        Example example = new Example("name", "value");
        when(exampleService.findAll()).thenReturn(List.of(example));

        List<Example> result = controller.findAll();

        assertThat(result).containsExactly(example);
    }

    @Test
    void findById_delegatesToServiceWithId() {
        Example example = new Example("name", "value");
        when(exampleService.findById("id-123")).thenReturn(example);

        Example result = controller.findById("id-123");

        assertThat(result).isEqualTo(example);
    }

    @Test
    void create_delegatesToServiceAndReturnsCreatedEntity() {
        Example entity = new Example("name", "value");
        Example saved = new Example("name", "value");
        saved.setId("id-123");
        when(exampleService.create(entity)).thenReturn(saved);

        Example result = controller.create(entity);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    void update_delegatesToServiceWithIdAndEntity() {
        Example entity = new Example("updated-name", "updated-value");
        Example updated = new Example("updated-name", "updated-value");
        updated.setId("id-123");
        when(exampleService.update("id-123", entity)).thenReturn(updated);

        Example result = controller.update("id-123", entity);

        assertThat(result).isEqualTo(updated);
    }

    @Test
    void delete_delegatesToServiceWithId() {
        controller.delete("id-123");

        verify(exampleService).delete("id-123");
    }
}