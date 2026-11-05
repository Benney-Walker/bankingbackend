package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TransactionService {

    private final TransactionRepo transactionRepo;
}
