package com.wallet.hackaton.quarkus.app.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InMemoryAccountRepositoryTest {
    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();

    @Test
    void returnsSameAccountForSameProviderAndSubject() {
        GoogleIdentity first = new GoogleIdentity("subject-1", "same@example.test", "First Name");
        GoogleIdentity updatedProfile = new GoogleIdentity("subject-1", "changed@example.test", "Updated Name");

        Account initial = repository.findOrCreate("google", first);
        Account repeated = repository.findOrCreate("google", updatedProfile);

        assertEquals(initial.id(), repeated.id());
        assertEquals("same@example.test", repeated.email());
    }

    @Test
    void doesNotUseEmailAsIdentityKey() {
        Account first = repository.findOrCreate(
                "google", new GoogleIdentity("subject-1", "same@example.test", "First"));
        Account second = repository.findOrCreate(
                "google", new GoogleIdentity("subject-2", "same@example.test", "Second"));

        assertNotEquals(first.id(), second.id());
    }
}
