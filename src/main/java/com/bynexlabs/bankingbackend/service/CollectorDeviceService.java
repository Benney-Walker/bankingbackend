package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.SessionEndReason;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * One phone per collector (owner decisions 2026-11-23, increment #7, D36):
 * - the app reads the phone's own device ID and sends it at collector-login and refresh
 *   (revised 2026-11-23 from an app install ID; the backend treats it as an opaque string);
 * - the first sign-in registers that phone; sign-in from another phone is refused (403) until
 *   the phone is released;
 * - the branch's agent or an administrator releases it (lost, replaced or reinstalled phone),
 *   which also signs the collector out everywhere.
 */
@Service
@RequiredArgsConstructor
public class CollectorDeviceService {

    private final StaffRepo staffRepo;

    private final SessionService sessionService;

    private final AuditLogService auditLogService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    /** At collector-login: registers the first phone, accepts the registered one, refuses others. */
    @Transactional
    public void requireRegisteredDevice(Staff collector, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new BadRequestException("deviceId is required to sign in to the collector app");
        }
        String device = deviceId.trim();
        // Locked, so two phones signing in for the first time at once can't both register.
        Staff locked = staffRepo.lockById(collector.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Collector not found"));
        if (locked.getRegisteredDeviceId() == null) {
            staffRepo.registerDevice(locked.getId(), device, LocalDateTime.now(clock));
            auditLogService.record(AuditAction.COLLECTOR_DEVICE_REGISTERED, AuditTargetType.STAFF, locked.getStaffId(), null);
            return;
        }
        if (!locked.getRegisteredDeviceId().equals(device)) {
            throw new ForbiddenException("This account is registered to another phone. "
                    + "Ask your branch's agent or an administrator to release it");
        }
    }

    /** Releases the collector's phone and ends their sessions. Agents: own branch's collectors only. */
    @Transactional
    public void release(String collectorStaffId, Staff releasedBy, boolean releasedByAdmin) {
        Staff collector = staffRepo.findByStaffId(collectorStaffId)
                .filter(staff -> staff.getRoles() != null
                        && staff.getRoles().stream().anyMatch(role -> role.getRole() == Roles.COLLECTOR))
                .orElseThrow(() -> new ResourceNotFoundException("Collector not found"));
        if (!releasedByAdmin && (releasedBy.getBranch() == null || collector.getBranch() == null
                || !releasedBy.getBranch().getId().equals(collector.getBranch().getId()))) {
            throw new ForbiddenException("This collector is from another branch");
        }
        if (staffRepo.releaseDevice(collector.getId()) == 0) {
            throw new ConflictException("This collector has no registered phone");
        }
        sessionService.endAllSessions(collector.getId(), SessionEndReason.DEVICE_RELEASED);
        auditLogService.record(AuditAction.COLLECTOR_DEVICE_RELEASED, AuditTargetType.STAFF, collectorStaffId,
                "Released by " + releasedBy.getStaffId());
    }
}
