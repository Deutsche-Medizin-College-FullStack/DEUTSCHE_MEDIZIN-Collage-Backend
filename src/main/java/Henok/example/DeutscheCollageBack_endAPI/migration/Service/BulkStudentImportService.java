package Henok.example.DeutscheCollageBack_endAPI.migration.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.RegistrationAndLogin.UserRegisterRequest;
import Henok.example.DeutscheCollageBack_endAPI.Entity.Batch;
import Henok.example.DeutscheCollageBack_endAPI.Entity.BatchClassYearSemester;
import Henok.example.DeutscheCollageBack_endAPI.Entity.StudentDetails;
import Henok.example.DeutscheCollageBack_endAPI.Entity.User;
import Henok.example.DeutscheCollageBack_endAPI.Enums.DocumentStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.ExitExamPassStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import Henok.example.DeutscheCollageBack_endAPI.Enums.MaritalStatus;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Role;
import Henok.example.DeutscheCollageBack_endAPI.Error.BadRequestException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.ProgramModalityRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.RegionRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.WoredaRepository;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.ZoneRepository;
import Henok.example.DeutscheCollageBack_endAPI.Service.UserService;
import Henok.example.DeutscheCollageBack_endAPI.migration.DTO.BulkImportStudentResult;
import Henok.example.DeutscheCollageBack_endAPI.migration.DTO.StudentImportDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


// Bulk Student Import Service
@Service
@RequiredArgsConstructor
public class BulkStudentImportService {

    private static final Logger log = LoggerFactory.getLogger(BulkStudentImportService.class);
    private static final String DEFAULT_PASSWORD = "stud1234";
    private static final Long PLACEHOLDER_BATCH_ID = 1L;

    private final UserService userService;
    private final UserRepository userRepository;
    private final StudentDetailsRepository studentDetailsRepository;
    private final DepartmentRepo departmentRepository;
    private final BatchClassYearSemesterRepo batchRepository;
    private final BatchRepo studentBatchRepository;
    private final StudentStatusRepo studentStatusRepository;
    private final SchoolBackgroundRepository schoolBackgroundRepository;
    private final ProgramModalityRepository programModalityRepository;
    private final RegionRepository regionRepository;
    private final ZoneRepository zoneRepository;
    private final WoredaRepository woredaRepository;

    private BatchClassYearSemester placeholderBatchCache = null;

    public BulkImportStudentResult importStudents(List<StudentImportDTO> dtos) {
        int success = 0;
        int failed = 0;
        List<String> failedUsernames = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            StudentImportDTO dto = dtos.get(i);
            try {
                System.out.println("=======[" + (dto.getUsername() != null ? dto.getUsername() : "UNKNOWN_USERNAME (row " + (i + 1) + ")") + "]==========");
                System.out.println("First Name (ENG/AMH): " + dto.getFirstNameENG() + " / " + dto.getFirstNameAMH());
                System.out.println("Father Name (ENG/AMH): " + dto.getFatherNameENG() + " / " + dto.getFatherNameAMH());
                System.out.println("Grandfather Name (ENG/AMH): " + dto.getGrandfatherNameENG() + " / " + dto.getGrandfatherNameAMH());
                System.out.println("Gender: " + dto.getGender());
                System.out.println("Marital Status: " + dto.getMaritalStatus());
                System.out.println("Phone: " + dto.getPhoneNumber());
                System.out.println("Date of Birth (GC): " + dto.getDateOfBirthGC());
                System.out.println("Date Enrolled (GC): " + dto.getDateEnrolledGC());
                System.out.println("Department ID: " + dto.getDepartmentEnrolledId());
                System.out.println("Batch ID: " + dto.getBatchId());
                System.out.println("Student Status ID: " + dto.getStudentRecentStatusId());
                System.out.println("School Background ID: " + dto.getSchoolBackgroundId());
                System.out.println("Batch Class Year Semester ID: " + dto.getBatchClassYearSemesterId());
                System.out.println("Program Modality Code: " + dto.getProgramModalityCode());
                System.out.println("Place of Birth - Region/Zone/Woreda Codes: " +
                        dto.getPlaceOfBirthRegionCode() + " / " +
                        dto.getPlaceOfBirthZoneCode() + " / " +
                        dto.getPlaceOfBirthWoredaCode());
                System.out.println("Is Transfer: " + dto.getIsTransfer());
                System.out.println("Document Status: " + dto.getDocumentStatus());
                System.out.println("Remark: " + dto.getRemark());

                importSingleStudent(dto);
                success++;
                System.out.println("Successfully imported student: " + dto.getUsername());
                System.out.println("========================================================");

            } catch (Exception e) {
                String username = dto.getUsername() != null ? dto.getUsername() : "UNKNOWN (row " + (i + 1) + ")";
                System.out.println("FAILED to import student: " + username);
                System.out.println("Error message: " + e.getMessage());
                // Print full stack trace for debugging
                e.printStackTrace();

                failed++;
                failedUsernames.add(username);
            }
            System.out.println("========================================================");
        }

        System.out.println("Bulk import completed: " + success + " successful, " + failed + " failed");
        return new BulkImportStudentResult(success, failed, failedUsernames);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importSingleStudent(StudentImportDTO dto) {
        if (isBlank(dto.getUsername())) {
            throw new BadRequestException("Username is required");
        }

        // Check if User with this username already exists
        Optional<User> existingUserOpt = userRepository.findByUsername(dto.getUsername());

        User user;
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            System.out.println("User already exists for username: " + dto.getUsername() + " (ID: " + user.getId() + "). Skipping user creation.");

            // Check if StudentDetails already exists for this user
            if (studentDetailsRepository.existsByUserId(user.getId())) {
                System.out.println("StudentDetails already exists for user ID " + user.getId() + ". Skipping entire record.");
                return; // Skip completely – nothing to do
            }
            // If we reach here: User exists, but no StudentDetails → proceed to create details only
        } else {
            // User does not exist → create new one
            UserRegisterRequest userRequest = new UserRegisterRequest();
            userRequest.setUsername(dto.getUsername());
            userRequest.setPassword(DEFAULT_PASSWORD);
            userRequest.setRole(Role.STUDENT);

            user = userService.registerUser(userRequest);
            System.out.println("Created new User for username: " + dto.getUsername() + " (ID: " + user.getId() + ")");
        }

        // Create StudentDetails
        StudentDetails details = new StudentDetails();
        details.setUser(user);

        // === Field mappings (unchanged) ===
        details.setFirstNameENG(dto.getFirstNameENG());
        details.setFatherNameENG(dto.getFatherNameENG());
        details.setGrandfatherNameENG(dto.getGrandfatherNameENG());
        details.setFirstNameAMH(dto.getFirstNameAMH());
        details.setFatherNameAMH(dto.getFatherNameAMH());
        details.setGrandfatherNameAMH(dto.getGrandfatherNameAMH());

        details.setGender(safeEnumValue(dto.getGender(), Gender.class));
        details.setMaritalStatus(
                isBlank(dto.getMaritalStatus())
                        ? MaritalStatus.SINGLE
                        : safeEnumValue(dto.getMaritalStatus(), MaritalStatus.class)
        );
        details.setPhoneNumber(dto.getPhoneNumber());

        if (isNotBlank(dto.getDateOfBirthGC())) {
            LocalDate birth = LocalDate.parse(dto.getDateOfBirthGC());
            details.setDateOfBirthGC(birth);
            details.setAge(Period.between(birth, LocalDate.now()).getYears());
        }

        if (isNotBlank(dto.getDateEnrolledGC())) {
            details.setDateEnrolledGC(LocalDate.parse(dto.getDateEnrolledGC()));
        }

        details.setDepartmentEnrolled(fetchById(departmentRepository, dto.getDepartmentEnrolledId(), "Department"));
        details.setStudentRecentStatus(fetchById(studentStatusRepository, dto.getStudentRecentStatusId(), "StudentStatus"));
        details.setSchoolBackground(fetchById(schoolBackgroundRepository, dto.getSchoolBackgroundId(), "SchoolBackground"));

        // Batch – use placeholder if empty
        if (isNotBlank(dto.getBatchClassYearSemesterId())) {
            details.setBatchClassYearSemester(fetchById(batchRepository, dto.getBatchClassYearSemesterId(), "BatchClassYearSemester"));
        } else {
            // Lazy-load placeholder only when needed (after migration has run)
            if (placeholderBatchCache == null) {
                System.out.println(" ----->> Lazy-loading placeholder BatchClassYearSemester with ID " + PLACEHOLDER_BATCH_ID);
                placeholderBatchCache = batchRepository.findById(PLACEHOLDER_BATCH_ID)
                        .orElseThrow(() -> new IllegalStateException(
                                "Placeholder BatchClassYearSemester ID " + PLACEHOLDER_BATCH_ID + " not found. " +
                                        "Make sure DataMigrationRunner has seeded it before running bulk import."));
            }
            details.setBatchClassYearSemester(placeholderBatchCache);
        }

        if (isNotBlank(dto.getBatchId())) {
            details.setBatch(fetchById(studentBatchRepository, dto.getBatchId(), "Batch"));
        } else {
            BatchClassYearSemester bcys = details.getBatchClassYearSemester();
            Batch derivedBatch = (bcys != null) ? bcys.getBatch() : null;
            details.setBatch(derivedBatch);
        }

        details.setProgramModality(isNotBlank(dto.getProgramModalityCode())
                ? programModalityRepository.findByModalityCode(dto.getProgramModalityCode()).orElse(null)
                : null);

        details.setPlaceOfBirthRegion(regionRepository.findByRegionCode(dto.getPlaceOfBirthRegionCode()).orElse(null));
        details.setPlaceOfBirthZone(zoneRepository.findByZoneCode(dto.getPlaceOfBirthZoneCode()).orElse(null));
        details.setPlaceOfBirthWoreda(woredaRepository.findByWoredaCode(dto.getPlaceOfBirthWoredaCode()).orElse(null));

        // Legacy full-name field is stored in contactPersonFirstNameENG by requirement.
        details.setContactPersonFirstNameENG(dto.getContactPersonFullNameENG());
        details.setContactPersonPhoneNumber(dto.getContactPersonPhoneNumber());
        details.setContactPersonRelation(dto.getContactPersonRelation());
        details.setTransfer("TRUE".equalsIgnoreCase(dto.getIsTransfer()));
        details.setDocumentStatus("TRUE".equalsIgnoreCase(dto.getDocumentStatus()) ? DocumentStatus.COMPLETE : DocumentStatus.INCOMPLETE);
        details.setStudentPassExitExam(parseExitExamPassStatus(dto.getIsStudentPassExitExam()));
        details.setRemark(dto.getRemark());

        details.setYearOfExamG12(dto.getYear_of_Exam_G12());
        details.setNationalexamIdG12(dto.getNATIONALEXAM_ID_G12());
        details.setEntryYearGC(dto.getEntry_Year_GC());
        details.setEntryYearEC(dto.getEntry_Year_EC());

        if (isNotBlank(dto.getDate_Class_EndGC())) {
            details.setDateClassEndGC(LocalDate.parse(dto.getDate_Class_EndGC()));
        }
        if (isNotBlank(dto.getDate_Graduated())) {
            details.setDateGraduated(LocalDate.parse(dto.getDate_Graduated()));
        }

        if (isNotBlank(dto.getExitExamScore())) {
            details.setExitExamScore(Double.valueOf(dto.getExitExamScore()));
        }
        if (isNotBlank(dto.getGrade12Result())) {
            details.setGrade12Result(Double.valueOf(dto.getGrade12Result()));
        }
        details.setExitExamUserID(dto.getExitExamUserID());

        studentDetailsRepository.save(details);
        System.out.println("StudentDetails created/updated for username: " + dto.getUsername());
    }

    // Helper methods remain the same
    private <T> T fetchById(JpaRepository<T, Long> repo, String idStr, String entityName) {
        if (isBlank(idStr)) return null;
        try {
            Long id = Long.parseLong(idStr.trim());
            return repo.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            log.warn("Invalid {} ID: {}", entityName, idStr);
            return null;
        }
    }

    private <T extends Enum<T>> T safeEnumValue(String value, Class<T> enumClass) {
        if (isBlank(value)) return null;
        try {
            String normalized = value.trim().toUpperCase().replace(" ", "_").replace("-", "_");
            return Enum.valueOf(enumClass, normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid {} value: {} (stored as null)", enumClass.getSimpleName(), value);
            return null;
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    private ExitExamPassStatus parseExitExamPassStatus(String value) {
        if (isBlank(value)) {
            return ExitExamPassStatus.Not_Taken;
        }

        String normalized = value.trim().toUpperCase().replace("-", " ").replace("_", " ");
        if ("YES".equals(normalized) || "TRUE".equals(normalized) || "PASS".equals(normalized)) {
            return ExitExamPassStatus.Yes;
        }
        if ("NO".equals(normalized) || "FALSE".equals(normalized) || "FAIL".equals(normalized)) {
            return ExitExamPassStatus.No;
        }
        if ("NOT TAKEN".equals(normalized) || "NOTTAKEN".equals(normalized)) {
            return ExitExamPassStatus.Not_Taken;
        }

        log.warn("Invalid isStudentPassExitExam value: {} (defaulting to Not_Taken)", value);
        return ExitExamPassStatus.Not_Taken;
    }
}
