package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.BranchRepo;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.models.Branch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class BranchService {

    private final BranchRepo branchRepo;

    public Branch getBranchDetails(UUID branchId){
        return branchRepo.findById(branchId).orElseThrow(()
                -> new ConflictException("Branch information cannot be found"));
    }
}
