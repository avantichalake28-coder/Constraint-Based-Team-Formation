package com.teamformation.backend.engine.formation.persistence;

import com.teamformation.backend.engine.formation.FormationPlan;
import com.teamformation.backend.engine.formation.FormationTeam;
import com.teamformation.backend.entity.CandidateTeam;
import com.teamformation.backend.entity.CandidateTeamMember;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.MembershipSource;
import com.teamformation.backend.repository.CandidateTeamMemberRepository;
import com.teamformation.backend.repository.CandidateTeamRepository;
import com.teamformation.backend.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persists one validated plan as candidate teams and algorithm-sourced
 * memberships. The caller owns the surrounding transaction; this component only
 * performs the writes and flushes so persistence can be verified immediately.
 */
@Component
public class FormationPlanPersister {

    private final CandidateTeamRepository candidateTeamRepository;
    private final CandidateTeamMemberRepository candidateTeamMemberRepository;
    private final UserRepository userRepository;

    public FormationPlanPersister(
            CandidateTeamRepository candidateTeamRepository,
            CandidateTeamMemberRepository candidateTeamMemberRepository,
            UserRepository userRepository) {
        this.candidateTeamRepository = candidateTeamRepository;
        this.candidateTeamMemberRepository = candidateTeamMemberRepository;
        this.userRepository = userRepository;
    }

    public void persist(Project project, FormationJob job, FormationPlan plan) {
        List<CandidateTeam> teams = new ArrayList<>(plan.teamCount());
        for (FormationTeam plannedTeam : plan.teams()) {
            CandidateTeam team = new CandidateTeam(project, job, plannedTeam.teamName());
            team.setCompatibilityScore(plannedTeam.preferenceScore());
            teams.add(team);
        }
        candidateTeamRepository.saveAll(teams);
        candidateTeamRepository.flush();

        Set<Long> studentIds = new LinkedHashSet<>();
        plan.teams().forEach(team -> studentIds.addAll(team.memberIds()));
        Map<Long, User> usersById = new HashMap<>();
        for (User student : userRepository.findAllById(studentIds)) {
            usersById.put(student.getId(), student);
        }

        List<CandidateTeamMember> members = new ArrayList<>(plan.assignedStudentCount());
        for (int index = 0; index < plan.teams().size(); index++) {
            FormationTeam plannedTeam = plan.teams().get(index);
            CandidateTeam team = teams.get(index);
            for (Long studentId : plannedTeam.memberIds()) {
                User student = usersById.get(studentId);
                if (student == null) {
                    throw new IllegalStateException(
                            "Eligible student " + studentId + " could not be loaded for persistence");
                }
                members.add(new CandidateTeamMember(
                        team, student, job.getId(), MembershipSource.ALGORITHM));
            }
        }
        candidateTeamMemberRepository.saveAll(members);
        candidateTeamMemberRepository.flush();
    }
}
