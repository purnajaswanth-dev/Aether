package com.context_platform.aether.decision;

import com.context_platform.aether.common.ActorContext;
import com.context_platform.aether.common.ActorType;
import com.context_platform.aether.common.ApiException;
import com.context_platform.aether.common.OptimisticLockingException;
import com.context_platform.aether.common.ResourceNotFoundException;
import com.context_platform.aether.event.AuditService;
import com.context_platform.aether.event.TimelineService;
import com.context_platform.aether.security.ProjectSecurityChecker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DecisionService {

    private final DecisionRepository decisionRepository;
    private final TimelineService timelineService;
    private final AuditService auditService;
    private final ProjectSecurityChecker securityChecker;

    public DecisionService(
        DecisionRepository decisionRepository,
        TimelineService timelineService,
        AuditService auditService,
        ProjectSecurityChecker securityChecker
    ) {
        this.decisionRepository = decisionRepository;
        this.timelineService = timelineService;
        this.auditService = auditService;
        this.securityChecker = securityChecker;
    }

    @Transactional
    public DecisionResponse createDecision(UUID projectId, CreateDecisionRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();
        return createDecisionInternal(projectId, request, actor);
    }

    @Transactional
    public DecisionResponse createDecision(UUID projectId, CreateDecisionRequest request, ActorContext actor) {
        return createDecisionInternal(projectId, request, actor);
    }

    private DecisionResponse createDecisionInternal(UUID projectId, CreateDecisionRequest request, ActorContext actor) {
        String publicId = decisionRepository.nextPublicId(projectId);

        TechnicalDecision decision = decisionRepository.createDecision(
            projectId,
            publicId,
            request.title(),
            DecisionState.PROPOSED.name(),
            request.category()
        );

        DecisionVersion version1 = decisionRepository.appendVersion(
            decision.id(),
            projectId,
            1,
            DecisionState.PROPOSED.name(),
            request.problemStatement(),
            request.proposedSolution(),
            request.rationale(),
            request.consequences(),
            request.sourceArtifactVersionId(),
            actor.actorId(),
            actor.actorType().name(),
            "Initial proposal"
        );

        timelineService.append(
            projectId,
            null,
            actor.actorType() == ActorType.AGENT ? "AGENT" : "PLATFORM",
            "DECISION_PROPOSED",
            null,
            decision.id(),
            1,
            "SUCCESS",
            actor.actorId(),
            actor.actorType().name(),
            "Proposed technical decision " + decision.publicId() + ": '" + decision.title() + "'",
            "{\"decisionId\":\"" + decision.id() + "\",\"publicId\":\"" + decision.publicId() + "\",\"state\":\"PROPOSED\"}"
        );

        return toResponse(decision, version1);
    }

    @Transactional
    public DecisionResponse submitForReview(UUID projectId, UUID decisionId, DecisionActionRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();

        return transitionDecision(
            projectId,
            decisionId,
            DecisionState.UNDER_REVIEW,
            request.expectedLockVersion(),
            request.notes(),
            null,
            request.evidenceArtifactVersionId(),
            actor,
            userId,
            "DECISION_REVIEW_REQUESTED",
            "Submitted decision for review: "
        );
    }

    @Transactional
    public DecisionResponse approveDecision(UUID projectId, UUID decisionId, DecisionActionRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();

        // CONSTITUTIONAL RULE 5: AI-generated decisions are never auto-approved. Only an authenticated human user can approve.
        if (actor.actorType() != ActorType.HUMAN_USER) {
            throw new ApiException(
                "AI agents are strictly forbidden from approving decisions. Only authenticated human users can approve decisions.",
                HttpStatus.FORBIDDEN
            );
        }

        DecisionResponse response = transitionDecision(
            projectId,
            decisionId,
            DecisionState.APPROVED,
            request.expectedLockVersion(),
            request.notes() != null ? request.notes() : "Decision approved by human reviewer",
            null,
            request.evidenceArtifactVersionId(),
            actor,
            userId,
            "DECISION_APPROVED",
            "Approved technical decision: "
        );

        auditService.log(
            projectId,
            userId,
            "DECISION_APPROVED",
            "HUMAN_USER",
            null,
            null,
            "{\"decisionId\":\"" + decisionId + "\",\"version\":" + response.currentVersion() + "}"
        );

        return response;
    }

    @Transactional
    public DecisionResponse rejectDecision(UUID projectId, UUID decisionId, DecisionActionRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();

        if (actor.actorType() != ActorType.HUMAN_USER) {
            throw new ApiException("Only authenticated human users can reject decisions.", HttpStatus.FORBIDDEN);
        }

        DecisionResponse response = transitionDecision(
            projectId,
            decisionId,
            DecisionState.REJECTED,
            request.expectedLockVersion(),
            request.notes() != null ? request.notes() : "Decision rejected",
            null,
            request.evidenceArtifactVersionId(),
            actor,
            userId,
            "DECISION_REJECTED",
            "Rejected technical decision: "
        );

        auditService.log(projectId, userId, "DECISION_REJECTED", "HUMAN_USER", null, null, "{\"decisionId\":\"" + decisionId + "\"}");
        return response;
    }

    @Transactional
    public DecisionResponse supersedeDecision(UUID projectId, UUID decisionId, DecisionActionRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();

        if (actor.actorType() != ActorType.HUMAN_USER) {
            throw new ApiException("Only authenticated human users can supersede decisions.", HttpStatus.FORBIDDEN);
        }

        return transitionDecision(
            projectId,
            decisionId,
            DecisionState.SUPERSEDED,
            request.expectedLockVersion(),
            request.notes() != null ? request.notes() : "Decision superseded",
            request.linkedDecisionId(),
            request.evidenceArtifactVersionId(),
            actor,
            userId,
            "DECISION_SUPERSEDED",
            "Superseded technical decision: "
        );
    }

    @Transactional
    public DecisionResponse markImplemented(UUID projectId, UUID decisionId, DecisionActionRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();

        return transitionDecision(
            projectId,
            decisionId,
            DecisionState.IMPLEMENTED,
            request.expectedLockVersion(),
            request.notes() != null ? request.notes() : "Implementation completed",
            null,
            request.evidenceArtifactVersionId(),
            actor,
            userId,
            "DECISION_IMPLEMENTED",
            "Marked implemented: "
        );
    }

    @Transactional
    public DecisionResponse markVerified(UUID projectId, UUID decisionId, DecisionActionRequest request, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "MEMBER");
        ActorContext actor = securityChecker.getCurrentActor();

        return transitionDecision(
            projectId,
            decisionId,
            DecisionState.VERIFIED,
            request.expectedLockVersion(),
            request.notes() != null ? request.notes() : "Verified with evidence",
            null,
            request.evidenceArtifactVersionId(),
            actor,
            userId,
            "DECISION_VERIFIED",
            "Marked verified: "
        );
    }

    @Transactional(readOnly = true)
    public List<DecisionResponse> listDecisions(UUID projectId, String state, String category, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        List<TechnicalDecision> decisions = decisionRepository.listDecisions(projectId, state, category);
        List<DecisionResponse> result = new ArrayList<>();

        for (TechnicalDecision d : decisions) {
            DecisionVersion latest = decisionRepository.getVersion(d.id(), d.currentVersion(), projectId).orElse(null);
            result.add(toResponse(d, latest));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public DecisionDetailResponse getDecision(UUID projectId, UUID decisionId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        TechnicalDecision decision = decisionRepository.findById(decisionId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Decision", decisionId));

        List<DecisionVersion> versions = decisionRepository.listVersions(decisionId, projectId);
        List<DecisionEvidence> evidence = decisionRepository.listEvidence(decisionId, projectId);
        DecisionVersion latest = versions.isEmpty() ? null : versions.get(versions.size() - 1);

        return new DecisionDetailResponse(toResponse(decision, latest), versions, evidence);
    }

    @Transactional(readOnly = true)
    public DecisionComparisonResponse compareDecision(UUID projectId, UUID decisionId, UUID userId) {
        securityChecker.verifyProjectAccess(projectId, userId, "VIEWER");

        TechnicalDecision decision = decisionRepository.findById(decisionId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Decision", decisionId));

        List<DecisionVersion> versions = decisionRepository.listVersions(decisionId, projectId);
        if (versions.isEmpty()) {
            throw new ApiException("No versions found for decision " + decision.publicId(), HttpStatus.NOT_FOUND);
        }

        DecisionVersion originalProposal = versions.get(0);
        List<DecisionVersion> reviewFindings = new ArrayList<>();
        List<DecisionVersion> modifiedProposals = new ArrayList<>();
        DecisionVersion approvedVersion = null;

        for (int i = 0; i < versions.size(); i++) {
            DecisionVersion v = versions.get(i);
            if (DecisionState.UNDER_REVIEW.name().equalsIgnoreCase(v.state())) {
                reviewFindings.add(v);
            }
            if (DecisionState.APPROVED.name().equalsIgnoreCase(v.state())) {
                approvedVersion = v;
            }
            if (i > 0 && i < versions.size() - 1 && !DecisionState.APPROVED.name().equalsIgnoreCase(v.state())) {
                modifiedProposals.add(v);
            }
        }

        DecisionVersion latest = versions.get(versions.size() - 1);
        return new DecisionComparisonResponse(
            toResponse(decision, latest),
            originalProposal,
            reviewFindings,
            modifiedProposals,
            approvedVersion
        );
    }

    private DecisionResponse transitionDecision(
        UUID projectId,
        UUID decisionId,
        DecisionState targetState,
        Integer expectedLockVersion,
        String notes,
        UUID linkedDecisionId,
        UUID evidenceArtifactVersionId,
        ActorContext actor,
        UUID userId,
        String eventType,
        String logPrefix
    ) {
        TechnicalDecision decision = decisionRepository.findById(decisionId, projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Decision", decisionId));

        DecisionState currentState = DecisionState.valueOf(decision.state());
        DecisionStateMachine.validateTransition(currentState, targetState);

        int lockVersion = expectedLockVersion != null ? expectedLockVersion : decision.lockVersion();
        int nextVersion = decision.currentVersion() + 1;

        // Optimistic locking check
        int rowsUpdated = decisionRepository.updateStateWithOptimisticLock(
            decisionId,
            projectId,
            targetState.name(),
            nextVersion,
            lockVersion
        );

        if (rowsUpdated == 0) {
            throw new OptimisticLockingException(
                "Conflict detected: Decision " + decision.publicId() + " was modified concurrently. " +
                "Expected lock version " + lockVersion + ", but state has already changed."
            );
        }

        DecisionVersion prevVersion = decisionRepository.getVersion(decisionId, decision.currentVersion(), projectId)
            .orElse(null);

        DecisionVersion newVersion = decisionRepository.appendVersion(
            decisionId,
            projectId,
            nextVersion,
            targetState.name(),
            prevVersion != null ? prevVersion.problemStatement() : "",
            prevVersion != null ? prevVersion.proposedSolution() : "",
            prevVersion != null ? prevVersion.rationale() : "",
            prevVersion != null ? prevVersion.consequences() : "",
            evidenceArtifactVersionId != null ? evidenceArtifactVersionId : (prevVersion != null ? prevVersion.sourceArtifactVersionId() : null),
            actor.actorId(),
            actor.actorType().name(),
            notes
        );

        if (evidenceArtifactVersionId != null) {
            decisionRepository.addEvidence(
                decisionId,
                projectId,
                "DOC_REFERENCE",
                notes != null ? notes : "Evidence linked during transition to " + targetState,
                evidenceArtifactVersionId,
                actor.actorId()
            );
        }

        timelineService.append(
            projectId,
            null,
            actor.actorType() == ActorType.AGENT ? "AGENT" : "PLATFORM",
            eventType,
            null,
            decisionId,
            nextVersion,
            "SUCCESS",
            actor.actorId(),
            actor.actorType().name(),
            logPrefix + decision.publicId() + " -> " + targetState.name() + " (v" + nextVersion + ")",
            "{\"decisionId\":\"" + decisionId + "\",\"version\":" + nextVersion + ",\"state\":\"" + targetState + "\"}"
        );

        return new DecisionResponse(
            decision.id(),
            decision.projectId(),
            decision.publicId(),
            decision.title(),
            targetState.name(),
            decision.category(),
            nextVersion,
            lockVersion + 1,
            newVersion.problemStatement(),
            newVersion.proposedSolution(),
            newVersion.rationale(),
            newVersion.consequences(),
            actor.actorId(),
            actor.actorType().name(),
            decision.createdAt(),
            newVersion.createdAt()
        );
    }

    private DecisionResponse toResponse(TechnicalDecision d, DecisionVersion v) {
        return new DecisionResponse(
            d.id(),
            d.projectId(),
            d.publicId(),
            d.title(),
            d.state(),
            d.category(),
            d.currentVersion(),
            d.lockVersion(),
            v != null ? v.problemStatement() : "",
            v != null ? v.proposedSolution() : "",
            v != null ? v.rationale() : "",
            v != null ? v.consequences() : "",
            v != null ? v.actorId() : "SYSTEM",
            v != null ? v.actorType() : "HUMAN_USER",
            d.createdAt(),
            d.updatedAt()
        );
    }
}

