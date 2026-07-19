package com.tsegabrehan.einvoicing.repository;

import com.tsegabrehan.einvoicing.domain.EInvoice;
import com.tsegabrehan.einvoicing.domain.InvoiceDirection;
import com.tsegabrehan.einvoicing.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EInvoiceRepository extends JpaRepository<EInvoice, String> {

    List<EInvoice> findByDirection(InvoiceDirection direction);

    List<EInvoice> findByStatus(InvoiceStatus status);

    List<EInvoice> findBySourceInvoiceNumber(String sourceInvoiceNumber);
}
