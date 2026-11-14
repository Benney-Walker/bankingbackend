package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.CollectorRepo;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.models.Collector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CollectorService {

    private final CollectorRepo collectorRepo;

    public Collector getCollector(String collectorId) {
        return collectorRepo.findByCollectorId(collectorId)
                .orElseThrow(() -> new ConflictException("Collector account not found"));
    }
}
