package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepo transactionRepo;

    @InjectMocks
    private TransactionService transactionService;

    private Transaction recorded() {
        return Transaction.builder()
                .idempotencyKey("key-1")
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("50.0000"))
                .memberAccount(MemberAccount.builder().accountNumber("1000000001").build())
                .status(TransactionStatus.COMPLETED)
                .build();
    }

    @Test
    void newKeyHasNothingToReplay() {
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        assertThat(transactionService.findReplay("key-1", TransactionType.DEPOSIT, "1000000001", new BigDecimal("50")))
                .isEmpty();
    }

    // A retry of the same request returns the transaction already recorded, so it isn't applied twice.
    @Test
    void sameKeyAndSameRequestReturnsTheRecordedTransaction() {
        Transaction existing = recorded();
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThat(transactionService.findReplay(" key-1 ", TransactionType.DEPOSIT, "1000000001", new BigDecimal("50")))
                .containsSame(existing);
    }

    @Test
    void sameKeyWithADifferentAmountIsAConflict() {
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(recorded()));

        assertThatThrownBy(() -> transactionService.findReplay("key-1", TransactionType.DEPOSIT, "1000000001", new BigDecimal("60")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This idempotency key was already used for a different transaction");
    }

    @Test
    void sameKeyWithADifferentAccountOrTypeIsAConflict() {
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(recorded()));

        assertThatThrownBy(() -> transactionService.findReplay("key-1", TransactionType.DEPOSIT, "1000000002", new BigDecimal("50")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> transactionService.findReplay("key-1", TransactionType.WITHDRAWAL, "1000000001", new BigDecimal("50")))
                .isInstanceOf(ConflictException.class);
    }

    // A transfer retry must name the same destination too.
    @Test
    void sameKeyWithADifferentDestinationIsAConflict() {
        Transaction transfer = recorded();
        transfer.setTransactionType(TransactionType.TRANSFER);
        transfer.setCounterpartyAccount(MemberAccount.builder().accountNumber("1000000002").build());
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(transfer));

        assertThat(transactionService.findReplay("key-1", TransactionType.TRANSFER, "1000000001", "1000000002", new BigDecimal("50")))
                .containsSame(transfer);
        assertThatThrownBy(() -> transactionService.findReplay("key-1", TransactionType.TRANSFER, "1000000001", "1000000003", new BigDecimal("50")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void blankOrOverlongKeyIsRejected() {
        assertThatThrownBy(() -> transactionService.findReplay(" ", TransactionType.DEPOSIT, "1000000001", BigDecimal.ONE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency key is required");
        assertThatThrownBy(() -> transactionService.findReplay("k".repeat(101), TransactionType.DEPOSIT, "1000000001", BigDecimal.ONE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency key must be at most 100 characters");
    }

    // Two simultaneous retries: the unique constraint stops the second one.
    @Test
    void duplicateKeyAtSaveTimeIsAConflict() {
        Transaction transaction = recorded();
        when(transactionRepo.saveAndFlush(transaction)).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> transactionService.record(transaction))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A transaction with this idempotency key is already being processed");
    }
}
