package com.tsegabrehan.einvoicing.repository;

import com.tsegabrehan.einvoicing.domain.TradingPartner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradingPartnerRepository extends JpaRepository<TradingPartner, String> {

    Optional<TradingPartner> findByVatId(String vatId);
}
