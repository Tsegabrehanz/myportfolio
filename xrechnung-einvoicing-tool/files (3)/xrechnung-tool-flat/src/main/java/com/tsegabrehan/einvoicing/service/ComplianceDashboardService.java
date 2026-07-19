package com.tsegabrehan.einvoicing.service;

import com.tsegabrehan.einvoicing.domain.InvoiceDirection;
import com.tsegabrehan.einvoicing.domain.InvoiceStatus;
import com.tsegabrehan.einvoicing.repository.EInvoiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FR-20, FR-21, FR-22: aggregate compliance-readiness metrics for the
 * dashboard. Deliberately simple (in-memory aggregation over the
 * EInvoice table) — a production deployment with real volume would push
 * this to database-level aggregate queries or a read-optimized reporting
 * store, but the shape of the numbers is the same either way.
 */
@Service
public class ComplianceDashboardService {

    private final EInvoiceRepository eInvoiceRepository;

    public ComplianceDashboardService(EInvoiceRepository eInvoiceRepository) {
        this.eInvoiceRepository = eInvoiceRepository;
    }

    public record DashboardSummary(
            long totalOutgoing,
            long validatedOutgoing,
            long rejectedOutgoing,
            long totalIncoming,
            long quarantinedIncoming,
            double outgoingComplianceRate,
            List<String> alerts
    ) {
    }

    public DashboardSummary summarize() {
        var outgoing = eInvoiceRepository.findByDirection(InvoiceDirection.OUTGOING);
        var incoming = eInvoiceRepository.findByDirection(InvoiceDirection.INCOMING);

        long validated = outgoing.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.VALIDATED || i.getStatus() == InvoiceStatus.SENT
                        || i.getStatus() == InvoiceStatus.ARCHIVED)
                .count();
        long rejected = outgoing.stream().filter(i -> i.getStatus() == InvoiceStatus.REJECTED).count();
        long quarantined = incoming.stream().filter(i -> i.getStatus() == InvoiceStatus.QUARANTINED).count();

        double complianceRate = outgoing.isEmpty() ? 1.0 : (double) validated / outgoing.size();

        var alerts = new java.util.ArrayList<String>();
        if (!outgoing.isEmpty() && complianceRate < 0.9) {
            alerts.add("Outgoing e-invoice compliance rate is " + String.format("%.0f%%", complianceRate * 100)
                    + " — below the 90%% readiness threshold. Review rejected invoices before the applicable "
                    + "issuance deadline (2027 for turnover > EUR 800,000; 2028 for all businesses).");
        }
        if (quarantined > 0) {
            alerts.add(quarantined + " incoming e-invoice(s) are quarantined and awaiting manual review.");
        }

        return new DashboardSummary(outgoing.size(), validated, rejected, incoming.size(), quarantined,
                complianceRate, alerts);
    }
}
