package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.location.LocationBatchRequest;
import com.bynexlabs.bankingbackend.DTO.location.LocationBatchResponse;
import com.bynexlabs.bankingbackend.DTO.location.LocationPointRequest;
import com.bynexlabs.bankingbackend.DTO.location.LocationPointResponse;
import com.bynexlabs.bankingbackend.Repository.CollectorLocationRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.LocationTrigger;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.CollectorLocation;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import com.bynexlabs.bankingbackend.models.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorLocationServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 16, 30);

    @Mock
    private CollectorLocationRepo locationRepo;
    @Mock
    private StaffRepo staffRepo;
    @Mock
    private TransactionRepo transactionRepo;

    private CollectorLocationService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Branch takoradi = Branch.builder().id(UUID.randomUUID()).name("Takoradi").build();
    private final Staff collector = withRole(Staff.builder().id(UUID.randomUUID()).staffId("900110").branch(tarkwa).build(), Roles.COLLECTOR);
    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900105").branch(tarkwa).build();
    private final Staff otherAgent = Staff.builder().id(UUID.randomUUID()).staffId("900106").branch(takoradi).build();

    @BeforeEach
    void setUp() {
        service = new CollectorLocationService(locationRepo, staffRepo, transactionRepo);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
    }

    private static Staff withRole(Staff staff, Roles role) {
        StaffRoles row = new StaffRoles();
        row.setStaff(staff);
        row.setRole(role);
        staff.setRoles(List.of(row));
        return staff;
    }

    private static LocationPointRequest point(LocationTrigger trigger, String key, LocalDateTime at) {
        return LocationPointRequest.builder().latitude(new BigDecimal("5.300100")).longitude(new BigDecimal("-1.990200"))
                .accuracyMeters(new BigDecimal("12.5")).batteryPercent(80).trigger(trigger)
                .paymentIdempotencyKey(key).recordedAt(at).build();
    }

    // --- recording ----------------------------------------------------------------------------

    @Test
    void batchStoresNewPointsAndCountsResentOnes() {
        when(locationRepo.insertIfNew(eq(collector.getId()), any(), any(), any(), any(), eq("EVERY_5_MIN"), isNull(),
                eq(NOW.minusMinutes(5)), eq(NOW))).thenReturn(1);
        when(locationRepo.insertIfNew(eq(collector.getId()), any(), any(), any(), any(), eq("PAYMENT"), eq("key-1"),
                eq(NOW.minusMinutes(1)), eq(NOW))).thenReturn(0);

        LocationBatchResponse response = service.record(new LocationBatchRequest(List.of(
                point(LocationTrigger.EVERY_5_MIN, null, NOW.minusMinutes(5)),
                point(LocationTrigger.PAYMENT, " key-1 ", NOW.minusMinutes(1)))), collector);

        assertThat(response.stored()).isEqualTo(1);
        assertThat(response.duplicates()).isEqualTo(1);
    }

    @Test
    void paymentPointNeedsTheKeyAndARegularPointMustNotHaveOne() {
        assertThatThrownBy(() -> service.record(new LocationBatchRequest(List.of(
                point(LocationTrigger.PAYMENT, null, NOW))), collector)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.record(new LocationBatchRequest(List.of(
                point(LocationTrigger.EVERY_5_MIN, "key-1", NOW))), collector)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(locationRepo);
    }

    @Test
    void aPointFromTheFutureRejectsTheWholeBatch() {
        assertThatThrownBy(() -> service.record(new LocationBatchRequest(List.of(
                point(LocationTrigger.EVERY_5_MIN, null, NOW.minusMinutes(5)),
                point(LocationTrigger.EVERY_5_MIN, null, NOW.plusMinutes(10)))), collector))
                .isInstanceOf(BadRequestException.class);
        verify(locationRepo, never()).insertIfNew(any(), any(), any(), any(), any(), anyString(), any(), any(), any());
    }

    // --- payments need a recent location (owner decision 2026-11-23) --------------------------

    @Test
    void onlinePaymentNeedsAPointReceivedInTheLast15Minutes() {
        when(locationRepo.existsByCollectorIdAndReceivedAtGreaterThanEqual(collector.getId(), NOW.minusMinutes(15)))
                .thenReturn(false, true);

        assertThatThrownBy(() -> service.requireRecentLocation(collector, null, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No location from this phone in the last 15 minutes. Turn on location and try again");
        service.requireRecentLocation(collector, null, NOW);
    }

    @Test
    void offlinePaymentNeedsAPointTakenWithin15MinutesOfIt() {
        LocalDateTime recordedAt = NOW.minusHours(2);
        when(locationRepo.existsByCollectorIdAndRecordedAtBetween(collector.getId(), recordedAt.minusMinutes(15),
                recordedAt.plusMinutes(15))).thenReturn(false);

        assertThatThrownBy(() -> service.requireRecentLocation(collector, recordedAt, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("within 15 minutes of this payment");
    }

    // --- office view ----------------------------------------------------------------------

    private CollectorLocation stored(LocationTrigger trigger, String key, LocalDateTime at) {
        CollectorLocation location = org.springframework.beans.BeanUtils.instantiateClass(CollectorLocation.class);
        ReflectionTestUtils.setField(location, "collectorId", collector.getId());
        ReflectionTestUtils.setField(location, "latitude", new BigDecimal("5.300100"));
        ReflectionTestUtils.setField(location, "longitude", new BigDecimal("-1.990200"));
        ReflectionTestUtils.setField(location, "accuracyMeters", new BigDecimal("12.50"));
        ReflectionTestUtils.setField(location, "trigger", trigger);
        ReflectionTestUtils.setField(location, "paymentIdempotencyKey", key);
        ReflectionTestUtils.setField(location, "recordedAt", at);
        ReflectionTestUtils.setField(location, "receivedAt", at);
        return location;
    }

    @Test
    void trailPlacesThisCollectorsPaymentsOnTheirPoints() {
        when(staffRepo.findByStaffId("900110")).thenReturn(Optional.of(collector));
        LocalDate day = LocalDate.of(2026, 11, 23);
        when(locationRepo.findByCollectorIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                collector.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay())).thenReturn(List.of(
                stored(LocationTrigger.EVERY_5_MIN, null, NOW.minusMinutes(10)),
                stored(LocationTrigger.PAYMENT, "key-1", NOW.minusMinutes(5))));
        Member member = new Member();
        member.setFirstName("Ama");
        member.setLastName("Mensah");
        MemberAccount account = new MemberAccount();
        account.setMember(member);
        account.setAccountNumber("1004312346");
        Transaction payment = Transaction.builder().id(UUID.randomUUID()).idempotencyKey("key-1").initiatedBy(collector)
                .transactionType(TransactionType.DEPOSIT).amount(new BigDecimal("20")).status(TransactionStatus.PENDING)
                .memberAccount(account).build();
        when(transactionRepo.findByIdempotencyKeyIn(List.of("key-1"))).thenReturn(List.of(payment));

        List<LocationPointResponse> trail = service.trail("900110", day, agent, false);

        assertThat(trail).hasSize(2);
        assertThat(trail.get(0).payment()).isNull();
        assertThat(trail.get(1).payment().memberName()).isEqualTo("Ama Mensah");
        assertThat(trail.get(1).payment().amount()).isEqualByComparingTo("20");
    }

    @Test
    void agentCantSeeAnotherBranchsCollector() {
        when(staffRepo.findByStaffId("900110")).thenReturn(Optional.of(collector));

        assertThatThrownBy(() -> service.trail("900110", null, otherAgent, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("This collector is from another branch");
        verifyNoInteractions(locationRepo);
    }

    @Test
    void administratorSeesAnyCollector() {
        when(staffRepo.findByStaffId("900110")).thenReturn(Optional.of(collector));

        assertThat(service.trail("900110", null, otherAgent, true)).isEmpty();
    }

    @Test
    void nonCollectorIsNotFound() {
        when(staffRepo.findByStaffId("900105")).thenReturn(Optional.of(withRole(agent, Roles.AGENT)));

        assertThatThrownBy(() -> service.trail("900105", null, agent, true)).isInstanceOf(ResourceNotFoundException.class);
    }
}
