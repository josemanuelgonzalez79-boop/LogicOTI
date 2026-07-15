package com.icap.template.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    Optional<Equipment> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
