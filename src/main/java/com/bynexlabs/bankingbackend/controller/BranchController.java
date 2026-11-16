package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.branch.BranchResponse;
import com.bynexlabs.bankingbackend.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Branches. ADMINISTRATOR and AGENT (SecurityConfig): agents need it to register members. */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/branch")
public class BranchController {

    private final BranchService branchService;

    @GetMapping("/v1/all")
    public List<BranchResponse> allBranches() {
        return branchService.listBranches();
    }
}
