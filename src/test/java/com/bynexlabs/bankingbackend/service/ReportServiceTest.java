package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.BranchRepo;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Staff;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Who sees which branch (owner decision 2026-11-24). The SQL itself was run against PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private EntityManager entityManager;
    @Mock private BranchRepo branchRepo;

    private ReportService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Branch bogoso = Branch.builder().id(UUID.randomUUID()).name("Bogoso").build();
    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900120").branch(tarkwa).build();
    private final Staff admin = Staff.builder().id(UUID.randomUUID()).staffId("900001").branch(tarkwa).build();

    @BeforeEach
    void setUp() {
        service = new ReportService(entityManager, branchRepo);
    }

    @Test
    void administratorSeesAllBranchesByDefault() {
        ReportService.Scope scope = service.scope(admin, true, null);

        assertThat(scope.branchId()).isNull();
        assertThat(scope.branchName()).isEqualTo("All branches");
    }

    @Test
    void administratorCanPickABranch() {
        when(branchRepo.findById(bogoso.getId())).thenReturn(Optional.of(bogoso));

        assertThat(service.scope(admin, true, bogoso.getId()).branchName()).isEqualTo("Bogoso");
    }

    @Test
    void agentSeesTheirOwnBranch() {
        ReportService.Scope scope = service.scope(agent, false, null);

        assertThat(scope.branchId()).isEqualTo(tarkwa.getId());
        assertThat(scope.branchName()).isEqualTo("Tarkwa");
    }

    @Test
    void agentCantSeeAnotherBranch() {
        assertThatThrownBy(() -> service.scope(agent, false, bogoso.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Agents can only see their own branch's reports");
    }

    @Test
    void dateRangeIsChecked() {
        assertThatThrownBy(() -> service.transactions(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), null, admin, true))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(entityManager);
    }
}
