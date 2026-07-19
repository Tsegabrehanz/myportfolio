package com.tsegabrehan.einvoicing.repository;

import com.tsegabrehan.einvoicing.domain.TransmissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransmissionRecordRepository extends JpaRepository<TransmissionRecord, String> {

    List<TransmissionRecord> findByEInvoiceId(String eInvoiceId);
}
