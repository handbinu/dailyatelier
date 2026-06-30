package com.dailyatelier.dailyatelier.repository;

import com.dailyatelier.dailyatelier.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, String> {
}
