package Henok.example.DeutscheCollageBack_endAPI.Entity;

import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Impairment;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Region;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Woreda;
import Henok.example.DeutscheCollageBack_endAPI.Entity.MOE_Data.Zone;
import Henok.example.DeutscheCollageBack_endAPI.Enums.Gender;
import Henok.example.DeutscheCollageBack_endAPI.Enums.MaritalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// TeacherDetail.java
@Entity
@Table(name = "teacher_detail")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One-to-one with User (FK = user_id)
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // --- Personal names (Amharic) ---
    @Column(name = "first_name_amharic", nullable = false)
    private String firstNameAmharic;

    @Column(name = "last_name_amharic", nullable = false)
    private String lastNameAmharic;

    // --- Personal names (English) ---
    @Column(name = "first_name_english", nullable = false)
    private String firstNameEnglish;

    @Column(name = "last_name_english", nullable = false)
    private String lastNameEnglish;

    // --- Demographics ---
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "date_of_birth_gc", nullable = false)
    private LocalDate dateOfBirthGC;

    @Column(name = "date_of_birth_ec")
    private String dateOfBirthEC;   // nullable

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Column
    private String email;

    // --- Employment ---
    @ManyToOne
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "hire_date_gc")
    private LocalDate hireDateGC;   // nullable

    @Column(name = "hire_date_ec")
    private String hireDateEC;      // nullable

    @Column(name = "resigned_date")
    private LocalDate resignedDate; // nullable

    @Column
    private String title;           // e.g. Lecturer, Professor

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    // --- Optional personal details ---
    @ManyToOne
    @JoinColumn(name = "impairment_code")
    private Impairment impairment;

    @Column
    @Enumerated(EnumType.STRING)
    private MaritalStatus maritalStatus;

    @ManyToOne
    @JoinColumn(name = "current_address_woreda")
    private Woreda currentAddressWoreda;

    @ManyToOne
    @JoinColumn(name = "current_address_zone")
    private Zone currentAddressZone;

    @ManyToOne
    @JoinColumn(name = "current_address_region")
    private Region currentAddressRegion;

    // --- Files ---
    @Column(name = "photograph", columnDefinition = "LONGBLOB")
    private byte[] photograph;

    @Lob
    @Column(name = "documents", columnDefinition = "LONGBLOB")
    private byte[] documents;       // single PDF (CV, certificates, …)
}