package Henok.example.DeutscheCollageBack_endAPI.Controller;

import Henok.example.DeutscheCollageBack_endAPI.Enums.*;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/filters")
public class FilterDataController {

    @Autowired private DepartmentRepo departmentRepository;
    @Autowired private BatchRepo batchRepository;
    @Autowired private ClassYearRepository classYearRepository;
    @Autowired private SemesterRepo semesterRepository;
    @Autowired private ProgramModalityRepository programModalityRepository;
    @Autowired private ProgramLevelRepository programLevelRepository;
    @Autowired private EnrollmentTypeRepository enrollmentTypeRepository;
    @Autowired private AcademicYearRepo academicYearRepository;
    @Autowired private ImpairmentRepository impairmentRepository;
    @Autowired private CourseCategoryRepo courseCategoryRepository;
    @Autowired private CourseSourceRepo courseSourceRepository;
    @Autowired private StudentStatusRepo studentStatusRepository;
    @Autowired private BatchClassYearSemesterRepo batchClassYearSemesterRepository;
    @Autowired private SchoolBackgroundRepository schoolBackgroundRepository;


    /**
     * GET /api/responses/all
     * Returns a comprehensive list of all academic entities with simplified response format
     * Format: Each entity type contains only essential fields (id, name, and relevant foreign key IDs)
     * Why no service layer: Simple data retrieval without business logic - direct repository access
     * Role: Accessible to all authenticated users (students, teachers, admins)
     */
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> getAllResponses() {
        Map<String, Object> response = new HashMap<>();

        // Department responses: id, name, programLevel code, programModality code
        List<Map<String, Object>> departments = departmentRepository.findAll().stream()
                .map(dept -> {
                    Map<String, Object> deptMap = new HashMap<>();
                    deptMap.put("id", dept.getDptID());
                    deptMap.put("name", dept.getDeptName());
                    deptMap.put("programLevelId", dept.getProgramLevel() != null ? dept.getProgramLevel().getCode() : null);
                    deptMap.put("programModalityId", dept.getProgramModality() != null ? dept.getProgramModality().getModalityCode() : null);
                    return deptMap;
                })
                .collect(Collectors.toList());
        response.put("departments", departments);

        // Batch responses: id, name
        List<Map<String, Object>> batches = batchRepository.findAll().stream()
                .map(batch -> {
                    Map<String, Object> batchMap = new HashMap<>();
                    batchMap.put("id", batch.getId());
                    batchMap.put("name", batch.getBatchName());
                    return batchMap;
                })
                .collect(Collectors.toList());
        response.put("batches", batches);

        // ClassYear responses: id, name
        List<Map<String, Object>> classYears = classYearRepository.findAll().stream()
                .map(cy -> {
                    Map<String, Object> cyMap = new HashMap<>();
                    cyMap.put("id", cy.getId());
                    cyMap.put("name", cy.getClassYear());
                    return cyMap;
                })
                .collect(Collectors.toList());
        response.put("classYears", classYears);

        // Semester responses: code (id), name
        List<Map<String, Object>> semesters = semesterRepository.findAll().stream()
                .map(sem -> {
                    Map<String, Object> semMap = new HashMap<>();
                    semMap.put("id", sem.getAcademicPeriodCode());
                    semMap.put("name", sem.getAcademicPeriod());
                    return semMap;
                })
                .collect(Collectors.toList());
        response.put("semesters", semesters);

        // ProgramModality responses: id (code), name, programLevelId
        List<Map<String, Object>> modalities = programModalityRepository.findAll().stream()
                .map(mod -> {
                    Map<String, Object> modMap = new HashMap<>();
                    modMap.put("id", mod.getModalityCode());
                    modMap.put("name", mod.getModality());
                    modMap.put("programLevelId", mod.getProgramLevel() != null ? mod.getProgramLevel().getCode() : null);
                    return modMap;
                })
                .collect(Collectors.toList());
        response.put("programModalities", modalities);

        // ProgramLevel responses: id (code), name
        List<Map<String, Object>> programLevels = programLevelRepository.findAll().stream()
                .map(pl -> {
                    Map<String, Object> plMap = new HashMap<>();
                    plMap.put("id", pl.getCode());
                    plMap.put("name", pl.getName());
                    return plMap;
                })
                .collect(Collectors.toList());
        response.put("programLevels", programLevels);

        // New: EnrollmentType responses: id (code), name
        List<Map<String, Object>> enrollmentTypes = enrollmentTypeRepository.findAll().stream()
                .map(et -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", et.getEnrollmentTypeCode());
                    map.put("name", et.getEnrollmentTypeName());
                    return map;
                })
                .collect(Collectors.toList());
        response.put("enrollmentTypes", enrollmentTypes);

        // AcademicYears: id (code), name (academicYearGC only)
        List<Map<String, Object>> academicYears = academicYearRepository.findAll().stream()
                .map(ay -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", ay.getYearCode());
                    map.put("name", ay.getAcademicYearGC());
                    map.put("name_EC", ay.getAcademicYearEC());
                    return map;
                })
                .collect(Collectors.toList());
        response.put("academicYears", academicYears);

        // Impairments: id, name
        List<Map<String, Object>> impairments = impairmentRepository.findAll().stream()
                .map(imp -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", imp.getImpairmentCode());
                    map.put("name", imp.getImpairment());
                    return map;
                })
                .collect(Collectors.toList());
        response.put("impairments", impairments);

        // CourseCategories: id, name
        List<Map<String, Object>> courseCategories = courseCategoryRepository.findAll().stream()
                .map(cat -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", cat.getCatID());
                    map.put("name", cat.getCatName());
                    return map;
                })
                .collect(Collectors.toList());
        response.put("courseCategories", courseCategories);

        // CourseSources: id, name
        List<Map<String, Object>> courseSources = courseSourceRepository.findAll().stream()
                .map(source -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", source.getSourceID());
                    map.put("name", source.getSourceName());
                    return map;
                })
                .collect(Collectors.toList());
        response.put("courseSources", courseSources);

        // StudentStatuses: id, name
        List<Map<String, Object>> studentStatuses = studentStatusRepository.findAll().stream()
                .map(status -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", status.getId());
                    map.put("name", status.getStatusName());
                    return map;
                })
                .collect(Collectors.toList());
        response.put("studentStatuses", studentStatuses);

        // === Batch + ClassYear + Semester (BCYS) - NEWLY ADDED ===
        List<Map<String, Object>> bcysList = batchClassYearSemesterRepository.findAll().stream()
                .map(bcys -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", bcys.getBcysID());
                    map.put("name", bcys.getDisplayName()); // Uses your existing getDisplayName() method
                    return map;
                })
                .collect(Collectors.toList());
        response.put("batchClassYearSemesters", bcysList);

        // SchoolBackgrounds: id, name
        List<Map<String, Object>> schoolBackgrounds = schoolBackgroundRepository.findAll().stream()
                .map(sb -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", sb.getId());
                    map.put("name", sb.getBackground());
                    return map;
                })
                .collect(Collectors.toList());
        response.put("schoolBackgrounds", schoolBackgrounds);


        // === Roles ===
        List<Map<String, Object>> roles = enumToOptions(Role.class);
        response.put("roles", roles);

        // === Gender ===
        List<Map<String, Object>> genders = enumToOptions(Gender.class);
        response.put("genders", genders);

        // === Marital Status ===
        List<Map<String, Object>> maritalStatuses = enumToOptions(MaritalStatus.class);
        response.put("maritalStatuses", maritalStatuses);

        // === Document Status ===
        List<Map<String, Object>> documentStatuses = enumToOptions(DocumentStatus.class);
        response.put("documentStatuses", documentStatuses);

        // === Exit Exam Pass Status ===
        List<Map<String, Object>> exitExamStatuses = enumToOptions(ExitExamPassStatus.class);
        response.put("exitExamPassStatuses", exitExamStatuses);

        // === Assessment Status (for assessments / reviews) ===
        List<Map<String, Object>> assessmentStatuses = enumToOptions(AssessmentStatus.class);
        response.put("assessmentStatuses", assessmentStatuses);

        // === Application Status (for student applications) ===
        List<Map<String, Object>> applicationStatuses = enumToOptions(ApplicationStatus.class);
        response.put("applicationStatuses", applicationStatuses);

        return ResponseEntity.ok(response);

    }

    private List<Map<String, Object>> enumToOptions(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", e.name());
                    map.put("name", e.name());
                    return map;
                })
                .collect(Collectors.toList());
    }
}
