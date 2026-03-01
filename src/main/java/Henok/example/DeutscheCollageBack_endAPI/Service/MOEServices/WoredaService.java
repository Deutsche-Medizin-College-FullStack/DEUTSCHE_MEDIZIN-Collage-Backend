package Henok.example.DeutscheCollageBack_endAPI.Service.MOEServices;


import Henok.example.DeutscheCollageBack_endAPI.DTO.MOE_DTOs.WoredaDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Woreda;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Zone;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.WoredaRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.ZoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WoredaService {

    @Autowired
    private WoredaRepository woredaRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    // Add multiple woredas with validation using DTO
    // Ensures no duplicates and valid zone references
    public List<WoredaDTO> addMultipleWoredas(List<WoredaDTO> woredaDTOs) {
        if (woredaDTOs == null || woredaDTOs.isEmpty()) {
            throw new IllegalArgumentException("Woreda list cannot be null or empty");
        }

        List<WoredaDTO> savedWoredaDTOs = new ArrayList<>();

        for (WoredaDTO woredaDTO : woredaDTOs) {
            if (woredaDTO.getWoredaCode() == null || woredaDTO.getWoredaCode().isEmpty()) {
                throw new IllegalArgumentException("Woreda code cannot be null or empty");
            }
            if (woredaRepository.existsByWoredaCode(woredaDTO.getWoredaCode())) {
                throw new DataIntegrityViolationException("Woreda with code " + woredaDTO.getWoredaCode() + " already exists");
            }

            if (woredaDTO.getZoneCode() == null || woredaDTO.getZoneCode().isEmpty()) {
                throw new IllegalArgumentException("Zone code is required for Woreda");
            }

            Zone zone = zoneRepository.findByZoneCode(woredaDTO.getZoneCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Zone with code " + woredaDTO.getZoneCode() + " not found"));

            Woreda woreda = mapToEntity(woredaDTO, zone);
            Woreda savedWoreda = woredaRepository.save(woreda);
            savedWoredaDTOs.add(mapToDTO(savedWoreda));
        }

        return savedWoredaDTOs;
    }

    // Find woreda by code and return DTO
    // Throws ResourceNotFoundException if not found
    public WoredaDTO findByWoredaCode(String woredaCode) {
        if (woredaCode == null || woredaCode.isEmpty()) {
            throw new IllegalArgumentException("Woreda code cannot be null or empty");
        }
        Woreda woreda = woredaRepository.findByWoredaCode(woredaCode)
                .orElseThrow(() -> new ResourceNotFoundException("Woreda with code " + woredaCode + " not found"));
        return mapToDTO(woreda);
    }

    // Retrieve all woredas as DTOs
    public List<WoredaDTO> findAll() {
        List<Woreda> woredas = woredaRepository.findAll();
        return woredas.isEmpty() ? Collections.emptyList() : woredas.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Find woredas by zone code and return DTOs
    // Throws ResourceNotFoundException if none found
    public List<WoredaDTO> findByZoneCode(String zoneCode) {
        if (zoneCode == null || zoneCode.isEmpty()) {
            throw new IllegalArgumentException("Zone code cannot be null or empty");
        }
        List<Woreda> woredas = woredaRepository.findByZoneZoneCode(zoneCode);
        if (woredas.isEmpty()) {
            throw new ResourceNotFoundException("No woredas found for zone code: " + zoneCode);
        }
        return woredas.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Find woredas by region code and return DTOs
    // Throws ResourceNotFoundException if none found
    public List<WoredaDTO> findByRegionCode(String regionCode) {
        if (regionCode == null || regionCode.isEmpty()) {
            throw new IllegalArgumentException("Region code cannot be null or empty");
        }
        List<Woreda> woredas = woredaRepository.findByZoneRegionRegionCode(regionCode);
        if (woredas.isEmpty()) {
            throw new ResourceNotFoundException("No woredas found for region code: " + regionCode);
        }
        return woredas.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void deleteAll() {
        woredaRepository.deleteAll();
    }

    // Map Woreda entity to WoredaDTO
    private WoredaDTO mapToDTO(Woreda woreda) {
        WoredaDTO dto = new WoredaDTO();
        dto.setWoredaCode(woreda.getWoredaCode());
        dto.setWoreda(woreda.getWoreda());
        dto.setZoneCode(woreda.getZone().getZoneCode());
        return dto;
    }

    // Map WoredaDTO to Woreda entity
    public Woreda mapToEntity(WoredaDTO dto, Zone zone) {
        Woreda woreda = new Woreda();
        woreda.setWoredaCode(dto.getWoredaCode());
        woreda.setWoreda(dto.getWoreda());
        woreda.setZone(zone);
        return woreda;
    }
}