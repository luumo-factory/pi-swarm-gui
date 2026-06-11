package ai.luumo.tools.pi.piswarm.gui.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelRefTest {

    @Test
    void sortsByProviderThenModel() {
        List<ModelRef> models = new ArrayList<>(List.of(
                new ModelRef("openai", "gpt-4o", "GPT-4o"),
                new ModelRef("anthropic", "claude-opus", "Opus"),
                new ModelRef("anthropic", "claude-sonnet", "Sonnet"),
                new ModelRef("openai", "gpt-4o-mini", "GPT-4o mini")));

        models.sort(ModelRef.BY_PROVIDER_THEN_MODEL);

        assertEquals(List.of("Opus", "Sonnet", "GPT-4o", "GPT-4o mini"),
                models.stream().map(ModelRef::displayLabel).toList());
    }

    @Test
    void labelWithProviderPrefixesProvider() {
        assertEquals("anthropic / Sonnet",
                new ModelRef("anthropic", "claude-sonnet", "Sonnet").displayLabelWithProvider());
    }

    @Test
    void labelWithProviderDoesNotDoubleUpWhenLabelIsProvider() {
        // No id/name => displayLabel falls back to provider; don't render "x / x".
        assertEquals("local", new ModelRef("local", null, null).displayLabelWithProvider());
    }
}
