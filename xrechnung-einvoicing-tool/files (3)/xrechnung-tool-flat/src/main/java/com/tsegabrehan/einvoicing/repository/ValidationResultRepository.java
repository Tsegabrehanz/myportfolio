package com.tsegabrehan.einvoicing.repository;

import com.tsegabrehan.einvoicing.domain.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ValidationResultRepository extends JpaRepository<ValidationResult, String> {

    List<ValidationResult> findByEInvoiceId(String eInvoiceId);

    Optional<ValidationResult> findFirstByEInvoiceIdOrderByValidatedAtDesc(String eInvoiceId);
}
