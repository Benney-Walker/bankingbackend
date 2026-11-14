package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 50)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_account_id")
    private MemberAccount memberAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by")
    private Staff initiatedBy;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    /**
     * The other member account involved (V35): a transfer's destination, or the shares account
     * that receives the share part of a first deposit. Null for single-account transactions.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_account_id")
    private MemberAccount counterpartyAccount;

    /** Withdrawal fee, taken from the account on top of the amount. */
    @Builder.Default
    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    /** Share part of a first deposit; the rest of the amount goes to memberAccount. */
    @Builder.Default
    @Column(name = "share_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal shareAmount = BigDecimal.ZERO;

    /** OFFICE, or COLLECTOR for payments recorded in the collector app (V37). */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private TransactionChannel channel = TransactionChannel.OFFICE;

    /** Device time of a collector payment recorded offline; null otherwise. */
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    /** Why a collector payment couldn't be applied when its window closed (status FAILED). */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
}
