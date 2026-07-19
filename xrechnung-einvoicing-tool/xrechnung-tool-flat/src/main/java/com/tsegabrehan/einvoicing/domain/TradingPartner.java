package com.tsegabrehan.einvoicing.domain;

import jakarta.persistence.*;

/**
 * SRS 5. Data Model — TradingPartner (FR-13: per-recipient transmission preference).
 */
@Entity
@Table(name = "trading_partner")
public class TradingPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "peppol_participant_id")
    private String peppolParticipantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_transmission_method", nullable = false)
    private TransmissionMethod preferredTransmissionMethod;

    @Column(name = "vat_id")
    private String vatId;

    /** Kleinunternehmer (small-business VAT exemption) — see SRS 9, Open Questions. */
    @Column(name = "kleinunternehmer")
    private boolean kleinunternehmer;

    protected TradingPartner() {
        // JPA
    }

    public TradingPartner(String name, String vatId, String peppolParticipantId,
                           TransmissionMethod preferredTransmissionMethod, boolean kleinunternehmer) {
        this.name = name;
        this.vatId = vatId;
        this.peppolParticipantId = peppolParticipantId;
        this.preferredTransmissionMethod = peppolParticipantId != null
                ? preferredTransmissionMethod
                : TransmissionMethod.EMAIL; // FR-13: fall back to email when no Peppol capability known
        this.kleinunternehmer = kleinunternehmer;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPeppolParticipantId() {
        return peppolParticipantId;
    }

    public TransmissionMethod getPreferredTransmissionMethod() {
        return preferredTransmissionMethod;
    }

    public String getVatId() {
        return vatId;
    }

    public boolean isKleinunternehmer() {
        return kleinunternehmer;
    }
}
