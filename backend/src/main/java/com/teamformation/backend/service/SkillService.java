package com.teamformation.backend.service;

import com.teamformation.backend.dto.CreateSkillRequest;
import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.dto.SkillResponse;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.repository.SkillRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill catalogue. Names are trimmed and unique ignoring case.
 */
@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final CurrentUserService currentUserService;

    public SkillService(SkillRepository skillRepository, CurrentUserService currentUserService) {
        this.skillRepository = skillRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SkillResponse> listSkills(Authentication authentication, Pageable pageable) {
        currentUserService.requireCurrentUser(authentication);
        return PageResponse.from(skillRepository.findAll(pageable).map(this::toResponse));
    }

    @Transactional
    public SkillResponse createSkill(Authentication authentication, CreateSkillRequest request) {
        currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR, UserRole.ADMIN);
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new BusinessException("Skill name is required");
        }
        if (skillRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new BusinessException("Skill already exists", HttpStatus.CONFLICT);
        }
        Skill skill = new Skill(name, trim(request.category()), trim(request.description()));
        try {
            skillRepository.saveAndFlush(skill);
        } catch (DataIntegrityViolationException ex) {
            // The database collation is case-insensitive, so a concurrent
            // insert of the same name in different case lands here.
            throw new BusinessException("Skill already exists", HttpStatus.CONFLICT);
        }
        return toResponse(skill);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SkillResponse toResponse(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName(), skill.getCategory(), skill.getDescription());
    }
}
