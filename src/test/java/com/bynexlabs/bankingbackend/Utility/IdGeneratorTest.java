package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.Repository.IdEntityRepo;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.models.IdEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdGeneratorTest {

    @Mock
    private IdEntityRepo idEntityRepo;

    @InjectMocks
    private IdGenerator idGenerator;

    private IdEntity counter(Id_Type type, long code) {
        return IdEntity.builder().idType(type).idCode(code).build();
    }

    @Test
    void firstStaffIdStartsAt900100AndCreatesTheCounter() {
        when(idEntityRepo.findByIdType(Id_Type.STAFF_ID)).thenReturn(Optional.empty());

        String staffId = idGenerator.generateId(Id_Type.STAFF_ID);

        assertThat(staffId).isEqualTo("900100");
        ArgumentCaptor<IdEntity> saved = ArgumentCaptor.forClass(IdEntity.class);
        verify(idEntityRepo).save(saved.capture());
        assertThat(saved.getValue().getIdType()).isEqualTo(Id_Type.STAFF_ID);
        assertThat(saved.getValue().getIdCode()).isEqualTo(900101L);
    }

    @Test
    void staffIdContinuesFromExistingCounter() {
        IdEntity existing = counter(Id_Type.STAFF_ID, 900137L);
        when(idEntityRepo.findByIdType(Id_Type.STAFF_ID)).thenReturn(Optional.of(existing));

        String staffId = idGenerator.generateId(Id_Type.STAFF_ID);

        assertThat(staffId).isEqualTo("900137");
        assertThat(existing.getIdCode()).isEqualTo(900138L);
        verify(idEntityRepo).save(existing);
    }

    // Documents the account-number format currently in the code (counter + 5-digit time suffix).
    // The intended format is still an open decision (D7); update this test when that is settled.
    @Test
    void memberAccountNumberIsCounterFollowedByFiveDigits() {
        IdEntity existing = counter(Id_Type.MEMBER_ACCOUNT, 10042L);
        when(idEntityRepo.findByIdType(Id_Type.MEMBER_ACCOUNT)).thenReturn(Optional.of(existing));

        String accountNumber = idGenerator.generateId(Id_Type.MEMBER_ACCOUNT);

        assertThat(accountNumber).matches("10042\\d{5}");
        assertThat(existing.getIdCode()).isEqualTo(10043L);
    }
}
