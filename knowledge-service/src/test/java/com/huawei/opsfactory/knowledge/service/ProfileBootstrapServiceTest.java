/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.opsfactory.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.opsfactory.knowledge.config.KnowledgeProperties;
import com.huawei.opsfactory.knowledge.repository.ProfileRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfileBootstrapServiceTest {

    private ProfileRepository profileRepository() {
        return mock(ProfileRepository.class);
    }

    private KnowledgeProperties properties() {
        return new KnowledgeProperties();
    }

    @Test
    void shouldCreateDefaultIndexProfileWhenNotExists() {
        ProfileRepository repo = profileRepository();
        when(repo.findIndexByName(ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME))
            .thenReturn(Optional.empty());
        when(repo.findRetrievalByName(ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME))
            .thenReturn(Optional.empty());

        new ProfileBootstrapService(repo, properties());

        verify(repo).insertIndex(any(ProfileRepository.ProfileRecord.class));
    }

    @Test
    void shouldCreateDefaultRetrievalProfileWhenNotExists() {
        ProfileRepository repo = profileRepository();
        when(repo.findIndexByName(ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME))
            .thenReturn(Optional.empty());
        when(repo.findRetrievalByName(ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME))
            .thenReturn(Optional.empty());

        new ProfileBootstrapService(repo, properties());

        verify(repo).insertRetrieval(any(ProfileRepository.ProfileRecord.class));
    }

    @Test
    void shouldRefreshExistingIndexProfile() {
        ProfileRepository repo = profileRepository();
        ProfileRepository.ProfileRecord existing = new ProfileRepository.ProfileRecord(
            "ip-1", ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME,
            java.util.Map.of(), "index", null, true, null,
            Instant.now(), Instant.now()
        );
        when(repo.findIndexByName(ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME))
            .thenReturn(Optional.of(existing));
        when(repo.findRetrievalByName(ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME))
            .thenReturn(Optional.empty());

        new ProfileBootstrapService(repo, properties());

        verify(repo).updateIndex(any(ProfileRepository.ProfileRecord.class));
    }

    @Test
    void shouldRefreshExistingRetrievalProfile() {
        ProfileRepository repo = profileRepository();
        ProfileRepository.ProfileRecord existing = new ProfileRepository.ProfileRecord(
            "rp-1", ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME,
            java.util.Map.of(), "retrieval", null, true, null,
            Instant.now(), Instant.now()
        );
        when(repo.findIndexByName(ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME))
            .thenReturn(Optional.empty());
        when(repo.findRetrievalByName(ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME))
            .thenReturn(Optional.of(existing));

        new ProfileBootstrapService(repo, properties());

        verify(repo).updateRetrieval(any(ProfileRepository.ProfileRecord.class));
    }

    @Test
    void shouldReturnDefaultIndexProfileId() {
        ProfileRepository repo = profileRepository();
        when(repo.findIndexByName(ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME))
            .thenReturn(Optional.empty());
        when(repo.findRetrievalByName(ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME))
            .thenReturn(Optional.empty());

        ProfileBootstrapService service = new ProfileBootstrapService(repo, properties());

        when(repo.findIndexByName(ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME))
            .thenReturn(Optional.of(new ProfileRepository.ProfileRecord(
                "ip-42", ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME,
                java.util.Map.of(), "index", null, true, null,
                Instant.now(), Instant.now()
            )));

        assertThat(service.defaultIndexProfileId()).isEqualTo("ip-42");
    }

    @Test
    void shouldReturnDefaultRetrievalProfileId() {
        ProfileRepository repo = profileRepository();
        when(repo.findIndexByName(ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME))
            .thenReturn(Optional.empty());
        when(repo.findRetrievalByName(ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME))
            .thenReturn(Optional.empty());

        ProfileBootstrapService service = new ProfileBootstrapService(repo, properties());

        when(repo.findRetrievalByName(ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME))
            .thenReturn(Optional.of(new ProfileRepository.ProfileRecord(
                "rp-99", ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME,
                java.util.Map.of(), "retrieval", null, true, null,
                Instant.now(), Instant.now()
            )));

        assertThat(service.defaultRetrievalProfileId()).isEqualTo("rp-99");
    }

    @Test
    void shouldReturnAllowedContentTypesFromProperties() {
        ProfileRepository repo = profileRepository();
        when(repo.findIndexByName(ProfileBootstrapService.DEFAULT_INDEX_PROFILE_NAME))
            .thenReturn(Optional.empty());
        when(repo.findRetrievalByName(ProfileBootstrapService.DEFAULT_RETRIEVAL_PROFILE_NAME))
            .thenReturn(Optional.empty());

        KnowledgeProperties props = properties();
        ProfileBootstrapService service = new ProfileBootstrapService(repo, props);

        assertThat(service.allowedContentTypes()).isEqualTo(props.getIngest().getAllowedContentTypes());
    }
}
