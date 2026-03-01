package Henok.example.DeutscheCollageBack_endAPI.Service.MOEServices;


import Henok.example.DeutscheCollageBack_endAPI.DTO.MOE_DTOs.ZoneDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Region;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Zone;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.RegionRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.ZoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZoneService {

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private RegionRepository regionRepository;

    // Add multiple zones with validation using DTO
    // Ensures no duplicates and valid region references
    public List<ZoneDTO> addMultipleZones(List<ZoneDTO> zoneDTOs) {
        if (zoneDTOs == null || zoneDTOs.isEmpty()) {
            throw new IllegalArgumentException("Zone list cannot be null or empty");
        }

        List<ZoneDTO> savedZoneDTOs = new ArrayList<>();

        for (ZoneDTO zoneDTO : zoneDTOs) {
            if (zoneDTO.getZoneCode() == null || zoneDTO.getZoneCode().isEmpty()) {
                throw new IllegalArgumentException("Zone code cannot be null or empty");
            }
            if (zoneRepository.existsByZoneCode(zoneDTO.getZoneCode())) {
                throw new DataIntegrityViolationException("Zone with code " + zoneDTO.getZoneCode() + " already exists");
            }

            if (zoneDTO.getRegionCode() == null || zoneDTO.getRegionCode().isEmpty()) {
                throw new IllegalArgumentException("Region code is required for Zone");
            }

            Region region = regionRepository.findByRegionCode(zoneDTO.getRegionCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Region with code " + zoneDTO.getRegionCode() + " not found"));

            Zone zone = mapToEntity(zoneDTO, region);
            Zone savedZone = zoneRepository.save(zone);
            savedZoneDTOs.add(mapToDTO(savedZone));
        }

        return savedZoneDTOs;
    }

    // Find zone by code and return DTO
    // Throws ResourceNotFoundException if not found
    public ZoneDTO findByZoneCode(String zoneCode) {
        if (zoneCode == null || zoneCode.isEmpty()) {
            throw new IllegalArgumentException("Zone code cannot be null or empty");
        }
        Zone zone = zoneRepository.findByZoneCode(zoneCode)
                .orElseThrow(() -> new ResourceNotFoundException("Zone with code " + zoneCode + " not found"));
        return mapToDTO(zone);
    }

    // Retrieve all zones as DTOs
    public List<ZoneDTO> findAll() {
        List<Zone> zones = zoneRepository.findAll();
        return zones.isEmpty() ? Collections.emptyList() : zones.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // Find zones by region code and return DTOs
    // Throws ResourceNotFoundException if none found
    public List<ZoneDTO> findByRegionCode(String regionCode) {
        if (regionCode == null || regionCode.isEmpty()) {
            throw new IllegalArgumentException("Region code cannot be null or empty");
        }
        List<Zone> zones = zoneRepository.findByRegionRegionCode(regionCode);
        if (zones.isEmpty()) {
            throw new ResourceNotFoundException("No zones found for region code: " + regionCode);
        }
        return zones.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void deleteAll() {
        zoneRepository.deleteAll();
    }

    // Map Zone entity to ZoneDTO
    private ZoneDTO mapToDTO(Zone zone) {
        ZoneDTO dto = new ZoneDTO();
        dto.setZoneCode(zone.getZoneCode());
        dto.setZone(zone.getZone());
        dto.setRegionCode(zone.getRegion().getRegionCode());
        return dto;
    }

    // Map ZoneDTO to Zone entity
    public Zone mapToEntity(ZoneDTO dto, Region region) {
        Zone zone = new Zone();
        zone.setZoneCode(dto.getZoneCode());
        zone.setZone(dto.getZone());
        zone.setRegion(region);
        return zone;
    }
}