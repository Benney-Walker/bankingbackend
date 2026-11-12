package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Account-creation consumers (roadmap 2.2): a redelivered message must not create a second account. */
@ExtendWith(MockitoExtension.class)
class AsyncServiceAccountCreationTest {

    @Mock
    private MemberService memberService;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private MemberAccountRepo memberAccountRepo;

    @Mock
    private StorageService storageService;

    @Mock
    private KycService kycService;

    @Mock
    private ProcessedMessageService processedMessageService;

    @InjectMocks
    private AsyncService asyncService;

    private final UUID memberId = UUID.randomUUID();

    @Test
    void firstDeliveryCreatesAnInactiveSharesAccount() {
        Member member = new Member();
        member.setId(memberId);
        when(processedMessageService.markFirstDelivery("msg-1", RabbitMQConfig.DEFAULT_SHARES_ACCOUNT_QUEUE_NAME))
                .thenReturn(true);
        when(memberService.getMemberDetails(memberId)).thenReturn(member);
        when(idGenerator.generateId(Id_Type.MEMBER_ACCOUNT)).thenReturn("1004212345");

        asyncService.createDefaultSharesAccount(SharesAccountEvent.builder().memberId(memberId).build(), "msg-1");

        ArgumentCaptor<MemberAccount> saved = ArgumentCaptor.forClass(MemberAccount.class);
        verify(memberAccountRepo).save(saved.capture());
        assertThat(saved.getValue().getAccountType()).isEqualTo(AccountType.SHARES);
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(saved.getValue().getCurrentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getValue().getMember()).isSameAs(member);
    }

    @Test
    void duplicateSharesAccountMessageIsSkipped() {
        when(processedMessageService.markFirstDelivery("msg-1", RabbitMQConfig.DEFAULT_SHARES_ACCOUNT_QUEUE_NAME))
                .thenReturn(false);

        asyncService.createDefaultSharesAccount(SharesAccountEvent.builder().memberId(memberId).build(), "msg-1");

        verifyNoInteractions(memberService, idGenerator, memberAccountRepo);
    }

    @Test
    void duplicateOtherAccountMessageIsSkipped() {
        when(processedMessageService.markFirstDelivery("msg-2", RabbitMQConfig.OTHER_ACCOUNT_CREATION_QUEUE_NAME))
                .thenReturn(false);

        asyncService.createOtherAccount(OtherAccountCreationEvent.builder()
                .memberId(memberId).accountType(AccountType.SAVINGS).build(), "msg-2");

        verifyNoInteractions(memberService, idGenerator, memberAccountRepo);
    }
}
