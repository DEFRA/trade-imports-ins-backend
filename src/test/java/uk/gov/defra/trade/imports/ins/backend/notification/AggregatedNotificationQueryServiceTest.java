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
    private static final String ORGANISATION_ID = "5900001";

    @Mock
    private AggregatedNotificationRepository repository;

    private AggregatedNotificationQueryService service;

    @BeforeEach
    void setup() {
        service = new AggregatedNotificationQueryService(repository, PAGE_SIZE);
    }

    @Test
    void findOrganisationPage_shouldQueryByOrganisationExcludingDeleted_whenNoReferenceNumber() {
        AggregatedNotification notification = submittedNotification();
        when(repository.findAllByOrganisationIdAndStatusNot(eq(ORGANISATION_ID), eq("DELETED"), any()))
            .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, PAGE_SIZE), 1));

        AggregatedNotificationPageResponse response = service.findOrganisationPage(1, null, null, ORGANISATION_ID);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrganisationIdAndStatusNot(
            eq(ORGANISATION_ID), eq("DELETED"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(PAGE_SIZE);
        assertThat(response.content()).containsExactly(notification);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(1L);
        verify(repository, never()).findByReferenceNumberAndOrganisationIdAndStatusNot(any(), any(), any());
    }

    @Test
    void findOrganisationPage_shouldUseZeroBasedRepositoryPage_whenSecondPageRequested() {
        when(repository.findAllByOrganisationIdAndStatusNot(eq(ORGANISATION_ID), eq("DELETED"), any()))
            .thenReturn(Page.empty());

        service.findOrganisationPage(2, null, null, ORGANISATION_ID);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrganisationIdAndStatusNot(
            eq(ORGANISATION_ID), eq("DELETED"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    }

    @Test
    void findOrganisationPage_shouldReturnSingleMatchForOrganisation_whenReferenceNumberGiven() {
        AggregatedNotification notification = submittedNotification();
        when(repository.findByReferenceNumberAndOrganisationIdAndStatusNot(
                "GBN-AG-26-001", ORGANISATION_ID, "DELETED"))
            .thenReturn(Optional.of(notification));

        AggregatedNotificationPageResponse response =
            service.findOrganisationPage(1, null, "GBN-AG-26-001", ORGANISATION_ID);

        assertThat(response.content()).containsExactly(notification);
        assertThat(response.totalElements()).isEqualTo(1L);
        verify(repository, never()).findAllByOrganisationIdAndStatusNot(any(), any(), any());
    }

    @Test
    void findOrganisationPage_shouldReturnEmptyPage_whenReferenceNotFoundForOrganisation() {
        when(repository.findByReferenceNumberAndOrganisationIdAndStatusNot(
                "GBN-AG-26-999", ORGANISATION_ID, "DELETED"))
            .thenReturn(Optional.empty());

        AggregatedNotificationPageResponse response =
            service.findOrganisationPage(1, null, "GBN-AG-26-999", ORGANISATION_ID);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void findOrganisationPage_shouldReportFirstPage_whenReferenceMatchRegardlessOfRequestedPage() {
        AggregatedNotification notification = submittedNotification();
        when(repository.findByReferenceNumberAndOrganisationIdAndStatusNot(
                "GBN-AG-26-001", ORGANISATION_ID, "DELETED"))
            .thenReturn(Optional.of(notification));

        AggregatedNotificationPageResponse response =
            service.findOrganisationPage(3, null, "GBN-AG-26-001", ORGANISATION_ID);

        assertThat(response.content()).containsExactly(notification);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(1L);
    }

    @Test
    void findOrganisationPage_shouldTrimReferenceBeforeOrganisationLookup_whenReferenceHasPadding() {
        when(repository.findByReferenceNumberAndOrganisationIdAndStatusNot(
                "GBN-AG-26-001", ORGANISATION_ID, "DELETED"))
            .thenReturn(Optional.empty());

        service.findOrganisationPage(1, null, "  GBN-AG-26-001  ", ORGANISATION_ID);

        verify(repository).findByReferenceNumberAndOrganisationIdAndStatusNot(
            "GBN-AG-26-001", ORGANISATION_ID, "DELETED");
    }

    @Test
    void findOrganisationPage_shouldListOrganisation_whenReferenceNumberIsBlank() {
        when(repository.findAllByOrganisationIdAndStatusNot(eq(ORGANISATION_ID), eq("DELETED"), any()))
            .thenReturn(Page.empty());

        service.findOrganisationPage(1, null, "   ", ORGANISATION_ID);

        verify(repository).findAllByOrganisationIdAndStatusNot(eq(ORGANISATION_ID), eq("DELETED"), any());
        verify(repository, never()).findByReferenceNumberAndOrganisationIdAndStatusNot(any(), any(), any());
    }

    private static AggregatedNotification submittedNotification() {
        return AggregatedNotification.builder()
            .aggregateId("agg-1")
            .referenceNumber("GBN-AG-26-001")
            .status("SUBMITTED")
            .build();
    }
}