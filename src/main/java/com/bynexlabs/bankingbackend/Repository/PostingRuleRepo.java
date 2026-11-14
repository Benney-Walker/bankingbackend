package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.models.PostingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostingRuleRepo extends JpaRepository<PostingRule, Integer> {

    @Query("select r from PostingRule r join fetch r.glAccount where r.event = :event order by r.id")
    List<PostingRule> findByEventWithAccount(@Param("event") PostingEvent event);
}
