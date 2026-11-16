package ai.indra.core.ai;

import ai.indra.core.model.BrainProfile;
import ai.indra.core.model.ChatMessage;
import ai.indra.core.model.Language;
import ai.indra.core.model.MemoryCategory;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.MessageRole;
import ai.indra.core.model.Persona;
import ai.indra.core.model.UserSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiBrainTest {

    @Test
    void systemPromptCarriesIdentityPersonaBrainAndMemory() {
        PersonalityEngine.Context context = new PersonalityEngine.Context()
                .setUserName("Umesh")
                .setTimeZone("Asia/Kathmandu")
                .setLocalTime("Sun 16 Nov, 09:00")
                .setMemoriesBlock("- [preference] loves momo\n");
        String prompt = PersonalityEngine.systemPrompt(Persona.FUNNY, BrainProfile.STRATEGIC, Language.ENGLISH, context);
        assertTrue(prompt.contains("INDRA AI"));
        assertTrue(prompt.contains("Umesh Chaudhary"));
        assertTrue(prompt.contains("PERSONALITY: funny"));
        assertTrue(prompt.contains("STRATEGIC"));
        assertTrue(prompt.contains("loves momo"));
        assertTrue(prompt.contains("Umesh"));
        assertFalse(prompt.contains("As an AI language model"));
    }

    @Test
    void nepaliPromptRequestsDevanagari() {
        String prompt = PersonalityEngine.systemPrompt(Persona.FRIENDLY, BrainProfile.CORE, Language.NEPALI,
                new PersonalityEngine.Context());
        assertTrue(prompt.contains("Nepali"));
        assertTrue(prompt.contains("Devanagari"));
    }

    @Test
    void promptBuilderIncludesHistoryAndMemoriesButStaysBounded() {
        UserSettings settings = UserSettings.defaults("u1");
        settings.setPersonaId("professional");
        settings.setBrainId("analytic");
        List<ChatMessage> history = new ArrayList<ChatMessage>();
        for (int i = 0; i < 40; i++) {
            history.add(ChatMessage.of("conv", "u1",
                    i % 2 == 0 ? MessageRole.USER : MessageRole.ASSISTANT, "message " + i));
        }
        List<MemoryItem> memories = new ArrayList<MemoryItem>();
        memories.add(MemoryItem.create("u1", "Works as a software engineer", MemoryCategory.WORK,
                ai.indra.core.model.MemorySource.USER_STATED, 4));

        ChatRequest request = PromptBuilder.build(settings, "Umesh", history, memories,
                new ArrayList<ai.indra.core.model.TaskItem>(), "what should I study tonight", "Asia/Kathmandu", false);

        assertTrue(request.getMessages().size() <= 14);
        AiMessage system = request.getMessages().get(0);
        assertEquals("system", system.getRole());
        assertTrue(system.getContent().contains("software engineer"));
        AiMessage last = request.getMessages().get(request.getMessages().size() - 1);
        assertEquals("user", last.getRole());
        assertEquals("what should I study tonight", last.getContent());
        assertEquals("professional", request.getPersonaId());
        assertEquals("analytic", request.getBrainId());
    }

    @Test
    void offlineProviderIsHonestAboutMissingConfiguration() {
        ChatRequest request = new ChatRequest().user("Explain photosynthesis");
        ChatResult result = new OfflineProvider().complete(request);
        assertTrue(result.isOffline());
        assertTrue(result.getText().toLowerCase().contains("offline"));
        assertFalse(result.getText().toLowerCase().contains("chlorophyll"));
    }

    @Test
    void offlineProviderAnswersTimeQuestions() {
        ChatResult result = new OfflineProvider().complete(new ChatRequest().user("what time is it"));
        assertTrue(result.getText().toLowerCase().contains("it's"));
    }

    @Test
    void providerCatalogKnowsProviders() {
        assertEquals("openai", ProviderCatalog.get("openai").getId());
        assertTrue(ProviderCatalog.get("deepseek").isRequiresKey());
        assertFalse(ProviderCatalog.get("ollama").isRequiresKey());
        assertFalse(ProviderCatalog.get("google").getDefaultModel().isEmpty());
        // Unknown ids degrade to the offline provider instead of throwing.
        assertEquals("offline", ProviderCatalog.get("unknown-provider").getId());
        assertTrue(ProviderCatalog.keyProviders().size() >= 6);
    }

    /** Fake provider used to exercise rotation and failover. */
    static final class FakeProvider implements AiProvider {
        private final String id;
        private final boolean fails;
        private final String text;

        FakeProvider(String id, boolean fails, String text) {
            this.id = id;
            this.fails = fails;
            this.text = text;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public ChatResult complete(ChatRequest request) throws AiProviderException {
            if (fails) {
                throw new AiProviderException(AiProviderException.Kind.RATE_LIMITED, "quota exceeded", 429, null);
            }
            return new ChatResult(text, id, "fake-model", 5L, 10, 20, false);
        }

        @Override
        public void stream(ChatRequest request, TokenListener listener) throws AiProviderException {
            ChatResult result = complete(request);
            listener.onToken(result.getText());
            listener.onComplete(result.getText());
        }
    }

    static final class FakeResolver implements KeyResolver {
        final List<String> failures = new ArrayList<String>();

        @Override
        public List<String> keysFor(String ownerId, String providerId) {
            if ("openai".equals(providerId)) {
                return Arrays.asList("key-a", "key-b");
            }
            return new ArrayList<String>();
        }

        @Override
        public boolean hasKeys(String ownerId, String providerId) {
            return "openai".equals(providerId);
        }

        @Override
        public void reportFailure(String ownerId, String providerId, String key) {
            failures.add(providerId + ":" + key);
        }

        @Override
        public void reportSuccess(String ownerId, String providerId, String key) {
            // no-op for the test
        }

        @Override
        public boolean isKeyless(String providerId) {
            return false;
        }
    }

    @Test
    void orchestratorRotatesToSecondKeyAfterFailure() throws Exception {
        final FakeResolver resolver = new FakeResolver();
        ProviderFactory factory = new ProviderFactory() {
            @Override
            public AiProvider create(String providerId, String apiKey, String baseUrl) {
                if (!"openai".equals(providerId)) {
                    return null;
                }
                return new FakeProvider(providerId, "key-a".equals(apiKey), "answer from " + apiKey);
            }
        };
        ChatOrchestrator orchestrator = new ChatOrchestrator(factory, resolver);
        ChatResult result = orchestrator.send("u1", new ChatRequest().setProvider("openai").user("hello"));
        assertEquals("answer from key-b", result.getText());
        assertEquals(1, resolver.failures.size());
        assertEquals("openai:key-a", resolver.failures.get(0));
    }

    @Test
    void orchestratorFallsBackToOfflineBrainWhenNothingIsConfigured() throws Exception {
        KeyResolver emptyResolver = new KeyResolver() {
            @Override
            public List<String> keysFor(String ownerId, String providerId) {
                return new ArrayList<String>();
            }

            @Override
            public boolean hasKeys(String ownerId, String providerId) {
                return false;
            }

            @Override
            public void reportFailure(String ownerId, String providerId, String key) {
            }

            @Override
            public void reportSuccess(String ownerId, String providerId, String key) {
            }

            @Override
            public boolean isKeyless(String providerId) {
                return false;
            }
        };
        ProviderFactory noopFactory = new ProviderFactory() {
            @Override
            public AiProvider create(String providerId, String apiKey, String baseUrl) {
                return "offline".equals(providerId) ? new OfflineProvider() : null;
            }
        };
        ChatOrchestrator orchestrator = new ChatOrchestrator(noopFactory, emptyResolver);
        ChatResult result = orchestrator.send("u1", new ChatRequest().setProvider("openai").user("hello"));
        assertTrue(result.isOffline());
        assertNotNull(result.getText());
    }

    @Test
    void orchestratorThrowsWhenEverythingFails() {
        KeyResolver resolver = new FakeResolver();
        ProviderFactory failing = new ProviderFactory() {
            @Override
            public AiProvider create(String providerId, String apiKey, String baseUrl) {
                return new FakeProvider(providerId, true, "");
            }
        };
        ChatOrchestrator orchestrator = new ChatOrchestrator(failing, resolver);
        assertThrows(AiProviderException.class,
                () -> orchestrator.send("u1", new ChatRequest().setProvider("openai").user("hi")));
    }
}
