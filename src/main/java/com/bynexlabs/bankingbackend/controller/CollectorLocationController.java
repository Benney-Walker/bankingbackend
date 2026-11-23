package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.location.LocationPointResponse;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.service.CollectorLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Office view of collector locations. Under /api/transaction, so ADMINISTRATOR and AGENT
 * (SecurityConfig). Agents see their own branch's collectors; administrators all.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transaction/v1/collector-locations")
public class CollectorLocationController {

    private final CollectorLocationService locationService;

    private final AuthenticatedStaffProvider staffProvider;

    /** A collector's points for a day (default today), in time order, with payments placed on them. */
    @GetMapping("/{collectorStaffId}")
    public List<LocationPointResponse> trail(@PathVariable String collectorStaffId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return locationService.trail(collectorStaffId, date, staffProvider.getStaff(),
                staffProvider.hasRole(Roles.ADMINISTRATOR));
    }
}
