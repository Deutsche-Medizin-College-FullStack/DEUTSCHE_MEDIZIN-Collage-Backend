package Henok.example.DeutscheCollageBack_endAPI.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtRequestFilter jwtRequestFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOriginPatterns(Arrays.asList("*"));
                    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")); // Allowed HTTP methods
                    config.setAllowedHeaders(Arrays.asList("*")); // Allow all headers
                    config.setAllowCredentials(false); // Credentials not needed for public APIs
                    return config;
                }))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login",
                                "/api/applicants/register",
                                "/api/auth/register", "/",
                                "/swagger-ui.html",
                                "/swagger-ui/index.html").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/enums/**",
                                "/api/country/**",
                                "/api/impairments/**",
                                "/api/program-modality/**",
                                "/api/program-levels/**",
                                "/api/region/**",
                                "/api/woreda/**",
                                "/api/zone/**",
                                "/api/academic-years/**",
                                "/api/departments/**",
                                "/api/enrollment-type/**",
                                "/api/school-backgrounds/**",
                                "/api/class-years/**",
                                "/api/semesters/**",
                                "/api/filters/options").permitAll()

                        .requestMatchers(HttpMethod.GET,
                                "/api/auth/me",
                                "/api/notifications/**",
                                "/api/auth/me/change-password",
                                "/api/courses/*").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/students/**").hasAnyRole("DEPARTMENT_HEAD", "REGISTRAR", "TEACHER", "DEAN", "VICE_DEAN", "GENERAL_MANAGER")


                        //Student Endpoints
                        .requestMatchers("/api/student/profile",
                                "/api/student/dashboard",
                                "/api/student/grade-reports").hasRole("STUDENT")

                        // Registrar endpoints
                        .requestMatchers(
                                "/api/batches/**",
                                "/api/class-years/**",
                                "/api/course-categories/**",
                                "/api/course-sources/**",
                                "/api/student-course-scores/**",
                                "/api/student-statuses/**",
                                "/api/semesters/**",
                                "/api/bcsy/**",
                                "/api/applicants/**",
                                "/api/auth/registrar/students/*/reset-password",
                                "/api/auth/register/student",
                               //---------------
                                "/api/student-slips/**",
                                "/api/students/slip-production",
                                "/api/grade-report/**",
                                "/api/students/*",
                                "/api/students/*/enable",
                                "/api/students/*/disable",
                                "/api/students/fields",
                                "/api/registrar/all-students",
                                // ------------------------------------
                                "/api/registrar/head-approved-scores",
                                "/api/registrar/assignments/*/final-approve-all",
                                "/api/registrar/profile",
                                "/api/registrar/update",
                                "/api/registrar/dashboard",
                                "/api/registrar/form-templates/**",
                                "/api/registrar/progression-sequences/**").hasRole("REGISTRAR")

                        // Department Head endpoints
                        .requestMatchers("/api/auth/register/teacher",
                                "/api/auth/head/teachers/*/reset-password",
                                "/api/department-heads/profile",
                                "/api/department-heads/update",
                                "/api/department-heads/dashboard",
                                "/api/department-heads/profile/photo",
                                "/api/department-heads/profile/document",
                                "/api/department-heads/teachers",
                                "/api/department-heads/my-courses",
                                "/api/department-heads/my-students",
                                "/api/department-heads/assessments/scores").hasRole("DEPARTMENT_HEAD")
                        .requestMatchers(HttpMethod.PUT,"/api/teachers/**",
                                "/api/department-heads/assignments/*/approve-all").hasRole("DEPARTMENT_HEAD")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/teachers/**",
                                "/api/teachers/*/course-assignments/**").hasRole("DEPARTMENT_HEAD")
                        .requestMatchers(HttpMethod.POST, "/api/teachers/*/course-assignments").hasRole("DEPARTMENT_HEAD")


                        // Teacher endpoints
                        .requestMatchers("/api/teachers/profile",
                                       "/api/teachers/update",
                                       "/api/teachers/my-students",
                                       "/api/teachers/courses/*/students",
                                       "/api/teachers/my-courses",
                                       "/api/teachers/dashboard",
                                       "/api/assessments/**",
                                       "/api/student-assessments/**").hasRole("TEACHER")

                        // --------------- Dean endpoints -----------------
                        .requestMatchers("/api/auth/register/vice-dean",
                                "/api/auth/vice-deans/*/reset-password",
                                "/api/deans/profile",
                                "/api/deans/update",
                                "/api/deans/dashboard",
                                "/api/deans/get-all-students-cgpa",
                                "/api/deans/department-heads",
                                "/api/deans/program-levels/**",
                                "/api/deans/program-modalities/**",
                                "/api/deans/head-approved-scores",
                                "/api/deans/assignments/*/approve-all").hasRole("DEAN")

                        // --------------- Vice Dean endpoints -----------------
                        .requestMatchers(
                                "/api/vice-deans/profile",
                                "/api/vice-deans/update",
                                "/api/vice-deans/dashboard",
                                "/api/vice-deans/get-all-students-cgpa").hasRole("VICE_DEAN")


                        // ================ GeneralManager endpoints ==================
                        .requestMatchers("/api/auth/register/general-manager",
                                "/api/auth/register/dean",
                                "/api/general-managers/**",
                                "/api/deans/active",
                                "/api/deans/*",
                                "/api/auth/deans/*/reset-password",
                                "/api/auth/registrars/*/reset-password").hasRole("GENERAL_MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/registrar/**").hasRole("GENERAL_MANAGER")


                        //---------------- Head and Registrar shared endpoints -----------------
                        .requestMatchers(HttpMethod.POST, "/api/courses",
                                "/api/courses/single",
                                "/api/courses/*",
                                "/api/courses/*/prerequisites/*").hasAnyRole("DEPARTMENT_HEAD", "REGISTRAR")
                        .requestMatchers(HttpMethod.PUT, "/api/courses",
                                "/api/courses/single",
                                "/api/courses/*",
                                "/api/courses/*/prerequisites/*").hasAnyRole("DEPARTMENT_HEAD", "REGISTRAR")
                        .requestMatchers(HttpMethod.DELETE, "/api/courses",
                                "/api/courses/single",
                                "/api/courses/*",
                                "/api/courses/*/prerequisites/*").hasAnyRole("DEPARTMENT_HEAD", "REGISTRAR")

                        // ---------------GeneralManager and Registrar shared endpoints -----------------
                        .requestMatchers("/api/registrar/photo/*",
                                "/api/registrar/nationalID/*",
                                "/api/registrar/update/*",
                                "/api/registrar/all").hasAnyRole("GENERAL_MANAGER", "REGISTRAR")

                        // ----------- Dean, Vice Dean and Registrar endpoints ----------------
                        .requestMatchers(
                                "/api/departments/**",
                                "/api/program-levels/**",
                                "/api/program-modality/**",
                                "/api/mark-intervals/**",
                                "/api/grading-systems/**").hasAnyRole("DEAN", "VICE_DEAN", "REGISTRAR")

                        // --------------- GeneralManager and ViceDeans or Deans shared endpoints -----------------
                        .requestMatchers("/api/auth/register/registrar").hasAnyRole("GENERAL_MANAGER", "VICE_DEAN", "DEAN")
                        .requestMatchers(HttpMethod.GET, "/api/department-heads/**").hasAnyRole("GENERAL_MANAGER", "VICE_DEAN", "DEAN")

                        // --------------- Dean and ViceDeans shared endpoints -----------------
                        .requestMatchers(
                                "/api/auth/register/department-head",
                                "/api/auth/department-heads/*/reset-password",
                                "/api/department-heads/**",
                                "/api/department-heads/get-photo/*",
                                "/api/department-heads/get-document/*",
                                "/api/department-heads/*/reassign-department").hasAnyRole("DEAN", "VICE_DEAN")


                        // ---------------- GeneralManager and Dean shared endpoints -----------------
                        .requestMatchers("/api/vice-deans/active",
                                "/api/vice-deans/*",
                                "/api/vice-deans/get-photo/*",
                                "/api/vice-deans/get-document/*").hasAnyRole("GENERAL_MANAGER", "DEAN")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(customAccessDeniedHandler)  // ← This is key
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint()) // optional: for unauthenticated
                )
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}