package com.tsegabrehan.einvoicing.service;

import com.tsegabrehan.einvoicing.domain.*;
import com.tsegabrehan.einvoicing.repository.ValidationResultRepository;
import com.tsegabrehan.einvoicing.validation.En16931Validator;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FR-6, FR-7, FR-8: validates a source invoice and persists the outcome.
 */
@Service
public class ValidationService {

    private final En16931Validator validator;
    private final ValidationResultRepository validationResultRepository;

    public ValidationService(En16931Validator validator, ValidationResultRepository validationResultRepository) {
        this.validator = validator;
        this.validationResultRepository = validationResultRepository;
    }

    public ValidationResult validateAndRecord(String eInvoiceId, SourceInvoice sourceInvoice) {
        En16931Validator.ValidationOutcomeResult result = validator.validate(sourceInvoice);
        ValidationResult record = new ValidationResult(
                eInvoiceId,
                En16931Validator.RULE_SET_VERSION,
                result.outcome(),
                result.issues()
        );
        return validationResultRepository.save(record);
    }

    public List<ValidationResult> history(String eInvoiceId) {
        return validationResultRepository.findByEInvoiceId(eInvoiceId);
    }
}
