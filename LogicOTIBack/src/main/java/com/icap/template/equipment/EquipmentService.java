package com.icap.template.equipment;

import com.icap.template.exception.ConflictException;
import com.icap.template.exception.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EquipmentService {

    private final EquipmentRepository repository;

    public EquipmentService(EquipmentRepository repository) {
        this.repository = repository;
    }

    public List<EquipmentResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(EquipmentResponse::from)
                .toList();
    }

    public EquipmentResponse findById(Long id) {
        return EquipmentResponse.from(getEntity(id));
    }

    @Transactional
    public EquipmentResponse create(EquipmentRequest request) {
        String code = normalizeCode(request.code());
        validateUniqueCode(code, null);
        return EquipmentResponse.from(repository.save(new Equipment(code, request.name().trim(), request.active())));
    }

    @Transactional
    public EquipmentResponse update(Long id, EquipmentRequest request) {
        Equipment equipment = getEntity(id);
        String code = normalizeCode(request.code());
        validateUniqueCode(code, id);
        equipment.update(code, request.name().trim(), request.active());
        return EquipmentResponse.from(equipment);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    private Equipment getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el equipo con id " + id));
    }

    private void validateUniqueCode(String code, Long currentId) {
        repository.findByCodeIgnoreCase(code)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException("Ya existe un equipo con el código " + code);
                });
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }
}
