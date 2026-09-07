package uk.gov.defra.trade.imports.ins.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AggregatedNotificationQueryServiceTest {

    private static final int PAGE_SIZE = 25;

    @Mock
    private AggregatedNotificationRepository repository;

    private AggregatedNotificationQueryService service;

    @BeforeEach
    void setup() {
        service = new AggregatedNotificationQueryService(repository, PAGE_SIZE);
    }

    @Test
    void findAll_withoutReferenceNumber_queriesRepositoryExcludingDeleted() {
        AggregatedNotification notification = AggregatedNotification.builder()
            .aggregateId("agg-1")
            .referenceNumber("GBN-AG-26-001")
            .status("SUBMITTED")
            .build();
        Pageable expectedPageable = PageRequest.of(0, PAGE_SIZE);
        when(repository.findAllByStatusNot(eq("DELETED"), any()))
            .thenReturn(new PageImpl<>(List.of(notification), expectedPageable, 1));

        AggregatedNotificationPageResponse response = service.findAll(1, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByStatusNot(eq("DELETED"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(PAGE_SIZE);
        assertThat(response.content()).containsExactly(notification);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(1L);
        verify(repository, never()).findByReferenceNumberAndStatusNot(any(), any());
    }

    @Test
    void findAll_secondPage_isZeroIndexedAgainstRepository() {
        when(repository.findAllByStatusNot(eq("DELETED"), any()))
            .thenReturn(Page.empty());

        service.findAll(2, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByStatusNot(eq("DELETED"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    }

    @Test
    void findAll_withReferenceNumber_returnsSingleMatch_notFullList() {
        AggregatedNotification notification = AggregatedNotification.builder()
            .aggregateId("agg-1")
            .referenceNumber("GBN-AG-26-001")
            .status("SUBMITTED")
            .build();
        when(repository.findByReferenceNumberAndStatusNot("GBN-AG-26-001", "DELETED"))
            .thenReturn(Optional.of(notification));

        AggregatedNotificationPageResponse response = service.findAll(1, null, "GBN-AG-26-001");

        assertThat(response.content()).containsExactly(notification);
        assertThat(response.totalElements()).isEqualTo(1L);
        verify(repository, never()).findAllByStatusNot(any(), any());
    }

    @Test
    void findAll_referenceNumberWithNoMatch_returnsEmptyPage_notAnError() {
        when(repository.findByReferenceNumberAndStatusNot("GBN-AG-26-999", "DELETED"))
            .thenReturn(Optional.empty());

        AggregatedNotificationPageResponse response = service.findAll(1, null, "GBN-AG-26-999");

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void findAll_referenceNumberIsTrimmedBeforeLookup() {
        when(repository.findByReferenceNumberAndStatusNot("GBN-AG-26-001", "DELETED"))
            .thenReturn(Optional.empty());

        service.findAll(1, null, "  GBN-AG-26-001  ");

        verify(repository).findByReferenceNumberAndStatusNot("GBN-AG-26-001", "DELETED");
    }

    @Test
    void findAll_blankReferenceNumber_isTreatedAsAbsent() {
        when(repository.findAllByStatusNot(eq("DELETED"), any()))
            .thenReturn(Page.empty());

        service.findAll(1, null, "   ");

        verify(repository).findAllByStatusNot(eq("DELETED"), any());
        verify(repository, never()).findByReferenceNumberAndStatusNot(any(), any());
    }
}
