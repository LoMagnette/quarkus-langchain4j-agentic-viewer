package io.quarkiverse.langchain4j.agentic.viewer.runtime.dev.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.RecordComponentInfo;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.agentic.viewer.deployment.scan.InputSchemaGenerator;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SourceRef;
import io.quarkiverse.langchain4j.agentic.viewer.runtime.topology.SystemInput;

/**
 * A field or input marked {@code @Nullable} is optional in the run form of a builder system as in a declarative one:
 * declaration annotations, type-use annotations (jspecify), and the parameters of a typed builder interface.
 */
class NullableParityTest {

    /** Like jakarta.annotation.Nullable: a declaration annotation. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Nullable {
    }

    /** Like org.jspecify.annotations.Nullable: type use only. */
    interface TypeUse {
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE_USE)
        @interface Nullable {
        }
    }

    record Contact(String name, @Nullable String email, @TypeUse.Nullable String phone) {
    }

    /** A typed agent interface given to a builder: no agentic annotation. */
    public interface Notifier {
        String notify(@V("name") String name, @V("email") @Nullable String email, @V("phone") @TypeUse.Nullable String phone);
    }

    @Test
    void recordComponentsAreOptionalLikeAtBuildTime() throws Exception {
        Index index = Index.of(Contact.class, Nullable.class, TypeUse.Nullable.class);
        ClassInfo contact = index.getClassByName(DotName.createSimple(Contact.class.getName()));
        Map<String, Boolean> buildTime = contact.unsortedRecordComponents().stream().collect(Collectors.toMap(
                RecordComponentInfo::name, component -> InputSchemaGenerator.required(component.type(), component)));
        Map<String, Boolean> runtime = java.util.Arrays.stream(Contact.class.getRecordComponents()).collect(
                Collectors.toMap(java.lang.reflect.RecordComponent::getName,
                        component -> RuntimeInputSchema.required(component.getGenericType(), component)));

        assertEquals(Map.of("name", true, "email", false, "phone", false), buildTime);
        assertEquals(buildTime, runtime);
        assertEquals("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"},"
                + "\"phone\":{\"type\":\"string\"}},\"required\":[\"name\"]}", RuntimeInputSchema.schema(Contact.class));
    }

    @Test
    void parametersOfATypedBuilderInterfaceAreOptionalWhenNullable() {
        Notifier notifier = AgenticServices.sequenceBuilder(Notifier.class)
                .subAgents(AgenticServices.agentAction(scope -> scope.writeState("sent", true))).build();

        List<SystemInput> inputs = BuilderTopology.system("n", "n", new SourceRef("n", "n", null), (AgentInstance) notifier)
                .inputs();

        assertEquals(Map.of("name", true, "email", false, "phone", false),
                inputs.stream().collect(Collectors.toMap(SystemInput::name, SystemInput::required)));
    }
}
