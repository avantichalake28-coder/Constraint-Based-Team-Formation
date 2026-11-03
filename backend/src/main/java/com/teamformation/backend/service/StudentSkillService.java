package com.teamformation.backend.service;

import com.teamformation.backend.dto.StudentSkillRequest;
import com.teamformation.backend.dto.StudentSkillResponse;
import com.teamformation.backend.dto.UpdateStudentSkillsRequest;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.StudentSkill;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.repository.SkillRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.StudentSkillRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Student skill selection from the mentor/admin catalogue. Every PUT replaces
 * the whole set atomically. Skills are trusted as entered.
 */
@Service
public class StudentSkillService {

    private final StudentSkillRepository studentSkillRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SkillRepository skillRepository;
    private final CurrentUserService currentUserService;

    public StudentSkillService(
            StudentSkillRepository studentSkillRepository,
            StudentProfileRepository studentProfileRepository,
            SkillRepository skillRepository,
            CurrentUserService currentUserService) {
        this.studentSkillRepository = studentSkillRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.skillRepository = skillRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<StudentSkillResponse> getMySkills(Authentication authentication) {
        StudentProfile profile = loadOwnProfile(authentication);
        return studentSkillRepository.findByStudentProfileId(profile.getId()).stream()
                .sorted(Comparator.comparing(skill -> skill.getSkill().getName()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<StudentSkillResponse> replaceMySkills(
            Authentication authentication, UpdateStudentSkillsRequest request) {
        StudentProfile profile = loadOwnProfile(authentication);
        List<StudentSkillRequest> requested = request.skills();

        Set<Long> seen = new LinkedHashSet<>();
        for (StudentSkillRequest item : requested) {
            if (!seen.add(item.skillId())) {
                throw new BusinessException("Duplicate skill id in request: " + item.skillId());
            }
        }

        Map<Long, Skill> skills = new LinkedHashMap<>();
        List<Long> missing = new ArrayList<>();
        for (Long skillId : seen) {
            skillRepository.findById(skillId).ifPresentOrElse(
                    skill -> skills.put(skillId, skill),
                    () -> missing.add(skillId));
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("Unknown skill ids: " + missing);
        }

        studentSkillRepository.deleteByStudentProfileId(profile.getId());
        List<StudentSkill> saved = new ArrayList<>();
        for (StudentSkillRequest item : requested) {
            saved.add(studentSkillRepository.save(
                    new StudentSkill(profile, skills.get(item.skillId()), item.proficiency())));
        }
        return saved.stream()
                .sorted(Comparator.comparing(skill -> skill.getSkill().getName()))
                .map(this::toResponse)
                .toList();
    }

    private StudentProfile loadOwnProfile(Authentication authentication) {
        User student = currentUserService.requireCurrentUserWithRole(authentication, UserRole.STUDENT);
        return studentProfileRepository.findByUserId(student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "userId", student.getId()));
    }

    private StudentSkillResponse toResponse(StudentSkill studentSkill) {
        Skill skill = studentSkill.getSkill();
        return new StudentSkillResponse(
                skill.getId(), skill.getName(), skill.getCategory(), studentSkill.getProficiencyLevel());
    }
}
