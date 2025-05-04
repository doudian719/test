package com.joe.task.service.crd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joe.task.model.crd.ClusterFlowParameters;
import com.joe.task.model.crd.ClusterOutputParameters;
import com.joe.task.service.k8s.CRDService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.reset;

@ExtendWith(MockitoExtension.class)
class CRDTemplateServiceTest {

    @Mock
    private CRDService crdService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CRDTemplateService crdTemplateService;

    private static final String ENV = "prod";
    private static final String NAMESPACE = "cattle-logging-system";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(crdService, objectMapper);
    }

    @Test
    void createClusterFlow_InvalidParameters() {
        // Test null parameters
        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterFlow(ENV, NAMESPACE, null);
        });

        // Test empty name
        ClusterFlowParameters parameters = new ClusterFlowParameters();
        parameters.setName("");
        parameters.setNamespaces(Arrays.asList("default"));
        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterFlow(ENV, NAMESPACE, parameters);
        });

        // Test empty namespaces
        parameters.setName("test-flow");
        parameters.setNamespaces(null);
        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterFlow(ENV, NAMESPACE, parameters);
        });
    }


    @Test
    void createClusterOutput_InvalidParameters() {
        // Test null parameters
        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterOutput(ENV, NAMESPACE, null);
        });

        // Test empty name
        ClusterOutputParameters parameters = new ClusterOutputParameters();
        parameters.setName("");
        parameters.setNamespace("cattle-logging-system");
        parameters.setDefaultTopic("test-topic");
        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterOutput(ENV, NAMESPACE, parameters);
        });

        // Test empty namespace
        parameters.setName("test-output");
        parameters.setNamespace("");
        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterOutput(ENV, NAMESPACE, parameters);
        });

        // Test empty defaultTopic
        parameters.setNamespace("cattle-logging-system");
        parameters.setDefaultTopic("");
        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterOutput(ENV, NAMESPACE, parameters);
        });
    }

    @Test
    void processClusterFlowTemplate_Success() {
        // Prepare test data
        String template = "apiVersion: logging.banzaicloud.io/v1beta1\n" +
                "kind: ClusterFlow\n" +
                "metadata:\n" +
                "  name: kube-system-logs\n" +
                "spec:\n" +
                "  match:\n" +
                "    - select:\n" +
                "        namespaces:\n" +
                "          - kube-system";
        ClusterFlowParameters parameters = new ClusterFlowParameters();
        parameters.setName("test-flow");
        parameters.setNamespaces(Arrays.asList("ns1", "ns2"));

        // Execute test
        String result = crdTemplateService.processClusterFlowTemplate(template, parameters);

        // Verify results
        assertNotNull(result);
        assertTrue(result.contains("name: test-flow"));
        assertTrue(result.contains("  - ns1"));
        assertTrue(result.contains("  - ns2"));
    }

    @Test
    void processClusterOutputTemplate_Success() {
        // Prepare test data
        String template = "apiVersion: logging.banzaicloud.io/v1beta1\n" +
                "kind: ClusterOutput\n" +
                "metadata:\n" +
                "  name: kafka-cluster-output\n" +
                "  namespace: cattle-logging-system\n" +
                "spec:\n" +
                "  kafka:\n" +
                "    default_topic: my-topic";
        ClusterOutputParameters parameters = new ClusterOutputParameters();
        parameters.setName("test-output");
        parameters.setNamespace("test-namespace");
        parameters.setDefaultTopic("test-topic");

        // Execute test
        String result = crdTemplateService.processClusterOutputTemplate(template, parameters);

        // Verify results
        assertNotNull(result);
        assertTrue(result.contains("name: test-output"));
        assertTrue(result.contains("namespace: test-namespace"));
        assertTrue(result.contains("default_topic: test-topic"));
    }

    @Test
    void validateClusterFlowParameters_Valid() {
        ClusterFlowParameters parameters = new ClusterFlowParameters();
        parameters.setName("test-flow");
        parameters.setNamespaces(Arrays.asList("default"));

        // Should not throw exception
        crdTemplateService.createClusterFlow(ENV, NAMESPACE, parameters);
    }

    @Test
    void validateClusterFlowParameters_NullParameters() {
        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterFlow(ENV, NAMESPACE, null);
        });
    }

    @Test
    void validateClusterFlowParameters_EmptyName() {
        ClusterFlowParameters parameters = new ClusterFlowParameters();
        parameters.setName("");
        parameters.setNamespaces(Arrays.asList("default"));

        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterFlow(ENV, NAMESPACE, parameters);
        });
    }

    @Test
    void validateClusterFlowParameters_EmptyNamespaces() {
        ClusterFlowParameters parameters = new ClusterFlowParameters();
        parameters.setName("test-flow");
        parameters.setNamespaces(null);

        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterFlow(ENV, NAMESPACE, parameters);
        });
    }

    @Test
    void validateClusterOutputParameters_Valid() {
        ClusterOutputParameters parameters = new ClusterOutputParameters();
        parameters.setName("test-output");
        parameters.setNamespace("test-namespace");
        parameters.setDefaultTopic("test-topic");

        // Should not throw exception
        crdTemplateService.createClusterOutput(ENV, NAMESPACE, parameters);
    }

    @Test
    void validateClusterOutputParameters_EmptyName() {
        ClusterOutputParameters parameters = new ClusterOutputParameters();
        parameters.setName("");
        parameters.setNamespace("test-namespace");
        parameters.setDefaultTopic("test-topic");

        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterOutput(ENV, NAMESPACE, parameters);
        });
    }

    @Test
    void validateClusterOutputParameters_EmptyNamespace() {
        ClusterOutputParameters parameters = new ClusterOutputParameters();
        parameters.setName("test-output");
        parameters.setNamespace("");
        parameters.setDefaultTopic("test-topic");

        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterOutput(ENV, NAMESPACE, parameters);
        });
    }

    @Test
    void validateClusterOutputParameters_EmptyDefaultTopic() {
        ClusterOutputParameters parameters = new ClusterOutputParameters();
        parameters.setName("test-output");
        parameters.setNamespace("test-namespace");
        parameters.setDefaultTopic("");

        assertThrows(IllegalArgumentException.class, () -> {
            crdTemplateService.createClusterOutput(ENV, NAMESPACE, parameters);
        });
    }
} 