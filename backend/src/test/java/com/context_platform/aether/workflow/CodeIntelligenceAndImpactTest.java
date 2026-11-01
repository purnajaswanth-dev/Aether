package com.context_platform.aether.workflow;

import com.context_platform.aether.BaseTest;
import com.context_platform.aether.auth.AuthResponse;
import com.context_platform.aether.project.ProjectResponse;
import com.context_platform.aether.workflow.model.CodeSymbolRecord;
import com.context_platform.aether.workflow.model.dto.ChangeImpactResult;
import com.context_platform.aether.workflow.repository.CodeSymbolRepository;
import com.context_platform.aether.workflow.service.CodeIntelligenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CodeIntelligenceAndImpactTest extends BaseTest {

    @Autowired
    private CodeIntelligenceService codeIntelligenceService;

    @Autowired
    private CodeSymbolRepository codeSymbolRepository;

    private UUID projectId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        AuthResponse user = registerUser("code_intel_tester@aether.test", "SecurePass123!", "Code Intel Tester");
        userId = user.user().id();
        ProjectResponse project = createTestProject("Code Intel Project", userId);
        projectId = project.id();
    }

    @Test
    void testJavaAstIndexingAndSymbolExtraction() {
        String javaSource = """
                package com.example.service;
                
                import com.example.model.User;
                import java.util.List;
                
                public class UserService {
                    public User findById(String id) {
                        return null;
                    }
                    
                    public void saveUser(User user) {
                        System.out.println("Saving");
                    }
                }
                """;

        codeIntelligenceService.indexSource(projectId, "src/main/java/com/example/service/UserService.java", javaSource);

        List<CodeSymbolRecord> symbols = codeSymbolRepository.findByFilePath(projectId, "src/main/java/com/example/service/UserService.java");
        assertFalse(symbols.isEmpty());

        assertTrue(symbols.stream().anyMatch(s -> "UserService".equals(s.symbolName()) && "CLASS".equals(s.symbolType())));
        assertTrue(symbols.stream().anyMatch(s -> s.symbolName().contains("findById") && "METHOD".equals(s.symbolType())));
        assertTrue(symbols.stream().anyMatch(s -> s.symbolName().contains("saveUser") && "METHOD".equals(s.symbolType())));
    }

    @Test
    void testChangeImpactAnalysisAndRelevantTests() {
        // 1. Index Service
        String serviceSource = """
                package com.example.service;
                public class PaymentService {
                    public boolean processPayment(double amount) { return true; }
                }
                """;
        codeIntelligenceService.indexSource(projectId, "src/main/java/com/example/service/PaymentService.java", serviceSource);

        // 2. Index Controller that imports Service
        String controllerSource = """
                package com.example.controller;
                import com.example.service.PaymentService;
                public class PaymentController {
                    private PaymentService paymentService;
                }
                """;
        codeIntelligenceService.indexSource(projectId, "src/main/java/com/example/controller/PaymentController.java", controllerSource);

        // 3. Index Test that imports Service
        String testSource = """
                package com.example.service;
                import com.example.service.PaymentService;
                public class PaymentServiceTest {
                    void testPayment() {}
                }
                """;
        codeIntelligenceService.indexSource(projectId, "src/test/java/com/example/service/PaymentServiceTest.java", testSource);

        // Analyze change impact when PaymentService.java is modified
        ChangeImpactResult impact = codeIntelligenceService.analyzeChangeImpact(
                projectId,
                List.of("src/main/java/com/example/service/PaymentService.java")
        );

        assertNotNull(impact);
        assertTrue(impact.affectedApis().stream().anyMatch(a -> a.contains("PaymentController")),
                "PaymentController should be identified as an affected API");
        assertTrue(impact.affectedTests().stream().anyMatch(t -> t.contains("PaymentServiceTest")),
                "PaymentServiceTest should be identified as an affected test suite");

        // Verify confidence levels
        assertEquals("HIGH", impact.confidenceLevels().get("src/test/java/com/example/service/PaymentServiceTest.java"));

        // Get relevant tests directly
        List<String> relevantTests = codeIntelligenceService.getRelevantTests(
                projectId,
                List.of("src/main/java/com/example/service/PaymentService.java")
        );
        assertTrue(relevantTests.stream().anyMatch(t -> t.contains("PaymentServiceTest")));
    }
}
