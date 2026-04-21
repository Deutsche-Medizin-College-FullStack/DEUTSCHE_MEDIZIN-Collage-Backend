package Henok.example.DeutscheCollageBack_endAPI.Service;

import Henok.example.DeutscheCollageBack_endAPI.DTO.GradeReport.GradeReportDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.GradeReport.GradeReportRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.GradeReport.GradeReportResponseDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.SimplifiedStudentCopyDTO;
import Henok.example.DeutscheCollageBack_endAPI.DTO.StudentCopy.StudentCopyRequestDTO;
import Henok.example.DeutscheCollageBack_endAPI.Entity.*;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.ProgramLevel;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Semester;
import Henok.example.DeutscheCollageBack_endAPI.Repository.*;
import Henok.example.DeutscheCollageBack_endAPI.Repository.MOE_Repos.SemesterRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GradeReportService {

    @Autowired
    private StudentDetailsRepository studentDetailsRepository;
    @Autowired
    private StudentCourseScoreRepo studentCourseScoreRepo;
    @Autowired
    private BatchClassYearSemesterRepo batchClassYearSemesterRepo;
    @Autowired
    private StudentCopyService studentCopyService;
    @Autowired
    private GradingSystemService gradingSystemService;
    @Autowired
    private ProgressionSequenceRepository progressionSequenceRepository;
    @Autowired
    private ClassYearRepository classYearRepository;
    @Autowired
    private SemesterRepo semesterRepo;

    /**
     * Generates grade reports for multiple students.
     * Optimized: skips invalid IDs early, continues on per-student errors.
     *
     * @param request The request containing list of student IDs
     * @return GradeReportResponseDTO containing grade reports for all valid students
     */
    @Transactional(readOnly = true)
    public GradeReportResponseDTO generateGradeReports(GradeReportRequestDTO request) {
        if (request == null || request.getStudentIds() == null || request.getStudentIds().isEmpty()) {
            throw new IllegalArgumentException("Student IDs list cannot be null or empty");
        }

        List<GradeReportDTO> gradeReports = new ArrayList<>(request.getStudentIds().size());

        for (Long studentId : request.getStudentIds()) {
            if (studentId == null) {
                continue;
            }

            try {
                GradeReportDTO gradeReport = generateGradeReportForStudent(studentId);
                if (gradeReport != null) {
                    gradeReports.add(gradeReport);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        GradeReportResponseDTO response = new GradeReportResponseDTO();
        response.setGradeReports(gradeReports);
        return response;
    }

    /**
     * Generates a grade report for a single student.
     * Optimized: pre-fetches all released scores once, groups efficiently, skips redundant queries.
     *
     * @param studentId The student ID
     * @return GradeReportDTO or null if student doesn't exist or has no records
     */
    @Transactional(readOnly = true)
    private GradeReportDTO generateGradeReportForStudent(Long studentId) {
        StudentDetails student = studentDetailsRepository.findById(studentId).orElse(null);
        if (student == null || student.getUser() == null) {
            return null;
        }

        User studentUser = student.getUser();

        // Fetch all released scores once (source of truth for history)
        List<StudentCourseScore> allReleasedScores = studentCourseScoreRepo
                .findByStudentAndIsReleasedTrue(studentUser);

        if (allReleasedScores.isEmpty()) {
            return null;
        }

        // Group by ClassYear + Semester combination (NOT by BatchClassYearSemester)
        // This prevents duplicate entries when student repeats the same year/semester in different batches
        Map<String, List<StudentCourseScore>> scoresByClassYearSemester = allReleasedScores.stream()
                .collect(Collectors.groupingBy(score -> 
                    score.getBatchClassYearSemester().getClassYear().getId() + "_" +
                    score.getBatchClassYearSemester().getSemester().getAcademicPeriodCode()
                ));

        // Prepare simplified copies - one per unique ClassYear + Semester
        List<SimplifiedStudentCopyDTO> studentCopies = new ArrayList<>(scoresByClassYearSemester.size());

        Department studentDept = student.getDepartmentEnrolled();

        for (Map.Entry<String, List<StudentCourseScore>> entry : scoresByClassYearSemester.entrySet()) {
            // Take any score from this group to extract classYear and semester (they are the same within group)
            StudentCourseScore anyScore = entry.getValue().get(0);
            BatchClassYearSemester anyBCYS = anyScore.getBatchClassYearSemester();

            try {
                StudentCopyRequestDTO request = new StudentCopyRequestDTO();
                request.setStudentId(studentId);
                request.setClassYearId(anyBCYS.getClassYear().getId());
                request.setSemesterId(anyBCYS.getSemester().getAcademicPeriodCode());

                SimplifiedStudentCopyDTO copy = studentCopyService.generateSimplifiedStudentCopy(request);
                if (copy != null) {
                    studentCopies.add(copy);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // Sort using ProgressionSequence (department-aware)
        studentCopies.sort(Comparator.comparingInt(copy ->
                getProgressionSequenceNumber(studentDept,
                        copy.getClassyear().getId(),
                        copy.getSemester().getId())
        ));

        // Build GradeReportDTO
        GradeReportDTO gradeReport = new GradeReportDTO();

        // Student Information
        gradeReport.setIdNumber(studentUser.getUsername());
        gradeReport.setFullName(String.join(" ",
                student.getFirstNameENG(),
                student.getFatherNameENG(),
                student.getGrandfatherNameENG()).trim());
        gradeReport.setGender(student.getGender().name());
        gradeReport.setBirthDateGC(student.getDateOfBirthGC());

        // Program Information
        GradeReportDTO.ProgramModalityInfo programModalityInfo = new GradeReportDTO.ProgramModalityInfo();
        programModalityInfo.setId(student.getProgramModality().getModalityCode());
        programModalityInfo.setName(student.getProgramModality().getModality());
        gradeReport.setProgramModality(programModalityInfo);

        Department department = student.getDepartmentEnrolled();
        GradeReportDTO.ProgramLevelInfo programLevelInfo = new GradeReportDTO.ProgramLevelInfo();
        ProgramLevel programLevel = department.getProgramLevel();
        if (programLevel != null) {
            programLevelInfo.setId(programLevel.getCode());
            programLevelInfo.setName(programLevel.getName());
        }
        gradeReport.setProgramLevel(programLevelInfo);

        GradeReportDTO.DepartmentInfo departmentInfo = new GradeReportDTO.DepartmentInfo();
        departmentInfo.setId(department.getDptID());
        departmentInfo.setName(department.getDeptName());
        gradeReport.setDepartment(departmentInfo);

        gradeReport.setDateEnrolledGC(student.getDateEnrolledGC());
        gradeReport.setDateIssuedGC(LocalDate.now());

        // ──────────────────────────────────────────────
        // Add footer text here
        GradingSystem gradingSystem = gradingSystemService.findApplicableGradingSystem(department);

        StringBuilder footer = new StringBuilder();

        // 1. Grading intervals in one line
        List<MarkInterval> intervals = gradingSystem.getIntervals();
        intervals.sort(Comparator.comparingDouble(MarkInterval::getGivenValue).reversed()); // A+ first

        for (int i = 0; i < intervals.size(); i++) {
            MarkInterval interval = intervals.get(i);
            footer.append(interval.getGradeLetter())
                    .append(" = ")
                    .append(String.format("%.2f", interval.getGivenValue()));
            if (i < intervals.size() - 1) {
                footer.append(", ");
            }
        }
        footer.append("\n");

        // 2. Notation / symbols
        footer.append("** = Course Repeated, * = Credit Transferred / Taken Externally\n");

        // 3. Date issued (human readable format - adjust locale/formatter if needed)
        footer.append("Date Issued: ")
                .append(gradeReport.getDateIssuedGC()
                        .format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
                .append("\n");

        // 4. Final disclaimer
        footer.append("THE TRANSCRIPT IS OFFICIAL ONLY WHEN SIGNED AND SEALED BY THE REGISTRAR");

        gradeReport.setFooterText(footer.toString());
        // ──────────────────────────────────────────────

        // Attach sorted copies
        gradeReport.setStudentCopies(studentCopies);

        return gradeReport;
    }

    /**
     * Returns the progression sequence number for a given classYear + semester.
     * Uses department-specific rule first, then global fallback.
     * Returns Integer.MAX_VALUE if no rule found (so it goes to the end).
     */
    private int getProgressionSequenceNumber(Department dept, Long classYearId, String semesterCode) {
        ClassYear cy = classYearRepository.findById(classYearId).orElse(null);
        Semester sem = semesterRepo.findById(semesterCode).orElse(null);
        if (cy == null || sem == null) {
            return Integer.MAX_VALUE;
        }

        // Try department-specific
        if (dept != null) {
            Optional<ProgressionSequence> specific = progressionSequenceRepository
                    .findByDepartmentAndClassYearAndSemester(dept, cy, sem);
            if (specific.isPresent()) {
                return specific.get().getSequenceNumber();
            }
        }

        // Fallback to global
        Optional<ProgressionSequence> global = progressionSequenceRepository
                .findByDepartmentIsNullAndClassYearAndSemester(cy, sem);

        return global.map(ProgressionSequence::getSequenceNumber)
                .orElse(Integer.MAX_VALUE);
    }

    /**
     * Helper class to hold ClassYear and Semester pairs.
     */
    private static class ClassYearSemesterPair {
        ClassYear classYear;
        Semester semester;

        ClassYearSemesterPair(ClassYear classYear, Semester semester) {
            this.classYear = classYear;
            this.semester = semester;
        }
    }
}

