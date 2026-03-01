package Henok.example.DeutscheCollageBack_endAPI.migration.runner;

import Henok.example.DeutscheCollageBack_endAPI.DTO.GradingSystemDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.MOE_DTOs.WoredaDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.MOE_DTOs.ZoneDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.MarkIntervalDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.GeneralManagerRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.RegistrarRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.*;
import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.*;
import Henok.example.DeutscheCollageBack_endAPI.Service.GeneralManagerService;
import Henok.example.DeutscheCollageBack_endAPI.Service.GradingSystemService;
import Henok.example.DeutscheCollageBack_endAPI.Service.MOEServices.WoredaService;
import Henok.example.DeutscheCollageBack_endAPI.Service.MOEServices.ZoneService;
import Henok.example.DeutscheCollageBack_endAPI.Service.ProgressionSequenceService;
import Henok.example.DeutscheCollageBack_endAPI.Service.RegistrarService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

// DataMigrationRunner
// Purpose: Seeds reference/master data from JSON files on startup
//          - Skips entity if table or file missing
//          - Handles empty files gracefully (no content → skip)
//          - Catches invalid JSON gracefully (logs and skips entity)
//          - Inserts records in the exact order they appear in JSON
//          - Per-record existence check → only inserts missing ones
//          - @Transactional per entity → rollback only on failure within one entity

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DataMigrationRunner implements CommandLineRunner {

    // Repositories
    private final AcademicYearRepo academicYearRepo;
    private final EnrollmentTypeRepository enrollmentTypeRepository;
    private final ImpairmentRepository impairmentRepository;
    private final BatchRepo batchRepo;
    private final ClassYearRepository classYearRepository;
    private final SemesterRepo semesterRepo;
    private final CourseCategoryRepo courseCategoryRepo;
    private final CourseSourceRepo courseSourceRepo;
    private final StudentStatusRepo studentStatusRepo;
    private final SchoolBackgroundRepository schoolBackgroundRepository;
    private final RegionRepository regionRepository;
    private final ZoneRepository zoneRepository;
    private final WoredaRepository woredaRepository;
    private final ProgramLevelRepository programLevelRepository;
    private final ProgramModalityRepository programModalityRepository;
    private final DepartmentRepo departmentRepo;
    private final GradingSystemRepository gradingSystemRepository;

    // Services
    private final GradingSystemService gradingSystemService; // <-- we will use the existing service!
    private final WoredaService woredaService;
    private final ZoneService zoneService;
    private final GeneralManagerService generalManagerService;
    private final RegistrarService registrarService;
    private final ProgressionSequenceService progressionService;


    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
//        mainFunction();
    }

    public void mainFunction(){
        try {
            loadAcademicYears();
            loadEnrollmentTypes();
            loadImpairments();
            loadBatches();
            loadClassYears();
            loadSemesters();
            loadCourseCategories();
            loadCourseSources();
            loadStudentStatuses();
            loadRegions();
            //-----------------------------------------------------------------
//            loadZones();                // zones depend on regions
//            loadWoredas();              // woredas depend on zones
            //-----------------------------------------------------------------
            loadSchoolBackgrounds();
            loadProgramLevels();

            loadProgramModalities();
            loadDepartments();
            loadProgressionSequences();

            System.out.println("----------------- Grading System Seeding ---------------");
            loadGradingSystems();
            System.out.println("----------------- Registrar Seeding --------------------");
            loadRegistrar();
            System.out.println("----------------- General Manager Seeding ----------------");
            loadGeneralManager();

            System.out.println("Reference data migration completed.");
        } catch (Exception e) {
            System.err.println("Unexpected error during reference data migration: " + e.getMessage());
            // Still allow app startup to continue
        }
    }

    // Common pattern used by all load methods
    private <T> void loadEntity(
            String entityName,
            String tableName,
            String filePath,
            JpaRepository<T, ?> repo,
            Predicate<T> existsChecker,
            Function<T, String> keyExtractor,
            Class<T> entityClass) throws IOException {

        if (!tableExists(tableName)) {
            System.out.println("Table " + tableName + " does not exist → skipping " + entityName);
            return;
        }

        InputStream is = getClass().getResourceAsStream(filePath);
        if (is == null) {
            System.out.println("File not found: " + filePath + " → skipping " + entityName);
            return;
        }

        List<T> items;
        try {
            // Stronger way to tell Jackson the exact list element type
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, entityClass);

            items = objectMapper.readValue(is, listType);

        } catch (JsonProcessingException e) {
            System.err.println("Invalid JSON format in " + filePath + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("IO error reading " + filePath + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        // Handle empty / null gracefully
        if (items == null || items.isEmpty()) {
            System.out.println(entityName + ": file is empty or contains no items → nothing to migrate");
            return;
        }

        int inserted = 0;
        int skipped = 0;

        // Still preserves JSON order
        for (T item : items) {
            if (existsChecker.test(item)) {
                skipped++;
                continue;
            }

            repo.save(item);
            inserted++;
        }

        System.out.println(entityName + ": inserted " + inserted + ", skipped " + skipped + " / total " + items.size());
    }

    @Transactional
    private void loadAcademicYears() throws IOException {
        loadEntity(
                "AcademicYear",
                "academic_year",
                "/data/academic_years.json",
                academicYearRepo,
                item -> academicYearRepo.existsByYearCode(item.getYearCode()),
                item -> item.getYearCode(),
                AcademicYear.class
        );
    }

    @Transactional
    private void loadEnrollmentTypes() throws IOException {
        loadEntity(
                "EnrollmentType",
                "enrollment_type",
                "/data/enrollment_types.json",
                enrollmentTypeRepository,
                item -> enrollmentTypeRepository.existsByEnrollmentTypeCode(item.getEnrollmentTypeCode()),
                item -> item.getEnrollmentTypeCode(),
                EnrollmentType.class
        );
    }

    @Transactional
    private void loadImpairments() throws IOException {
        loadEntity(
                "Impairment",
                "impairment",
                "/data/impairments.json",
                impairmentRepository,
                item -> impairmentRepository.existsByImpairmentCode(item.getImpairmentCode()),
                item -> item.getImpairmentCode(),
                Impairment.class
        );
    }

    @Transactional
    private void loadBatches() throws IOException {
        loadEntity(
                "Batch",
                "batch",
                "/data/batches.json",
                batchRepo,
                item -> batchRepo.findByBatchName(item.getBatchName()).isPresent(),
                Batch::getBatchName,
                Batch.class
        );
    }

    @Transactional
    private void loadClassYears() throws IOException {
        loadEntity(
                "ClassYear",
                "class_year",
                "/data/class_years.json",
                classYearRepository,
                item -> classYearRepository.findByClassYear(item.getClassYear()).isPresent(),
                ClassYear::getClassYear,
                ClassYear.class
        );
    }

    @Transactional
    private void loadSemesters() throws IOException {
        loadEntity(
                "Semester",
                "semester",
                "/data/semesters.json",
                semesterRepo,
                item -> semesterRepo.findByAcademicPeriodCode(item.getAcademicPeriodCode()).isPresent(),
                Semester::getAcademicPeriodCode,
                Semester.class
        );
    }

    @Transactional
    private void loadCourseCategories() throws IOException {
        loadEntity(
                "CourseCategory",
                "course_category",
                "/data/course_categories.json",
                courseCategoryRepo,
                item -> courseCategoryRepo.existsByCatNameIgnoreCase(item.getCatName()),
                CourseCategory::getCatName,
                CourseCategory.class
        );
    }

    @Transactional
    private void loadCourseSources() throws IOException {
        loadEntity(
                "CourseSource",
                "course_source",
                "/data/course_sources.json",
                courseSourceRepo,
                item -> courseSourceRepo.existsBySourceNameIgnoreCase(item.getSourceName()),
                CourseSource::getSourceName,
                CourseSource.class
        );
    }

    @Transactional
    private void loadStudentStatuses() throws IOException {
        loadEntity(
                "StudentStatus",
                "status_t",
                "/data/student_statuses.json",
                studentStatusRepo,
                item -> studentStatusRepo.existsByStatusNameIgnoreCase(item.getStatusName()),
                StudentStatus::getStatusName,
                StudentStatus.class
        );
    }

    // ────────────────────────────────────────────────
// SchoolBackground – key: background (unique string)
// ────────────────────────────────────────────────
    @Transactional
    private void loadSchoolBackgrounds() throws IOException {
        loadEntity(
                "SchoolBackground",
                "School_Background_T",
                "/data/school_backgrounds.json",
                schoolBackgroundRepository,
                item -> schoolBackgroundRepository.findByBackground(item.getBackground()).isPresent(),
                SchoolBackground::getBackground,
                SchoolBackground.class
        );
    }

    // ────────────────────────────────────────────────
// Region – key: regionCode (natural/business key)
// ────────────────────────────────────────────────
    @Transactional
    private void loadRegions() throws IOException {
        loadEntity(
                "Region",
                "region",
                "/data/regions.json",
                regionRepository,
                item -> regionRepository.existsByRegionCode(item.getRegionCode()),
                Region::getRegionCode,
                Region.class
        );
    }

    // ────────────────────────────────────────────────
// ProgramLevel – key: code (case-insensitive check)
// ────────────────────────────────────────────────
    @Transactional
    private void loadProgramLevels() throws IOException {
        loadEntity(
                "ProgramLevel",
                "program_level",
                "/data/program_levels.json",
                programLevelRepository,
                item -> programLevelRepository.existsByCodeIgnoreCase(item.getCode()),
                ProgramLevel::getCode,
                ProgramLevel.class
        );
    }

    //------------------- Foreign Key Tables -----------------------------------------------------------
    // Loads ProgramModality entities
    // Each record must reference an existing ProgramLevel by code
    // If the programLevel code does not exist → skip that record and log
    @Transactional
    private void loadProgramModalities() throws IOException {
        String entityName = "ProgramModality";
        String table = "program_modality";
        String path = "/data/program_modalities.json";

        if (!tableExists(table)) {
            System.out.println("Table " + table + " does not exist → skipping " + entityName);
            return;
        }

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            System.out.println("File not found: " + path + " → skipping " + entityName);
            return;
        }

        List<Map<String, Object>> rawItems;
        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Map.class);
            rawItems = objectMapper.readValue(is, listType);
        } catch (JsonProcessingException e) {
            System.err.println("Invalid JSON in " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("IO error reading " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        if (rawItems == null || rawItems.isEmpty()) {
            System.out.println(entityName + ": file is empty or contains no items → nothing to migrate");
            return;
        }

        int inserted = 0;
        int skipped = 0;
        int missingParent = 0;

        for (Map<String, Object> raw : rawItems) {
            try {
                String modalityCode = (String) raw.get("modalityCode");
                String modality = (String) raw.get("modality");
                String programLevelCode = (String) raw.get("programLevelCode");

                if (modalityCode == null || modalityCode.trim().isEmpty()) {
                    System.out.println("Skipping invalid " + entityName + " - missing modalityCode");
                    skipped++;
                    continue;
                }

                if (programModalityRepository.existsByModalityCode(modalityCode)) {
                    skipped++;
                    continue;
                }

                // Resolve parent
                ProgramLevel level = null;
                if (programLevelCode != null && !programLevelCode.trim().isEmpty()) {
                    level = programLevelRepository.findById(programLevelCode).orElse(null);
                    if (level == null) {
                        System.out.println("Skipping " + entityName + " '" + modalityCode + "' - programLevelCode '"
                                + programLevelCode + "' not found");
                        missingParent++;
                        continue;
                    }
                } else {
                    // If program_level_code is optional in your current model (nullable=true)
                    // but @ManyToOne optional=false → this would violate constraint
                    // Adjust based on your real nullability intention
                    System.out.println("Skipping " + entityName + " '" + modalityCode + "' - programLevelCode is required but missing");
                    missingParent++;
                    continue;
                }

                ProgramModality modalityObj = new ProgramModality();
                modalityObj.setModalityCode(modalityCode);
                modalityObj.setModality(modality);
                modalityObj.setProgramLevel(level);

                programModalityRepository.save(modalityObj);
                inserted++;

            } catch (Exception e) {
                System.err.println("Error processing " + entityName + " record: " + e.getMessage());
                skipped++;
            }
        }

        System.out.println(entityName + ": inserted " + inserted + ", skipped " + skipped
                + ", missing parent " + missingParent + " / total " + rawItems.size());
    }

    // Loads Department entities
    // Handles totalCrHr coming as string (possibly with spaces or empty)
    // → trims → converts to Integer if valid → sets null if empty/whitespace
    @Transactional
    private void loadDepartments() throws IOException {
        String entityName = "Department";
        String table = "department";
        String path = "/data/departments.json";

        if (!tableExists(table)) {
            System.out.println("Table " + table + " does not exist → skipping " + entityName);
            return;
        }

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            System.out.println("File not found: " + path + " → skipping " + entityName);
            return;
        }

        List<Map<String, Object>> rawItems;
        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Map.class);
            rawItems = objectMapper.readValue(is, listType);
        } catch (JsonProcessingException e) {
            System.err.println("Invalid JSON in " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("IO error reading " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        if (rawItems == null || rawItems.isEmpty()) {
            System.out.println(entityName + ": file is empty or contains no items → nothing to migrate");
            return;
        }

        int inserted = 0;
        int skipped = 0;
        int missingModality = 0;
        int missingLevel = 0;
        int invalidCrHr = 0;

        for (Map<String, Object> raw : rawItems) {
            try {
                String departmentCode = (String) raw.get("departmentCode");
                String deptName = (String) raw.get("deptName");
                String totalCrHrStr = (String) raw.get("totalCrHr");           // comes as String
                String modalityCode = (String) raw.get("modalityCode");
                String programLevelCode = (String) raw.get("programLevelCode");

                if (departmentCode == null || departmentCode.trim().isEmpty()) {
                    System.out.println("Skipping invalid " + entityName + " - missing or empty departmentCode");
                    skipped++;
                    continue;
                }

                if (departmentRepo.existsByDepartmentCode(departmentCode)) {
                    skipped++;
                    continue;
                }

                // ─────── Handle totalCrHr ───────
                Integer totalCrHr = null;
                if (totalCrHrStr != null) {
                    String trimmed = totalCrHrStr.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            totalCrHr = Integer.parseInt(trimmed);
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid totalCrHr value for department '" + departmentCode
                                    + "': '" + totalCrHrStr + "' → setting to null");
                            invalidCrHr++;
                        }
                    }
                    // else: empty after trim → leave as null (which is allowed)
                }

                // ─────── Resolve ProgramModality (optional) ───────
                ProgramModality modality = null;
                if (modalityCode != null && !modalityCode.trim().isEmpty()) {
                    modality = programModalityRepository.findByModalityCode(modalityCode).orElse(null);
                    if (modality == null) {
                        System.out.println("Missing modality for department '" + departmentCode
                                + "': code '" + modalityCode + "' not found → setting FK to null");
                        missingModality++;
                    }
                }

                // ─────── Resolve ProgramLevel (optional) ───────
                ProgramLevel level = null;
                if (programLevelCode != null && !programLevelCode.trim().isEmpty()) {
                    level = programLevelRepository.findById(programLevelCode).orElse(null);
                    if (level == null) {
                        System.out.println("Missing program level for department '" + departmentCode
                                + "': code '" + programLevelCode + "' not found → setting FK to null");
                        missingLevel++;
                    }
                }

                Department dept = new Department();
                dept.setDepartmentCode(departmentCode);
                dept.setDeptName(deptName);
                dept.setTotalCrHr(totalCrHr);              // now safely Integer or null
                dept.setProgramModality(modality);
                dept.setProgramLevel(level);

                departmentRepo.save(dept);
                inserted++;

            } catch (Exception e) {
                System.err.println("Error processing " + entityName + " record: " + e.getMessage());
                skipped++;
            }
        }

        System.out.println(entityName + ": inserted " + inserted + ", skipped " + skipped
                + ", missing modality " + missingModality
                + ", missing level " + missingLevel
                + ", invalid totalCrHr " + invalidCrHr
                + " / total " + rawItems.size());
    }



    // ────────────────────────────────────────────────
    // loadGradingSystems()
    // Purpose: Seeds one or more GradingSystem + their MarkInterval children
    // Strategy:
    //   - Reads JSON array of full grading systems
    //   - For each system: builds GradingSystemDTO → calls createGradingSystem(dto)
    //   - Uses the existing service → inherits validation, duplicate check, cascade save
    //   - If service throws exception (duplicate name, invalid intervals, missing department, etc.)
    //     → catch, log gracefully, skip that system only (continue with next)
    //   - Preserves order from JSON
    // ────────────────────────────────────────────────
    @Transactional
    private void loadGradingSystems() throws IOException {
        String entityName = "GradingSystem";
        String table = "grading_system";
        String path = "/data/grading_systems.json";

        if (!tableExists(table)) {
            System.out.println("\tTable " + table + " does not exist → skipping " + entityName);
            return;
        }

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            System.out.println("\tFile not found: " + path + " → skipping " + entityName);
            return;
        }

        List<Map<String, Object>> rawSystems;
        try {
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Map.class);
            rawSystems = objectMapper.readValue(is, listType);
        } catch (JsonProcessingException e) {
            System.err.println("\tInvalid JSON format in " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("\tIO error reading " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        if (rawSystems == null || rawSystems.isEmpty()) {
            System.out.println("\t" + entityName + ": file is empty or contains no items → nothing to migrate");
            return;
        }

        int inserted = 0;
        int skipped = 0;
        int failed = 0;

        for (Map<String, Object> raw : rawSystems) {
            try {
                String versionName = (String) raw.get("versionName");
                if (versionName == null || versionName.trim().isEmpty()) {
                    System.out.println("\tSkipping invalid " + entityName + " - missing versionName");
                    skipped++;
                    continue;
                }

                // Quick early duplicate check (service will also check, but we avoid unnecessary work)
                if (gradingSystemRepository.findByVersionName(versionName).isPresent()) {
                    System.out.println("\t" + entityName + " '" + versionName + "' already exists → skipping");
                    skipped++;
                    continue;
                }

                GradingSystemDTO dto = new GradingSystemDTO();
                dto.setVersionName(versionName);
                dto.setRemark((String) raw.get("remark"));
                dto.setActive(Boolean.TRUE.equals(raw.get("isActive"))); // default false, only true if explicitly set

                // Optional department
                String deptCode = (String) raw.get("departmentCode");
                if (deptCode != null && !deptCode.trim().isEmpty()) {
                    Department dept = departmentRepo.findByDepartmentCode(deptCode)
                            .orElse(null);
                    if (dept != null) {
                        dto.setDepartmentId(dept.getDptID());
                    } else {
                        System.out.println("\tDepartment code '" + deptCode + "' not found for "
                                + entityName + " '" + versionName + "' → proceeding without department");
                    }
                }

                // Intervals – required field in practice
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rawIntervals = (List<Map<String, Object>>) raw.get("intervals");
                if (rawIntervals == null || rawIntervals.isEmpty()) {
                    System.out.println("\tSkipping " + entityName + " '" + versionName + "' - no intervals provided");
                    skipped++;
                    continue;
                }

                List<MarkIntervalDTO> intervalDTOs = new ArrayList<>();
                for (Map<String, Object> rawInt : rawIntervals) {
                    MarkIntervalDTO intDto = new MarkIntervalDTO();
                    intDto.setDescription((String) rawInt.get("description"));
                    intDto.setMin(getDouble(rawInt.get("min")));
                    intDto.setMax(getDouble(rawInt.get("max")));
                    intDto.setGivenValue(getDouble(rawInt.get("givenValue")));
                    intDto.setGradeLetter((String) rawInt.get("gradeLetter"));
                    intervalDTOs.add(intDto);
                }
                dto.setIntervals(intervalDTOs);

                // Let the service do the real work (validation, save, cascade)
                gradingSystemService.createGradingSystem(dto);
                inserted++;

                System.out.println("Successfully seeded " + entityName + ": " + versionName);

            } catch (IllegalArgumentException e) {
                // Common: duplicate name, overlapping intervals, etc.
                System.err.println("\tValidation failed for " + entityName + " '"
                        + (raw.get("versionName") != null ? raw.get("versionName") : "unknown")
                        + "': " + e.getMessage() + " → skipping");
                failed++;
            } catch (ResourceNotFoundException e) {
                // e.g. department not found
                System.err.println("\tResource not found while seeding " + entityName + ": " + e.getMessage() + " → skipping");
                failed++;
            } catch (Exception e) {
                // Catch-all for unexpected issues
                System.err.println("\tUnexpected error seeding " + entityName + " record: " + e.getMessage());
                failed++;
            }
        }

        System.out.println("\t" + entityName + ": inserted " + inserted + ", skipped " + skipped
                + ", failed " + failed + " / total " + rawSystems.size());
    }

    // Helper to safely convert Object → double (handles null, string numbers, etc.)
    private double getDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) return 0.0;
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number format: '" + s + "' → using 0.0");
                return 0.0;
            }
        }
        return 0.0;
    }

    // ────────────────────────────────────────────────
    // Load Zones – imports one by one, continues on error
    // ────────────────────────────────────────────────
    @Transactional
    private void loadZones() throws IOException {
        String entityName = "Zone";
        String table = "zone";
        String path = "/data/zones.json";

        if (!tableExists(table)) {
            System.out.println("\tTable " + table + " does not exist → skipping " + entityName);
            return;
        }

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            System.out.println("\tFile not found: " + path + " → skipping " + entityName);
            return;
        }

        List<ZoneDTO> zoneDTOs;
        try {
            zoneDTOs = objectMapper.readValue(is, new TypeReference<List<ZoneDTO>>() {});
        } catch (JsonProcessingException e) {
            System.err.println("\tInvalid JSON format in " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("\tIO error reading " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        if (zoneDTOs == null || zoneDTOs.isEmpty()) {
            System.out.println("\t" + entityName + ": file is empty or contains no items → nothing to migrate");
            return;
        }

        int total = zoneDTOs.size();
        int success = 0;
        int skipped = 0;
        int failed = 0;

        for (ZoneDTO dto : zoneDTOs) {
            String identifier = dto.getZoneCode() != null ? dto.getZoneCode() : "unknown";

            try {
                // Quick pre-check (optional – service will also check)
                if (dto.getZoneCode() == null || dto.getZoneCode().trim().isEmpty()) {
                    System.out.println("\tSkipping " + entityName + " – missing zoneCode");
                    skipped++;
                    continue;
                }

                if (zoneRepository.existsByZoneCode(dto.getZoneCode())) {
                    System.out.println("\tSkipping " + entityName + " '" + identifier + "' – already exists");
                    skipped++;
                    continue;
                }

                if (dto.getRegionCode() == null || dto.getRegionCode().trim().isEmpty()) {
                    System.out.println("\tSkipping " + entityName + " '" + identifier + "' – missing regionCode");
                    skipped++;
                    continue;
                }

                Region region = regionRepository.findByRegionCode(dto.getRegionCode())
                        .orElse(null);

                if (region == null) {
                    System.out.println("\tSkipping " + entityName + " '" + identifier + "' – region code '"
                            + dto.getRegionCode() + "' not found");
                    failed++;
                    continue;
                }

                // Reuse your service logic (single record)
                Zone zone = zoneService.mapToEntity(dto, region);   // assuming mapToEntity is public or accessible
                zoneRepository.save(zone);
                success++;

                // Optional: log success for traceability (can be removed later)
                // System.out.println("\tImported " + entityName + " '" + identifier + "'");

            } catch (Exception e) {
                System.err.println("\tFailed to import " + entityName + " '" + identifier + "': " + e.getMessage());
                failed++;
            }
        }

        System.out.println("\t" + entityName + ": success " + success + ", skipped " + skipped
                + ", failed " + failed + " / total " + total);
    }

    // ────────────────────────────────────────────────
    // Load Woredas – imports one by one, continues on error
    // ────────────────────────────────────────────────
    @Transactional
    private void loadWoredas() throws IOException {
        String entityName = "Woreda";
        String table = "woreda";
        String path = "/data/woredas.json";

        if (!tableExists(table)) {
            System.out.println("\tTable " + table + " does not exist → skipping " + entityName);
            return;
        }

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            System.out.println("\tFile not found: " + path + " → skipping " + entityName);
            return;
        }

        List<WoredaDTO> woredaDTOs;
        try {
            woredaDTOs = objectMapper.readValue(is, new TypeReference<List<WoredaDTO>>() {});
        } catch (JsonProcessingException e) {
            System.err.println("\tInvalid JSON format in " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("\tIO error reading " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        if (woredaDTOs == null || woredaDTOs.isEmpty()) {
            System.out.println("\t" + entityName + ": file is empty or contains no items → nothing to migrate");
            return;
        }

        int total = woredaDTOs.size();
        int success = 0;
        int skipped = 0;
        int failed = 0;

        for (WoredaDTO dto : woredaDTOs) {
            String identifier = dto.getWoredaCode() != null ? dto.getWoredaCode() : "unknown";

            try {
                // Quick pre-checks
                if (dto.getWoredaCode() == null || dto.getWoredaCode().trim().isEmpty()) {
                    System.out.println("\tSkipping " + entityName + " – missing woredaCode");
                    skipped++;
                    continue;
                }

                if (woredaRepository.existsByWoredaCode(dto.getWoredaCode())) {
                    System.out.println("\tSkipping " + entityName + " '" + identifier + "' – already exists");
                    skipped++;
                    continue;
                }

                if (dto.getZoneCode() == null || dto.getZoneCode().trim().isEmpty()) {
                    System.out.println("\tSkipping " + entityName + " '" + identifier + "' – missing zoneCode");
                    skipped++;
                    continue;
                }

                Zone zone = zoneRepository.findByZoneCode(dto.getZoneCode())
                        .orElse(null);

                if (zone == null) {
                    System.out.println("\tSkipping " + entityName + " '" + identifier + "' – zone code '"
                            + dto.getZoneCode() + "' not found");
                    failed++;
                    continue;
                }

                // Reuse your service logic (single record)
                Woreda woreda = woredaService.mapToEntity(dto, zone);   // assuming mapToEntity is accessible
                woredaRepository.save(woreda);
                success++;

            } catch (Exception e) {
                System.err.println("\tFailed to import " + entityName + " '" + identifier + "': " + e.getMessage());
                failed++;
            }
        }

        System.out.println("\t" + entityName + ": success " + success + ", skipped " + skipped
                + ", failed " + failed + " / total " + total);
    }

    // ────────────────────────────────────────────────
    // Load Progression Sequences – bulk migration using existing service
    // Order: should run AFTER class years, semesters, departments are seeded
    // ────────────────────────────────────────────────
    @Transactional
    private void loadProgressionSequences() throws IOException {
        String entityName = "ProgressionSequence";
        String table = "progression_sequence"; // adjust if your table name differs
        String path = "/data/progression_sequences.json";

        if (!tableExists(table)) {
            System.out.println("\tTable " + table + " does not exist → skipping " + entityName);
            return;
        }

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            System.out.println("\tFile not found: " + path + " → skipping " + entityName);
            return;
        }

        List<Map<String, Object>> requests;
        try {
            requests = objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            System.err.println("\tInvalid JSON format in " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("\tIO error reading " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        if (requests == null || requests.isEmpty()) {
            System.out.println("\t" + entityName + ": file is empty or contains no items → nothing to migrate");
            return;
        }

        int total = requests.size();

        try {
            // Call your existing bulk service
            Map<String, Object> result = progressionService.createBulk(requests);

            int requested = (int) result.getOrDefault("totalRequested", 0);
            int failed = (int) result.getOrDefault("totalFailed", 0);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.getOrDefault("results", Collections.emptyList());

            int success = requested - failed;

            System.out.println("\t" + entityName + ": success " + success + ", failed " + failed + " / total " + requested);

            // Log individual failures (very helpful for debugging large JSON)
            if (!errors.isEmpty()) {
                System.out.println("\tFailed records:");
                for (Map<String, Object> err : errors) {
                    String input = (String) err.get("input");
                    String reason = (String) err.get("reason");
                    System.out.println("\t\t- Input: " + input + " → Reason: " + reason);
                }
            }

        } catch (BadRequestException e) {
            System.err.println("\tBad request when seeding " + entityName + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\tUnexpected error while seeding " + entityName + ": " + e.getMessage());
        }
    }

    //================================ Users ======================================

    // Seed Registrar – one record from JSON
    // Uses existing registrar registration service
    // Multipart files are set to null (no images during seeding)
    // ────────────────────────────────────────────────
    @Transactional
    private void loadRegistrar() throws IOException {
        String entityName = "Registrar";
        String path = "/data/registrar.json";

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            System.out.println("\tFile not found: " + path + " → skipping " + entityName);
            return;
        }

        RegistrarRegisterRequest request;
        try {
            request = objectMapper.readValue(is, RegistrarRegisterRequest.class);
        } catch (JsonProcessingException e) {
            System.err.println("\tInvalid JSON format in " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("\tIO error reading " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        if (request == null) {
            System.out.println("\t" + entityName + ": file is empty or invalid → nothing to migrate");
            return;
        }

        String identifier = request.getUsername() != null ? request.getUsername() : "unknown";

        try {
            // Call service with null images (as per your requirement for seeding)
            MultipartFile nullNationalId = null;
            MultipartFile nullPhoto = null;

            registrarService.registerRegistrar(request, nullNationalId, nullPhoto);

            System.out.println("\tSuccessfully seeded " + entityName + ": " + identifier);

        } catch (IllegalArgumentException e) {
            System.err.println("\tValidation failed for " + entityName + " '" + identifier + "': " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\tFailed to seed " + entityName + " '" + identifier + "': " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // Seed General Manager – one record from JSON
    // Uses existing general manager registration service
    // Multipart files are set to null (no images during seeding)
    // ────────────────────────────────────────────────
    @Transactional
    private void loadGeneralManager() throws IOException {
        String entityName = "GeneralManager";
        String path = "/data/general_manager.json";

        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            System.out.println("\tFile not found: " + path + " → skipping " + entityName);
            return;
        }

        GeneralManagerRegisterRequest request;
        try {
            request = objectMapper.readValue(is, GeneralManagerRegisterRequest.class);
        } catch (JsonProcessingException e) {
            System.err.println("\tInvalid JSON format in " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("\tIO error reading " + path + " → skipping " + entityName + ": " + e.getMessage());
            return;
        }

        if (request == null) {
            System.out.println("\t" + entityName + ": file is empty or invalid → nothing to migrate");
            return;
        }

        String identifier = request.getUsername() != null ? request.getUsername() : "unknown";

        try {
            // Call service with null images
            MultipartFile nullNationalId = null;
            MultipartFile nullPhoto = null;

            generalManagerService.registerGeneralManager(request, nullNationalId, nullPhoto);

            System.out.println("\tSuccessfully seeded " + entityName + ": " + identifier);

        } catch (IllegalArgumentException e) {
            System.err.println("\tValidation failed for " + entityName + " '" + identifier + "': " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\tFailed to seed " + entityName + " '" + identifier + "': " + e.getMessage());
        }
    }



    //----------------------================================================================----------------------
    private boolean tableExists(String tableName) {
        try {
            jdbcTemplate.execute("DESCRIBE " + tableName);
            return true;
        } catch (DataAccessException e) {
            // Usually "Table doesn't exist" or similar
            return false;
        }
    }
}