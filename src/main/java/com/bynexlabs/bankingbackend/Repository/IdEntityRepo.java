package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.IdEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdEntityRepo extends JpaRepository<IdEntity, Long> {
}
