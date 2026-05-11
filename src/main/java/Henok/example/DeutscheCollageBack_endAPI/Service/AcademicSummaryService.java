package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryBulkResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.Reports.AcademicSummaryResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.AcademicYear;
import Henok.example.DeutscheCollageBack_endAPI.Error.ResourceNotFoundException;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AcademicSummaryService {

    @Autowired
    private StudentCourseScoreRepo studentCourseScoreRepo;

    @Autowired
    private StudentDetailsRepository studentDetailsRepository;

    @Autowired
    private BatchClassYearSemesterRepo batchClassYearSemesterRepo;

    @Autowired
    private DepartmentBCYSRepository departmentBCYSRepository;

    @Autowired
    private DepartmentRepo departmentRepository;

    @Autowired
    private StudentCopyService studentCopyService;

    public AcademicSummaryBulkResponseDTO generateAcademicSummaries(List<AcademicSummaryRequestDTO> requests) {
        List<AcademicSummaryBulkResponseDTO.Item> results = new ArrayList<>();

        if (requests == null || requests.isEmpty()) {
            return new AcademicSummaryBulkResponseDTO(results);
        }

        for (AcademicSummaryRequestDTO request : requests) {
            try {
                AcademicSummaryResponseDTO summary = generateAcademicSummary(request);
                results.add(new AcademicSummaryBulkResponseDTO.Item(request, summary, true, null));
            } catch (Exception e) {
                System.out.println("Skipping academic summary request departmentId=" +
                        (request != null ? request.getDepartmentId() : null) +
                        ", bcysId=" + (request != null ? request.getBcysId() : null) +
                        " due to: " + e.getMessage());
                results.add(new AcademicSummaryBulkResponseDTO.Item(request, null, false, e.getMessage()));
            }
        }

        return new AcademicSummaryBulkResponseDTO(results);
    }

    /**
     * Generates an academic summary report for all students in a given department and BCYS.
     * 
     * @param request Contains departmentId and bcysId
     * @return AcademicSummaryResponseDTO with header info and all students' academic data
     * @throws ResourceNotFoundException if department, BCYS, or DepartmentBCYS not found
     */
    public AcademicSummaryResponseDTO generateAcademicSummary(AcademicSummaryRequestDTO request) {
        // 1. Validate and fetch department, BCYS
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id: " + request.getDepartmentId()
                ));

        BatchClassYearSemester bcys = batchClassYearSemesterRepo.findById(request.getBcysId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BatchClassYearSemester not found with id: " + request.getBcysId()
                ));
        System.out.println("Generating academic summary for Department: " + department.getDeptName() + ", BCYS: " + bcys.getDisplayName());

        // 2. Get DepartmentBCYS (contains displayName and academicYear)
        DepartmentBCYS deptBCYS = departmentBCYSRepository.findByBcysAndDepartment(bcys, department)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No cohort record found for department " + department.getDeptName() +
                        " and BCYS " + bcys.getDisplayName()
                ));
System.out.println("Found DepartmentBCYS");
        // 3. Get all distinct students with released scores in this department + BCYS
        List<StudentCourseScore> allScoresInBCYS = studentCourseScoreRepo
                .findByBatchClassYearSemesterAndIsReleasedTrue(bcys);

        // Filter to only students in this department
        Set<User> uniqueStudents = new HashSet<>();
        for (StudentCourseScore score : allScoresInBCYS) {
            StudentDetails studentDetails = studentDetailsRepository.findByUser(score.getStudent())
                    .orElse(null);
            if (studentDetails != null && 
                studentDetails.getDepartmentEnrolled().getDptID().equals(department.getDptID())) {
                uniqueStudents.add(score.getStudent());
            }
        }
System.out.println("Found " + uniqueStudents.size() + " unique students with released scores in this department and BCYS");

        // 4. Build header info
        AcademicSummaryResponseDTO.HeaderInfo headerInfo = buildHeaderInfo(deptBCYS, bcys);

        // 5. Generate student summaries
        List<AcademicSummaryResponseDTO.StudentSummary> studentSummaries = new ArrayList<>();
        System.out.println("Generating summaries for " + uniqueStudents.size() + " students");
        for (User student : uniqueStudents) {
            try {
                // Call generateStudentCopy to get full academic data
                StudentCopyRequestDTO copyRequest = new StudentCopyRequestDTO();
                copyRequest.setStudentId(student.getId());
                copyRequest.setClassYearId(bcys.getClassYear().getId());
                copyRequest.setSemesterId(bcys.getSemester().getAcademicPeriodCode());

                StudentCopyDTO studentCopy = studentCopyService.generateStudentCopy(copyRequest);

                // Extract and convert to summary
                AcademicSummaryResponseDTO.StudentSummary summary = convertToStudentSummary(studentCopy);
                studentSummaries.add(summary);
            } catch (Exception e) {
                // Skip students that have errors (e.g., no valid copy for this BCYS)
                System.out.println("Skipping studentId " + student.getId() + " due to: " + e.getMessage());
                continue;
            }
        }
        System.out.println("Generated summaries for " + studentSummaries.size() + " students");

        // 6. Build and return response
        AcademicSummaryResponseDTO response = new AcademicSummaryResponseDTO();
        response.setHeader(headerInfo);
        response.setStudents(studentSummaries);

        return response;
    }

    /**
     * Helper: Builds header information from DepartmentBCYS and BCYS
     */
    private AcademicSummaryResponseDTO.HeaderInfo buildHeaderInfo(DepartmentBCYS deptBCYS, BatchClassYearSemester bcys) {
        AcademicSummaryResponseDTO.HeaderInfo header = new AcademicSummaryResponseDTO.HeaderInfo();

        // DepartmentBCYS has displayName
        header.setDepartmentBcysDisplay(deptBCYS.getDisplayName());

        // ClassYear and Semester info
        if (bcys.getClassYear() != null) {
            header.setClassYearName(bcys.getClassYear().getClassYear());
        }
        if (bcys.getSemester() != null) {
            header.setSemesterName(bcys.getSemester().getAcademicPeriod());
        }

        // Academic Year info from DepartmentBCYS
        if (deptBCYS.getAcademicYear() != null) {
            AcademicYear academicYear = deptBCYS.getAcademicYear();
            AcademicSummaryResponseDTO.AcademicYearInfo yearInfo = new AcademicSummaryResponseDTO.AcademicYearInfo();
            yearInfo.setYearCode(academicYear.getYearCode());
            yearInfo.setYearGC(academicYear.getAcademicYearGC());
            header.setAcademicYear(yearInfo);
        }

        return header;
    }

    /**
     * Helper: Converts StudentCopyDTO to StudentSummary
     */
    private AcademicSummaryResponseDTO.StudentSummary convertToStudentSummary(StudentCopyDTO studentCopy) {
        AcademicSummaryResponseDTO.StudentSummary summary = new AcademicSummaryResponseDTO.StudentSummary();

        summary.setStudentId(studentCopy.getIdNumber());

        // Convert course grades
        List<AcademicSummaryResponseDTO.CourseInfo> courses = studentCopy.getCourses().stream()
                .map(courseGrade -> new AcademicSummaryResponseDTO.CourseInfo(
                        courseGrade.getCourseCode(),
                        courseGrade.getCourseTitle(),
                        courseGrade.getScore(),
                        courseGrade.getLetterGrade()
                ))
                .collect(Collectors.toList());
        summary.setCourses(courses);

        // GPA and Status info
        summary.setSemesterGPA(studentCopy.getSemesterGPA());
        summary.setSemesterCGPA(studentCopy.getSemesterCGPA());
        summary.setSemesterGPALetter(studentCopy.getSemesterGPALetter());
        summary.setSemesterCGPALetter(studentCopy.getSemesterCGPALetter());
        summary.setPreviousCGPA(studentCopy.getPreviousCGPA());
        summary.setPreviousCGPALetter(studentCopy.getPreviousCGPALetter());
        summary.setStatus(studentCopy.getStatus());

        return summary;
    }
}
