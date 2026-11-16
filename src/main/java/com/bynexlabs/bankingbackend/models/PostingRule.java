package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.EntryType;
import com.bynexlabs.bankingbackend.enums.MatchSide;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One posting rule (V34): for this event, put this part of the amount on this side of this GL
 * account, optionally only when a member account on the given side has the given type. The
 * rules are data so the credit union's accountant can change them without a code change.
 */
@Entity
@Table(name = "posting_rule")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 40)
    private PostingEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_part", nullable = false, length = 20)
    private AmountPart amountPart;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account_id", nullable = false)
    private GLAccount glAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_side", length = 20)
    private MatchSide matchSide;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_account_type", length = 20)
    private AccountType matchAccountType;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
