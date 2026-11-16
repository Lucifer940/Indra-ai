package ai.indra.core.memory;

import ai.indra.core.model.MemoryCategory;
import ai.indra.core.model.MemoryItem;
import ai.indra.core.model.MemorySource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryEngineTest {

    @Test
    void detectsExplicitRememberRequest() {
        MemoryEngine.Candidate candidate = MemoryEngine.detectExplicit("Remember that I love momo and hate onions");
        assertNotNull(candidate);
        assertTrue(candidate.isExplicit());
        assertEquals("I love momo and hate onions", candidate.getContent());
        assertEquals(MemoryCategory.PREFERENCE, candidate.getCategory());
        assertEquals(5, candidate.getImportance());
    }

    @Test
    void detectsNepaliRememberRequest() {
        MemoryEngine.Candidate candidate = MemoryEngine.detectExplicit("सम्झनुस् मेरो जन्मदिन ५ मार्च हो");
        assertNotNull(candidate);
        assertTrue(candidate.getContent().contains("जन्मदिन"));
        assertEquals(MemoryCategory.PROFILE, candidate.getCategory());
    }

    @Test
    void ignoresNormalStatementsWithoutPrefix() {
        assertNull(MemoryEngine.detectExplicit("What is the weather today?"));
    }

    @Test
    void autoMemoryOnlyCapturesPersonalFacts() {
        assertNotNull(MemoryEngine.detectAutomatic("I live in Kathmandu"));
        assertNull(MemoryEngine.detectAutomatic("What is the capital of Nepal?"));
        assertNull(MemoryEngine.detectAutomatic("hi"));
    }

    @Test
    void classifiesCategories() {
        assertEquals(MemoryCategory.RELATIONSHIP, MemoryEngine.classify("My sister is a doctor"));
        assertEquals(MemoryCategory.HEALTH, MemoryEngine.classify("I am allergic to peanuts"));
        assertEquals(MemoryCategory.STUDY, MemoryEngine.classify("My exam is next month"));
        assertEquals(MemoryCategory.WORK, MemoryEngine.classify("I work at a bank in Pokhara"));
        assertEquals(MemoryCategory.CONTACT, MemoryEngine.classify("Mom's phone number is 9800000000"));
        assertEquals(MemoryCategory.OTHER, MemoryEngine.classify("The sky is blue"));
    }

    @Test
    void similarityDetectsDuplicates() {
        assertTrue(MemoryEngine.isSimilar("I love momo", "i love momo"));
        assertTrue(MemoryEngine.isSimilar("My favourite colour is blue", "my favorite colour is blue"));
        assertFalse(MemoryEngine.isSimilar("I love momo", "I work at a bank"));
    }

    @Test
    void rankingPrefersRelevantPinnedAndImportant() {
        String userId = "user-1";
        List<MemoryItem> items = new ArrayList<MemoryItem>();
        MemoryItem gym = MemoryItem.create(userId, "Goes to the gym every morning at 6", MemoryCategory.ROUTINE,
                MemorySource.USER_STATED, 3);
        MemoryItem momo = MemoryItem.create(userId, "Loves momo with buff filling", MemoryCategory.PREFERENCE,
                MemorySource.USER_STATED, 3);
        MemoryItem allergy = MemoryItem.create(userId, "Allergic to peanuts", MemoryCategory.HEALTH,
                MemorySource.USER_STATED, 5);
        allergy.setPinned(true);
        items.add(gym);
        items.add(momo);
        items.add(allergy);

        List<MemoryItem> ranked = MemoryEngine.rank(items, "what should I eat for dinner momo?", 2);
        assertEquals(2, ranked.size());
        assertEquals("Loves momo with buff filling", ranked.get(0).getContent());
        assertTrue(ranked.contains(allergy));
    }

    @Test
    void promptFormattingSkipsDeletedEntries() {
        MemoryItem kept = MemoryItem.create("u", "Likes tea", MemoryCategory.PREFERENCE, MemorySource.USER_STATED, 3);
        MemoryItem deleted = MemoryItem.create("u", "Old address", MemoryCategory.PROFILE, MemorySource.USER_STATED, 3);
        deleted.setDeletedAt(System.currentTimeMillis());
        List<MemoryItem> items = new ArrayList<MemoryItem>();
        items.add(kept);
        items.add(deleted);
        String block = MemoryEngine.formatForPrompt(items, 10);
        assertTrue(block.contains("Likes tea"));
        assertFalse(block.contains("Old address"));
    }

    @Test
    void forgetTopicIsExtracted() {
        assertEquals("I love momo", MemoryEngine.detectForgetTopic("forget that I love momo"));
        assertNull(MemoryEngine.detectForgetTopic("what is the time"));
    }

    @Test
    void findDuplicateMatchesExistingMemory() {
        List<MemoryItem> items = new ArrayList<MemoryItem>();
        items.add(MemoryItem.create("u", "I love momo", MemoryCategory.PREFERENCE, MemorySource.USER_STATED, 3));
        assertNotNull(MemoryEngine.findDuplicate(items, "i love momo"));
        assertNull(MemoryEngine.findDuplicate(items, "I hate traffic"));
    }

    @Test
    void tokensWorkForDevanagari() {
        assertTrue(MemoryEngine.tokens("मलाई मन पर्छ").contains("मलाई"));
    }
}
