package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.branch.BranchResponse;
import com.bynexlabs.bankingbackend.Repository.BranchRepo;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.models.Branch;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class BranchService {

    private final BranchRepo branchRepo;

    public Branch getBranchDetails(UUID branchId){
        return branchRepo.findById(branchId).orElseThrow(()
                -> new ConflictException("Branch information cannot be found"));
    }

    /** Every branch, by name: for choosing a branch when adding staff or registering a member. */
    @Transactional(readOnly = true)
    public List<BranchResponse> listBranches() {
        return branchRepo.findAll(Sort.by(Sort.Direction.ASC, "name")).stream().map(BranchResponse::from).toList();
    }
}
