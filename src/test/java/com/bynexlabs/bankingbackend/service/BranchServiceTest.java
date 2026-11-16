package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.branch.BranchResponse;
import com.bynexlabs.bankingbackend.Repository.BranchRepo;
import com.bynexlabs.bankingbackend.models.Branch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

    @Mock
    private BranchRepo branchRepo;

    @InjectMocks
    private BranchService branchService;

    @Test
    void listsEveryBranchByNameWithIdNameAndLocation() {
        Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").location("Tarkwa, Western Region").build();
        when(branchRepo.findAll(Sort.by(Sort.Direction.ASC, "name"))).thenReturn(List.of(tarkwa));

        List<BranchResponse> branches = branchService.listBranches();

        assertThat(branches).containsExactly(new BranchResponse(tarkwa.getId(), "Tarkwa", "Tarkwa, Western Region"));
    }
}
