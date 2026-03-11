package com.certifytube.backend.service;

import com.certifytube.backend.model.Certificate;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.repository.CertificateRepository;
import com.certifytube.backend.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NameBackfillServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private CertificateRepository certificateRepository;

    @InjectMocks
    private NameBackfillService nameBackfillService;

    @Test
    void backfillNamesShouldNormalizeUsersAndMigrateOldCertificateLearnerNames() {
        UserAccount userMissingName = new UserAccount();
        userMissingName.setId(1L);
        userMissingName.setEmail("john@example.com");
        userMissingName.setName(" ");

        UserAccount userWithSpacedName = new UserAccount();
        userWithSpacedName.setId(2L);
        userWithSpacedName.setEmail("jane@example.com");
        userWithSpacedName.setName("  Jane Doe  ");

        Certificate certWithEmailLearnerName = new Certificate();
        certWithEmailLearnerName.setCertificateId("c1");
        certWithEmailLearnerName.setUserId(1L);
        certWithEmailLearnerName.setLearnerName("john@example.com");

        Certificate certWithBlankLearnerName = new Certificate();
        certWithBlankLearnerName.setCertificateId("c2");
        certWithBlankLearnerName.setUserId(2L);
        certWithBlankLearnerName.setLearnerName(" ");

        Certificate certWithValidLearnerName = new Certificate();
        certWithValidLearnerName.setCertificateId("c3");
        certWithValidLearnerName.setUserId(2L);
        certWithValidLearnerName.setLearnerName("Official Snapshot");

        when(userAccountRepository.findAll()).thenReturn(List.of(userMissingName, userWithSpacedName));
        when(certificateRepository.findAll()).thenReturn(List.of(
                certWithEmailLearnerName,
                certWithBlankLearnerName,
                certWithValidLearnerName
        ));
        when(userAccountRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(certificateRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        nameBackfillService.backfillNames();

        assertEquals("john", userMissingName.getName());
        assertEquals("Jane Doe", userWithSpacedName.getName());
        assertEquals("john", certWithEmailLearnerName.getLearnerName());
        assertEquals("Jane Doe", certWithBlankLearnerName.getLearnerName());
        assertEquals("Official Snapshot", certWithValidLearnerName.getLearnerName());

        ArgumentCaptor<List<UserAccount>> userUpdatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(userAccountRepository).saveAll(userUpdatesCaptor.capture());
        assertTrue(userUpdatesCaptor.getValue().size() >= 2);

        ArgumentCaptor<List<Certificate>> certUpdatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(certificateRepository).saveAll(certUpdatesCaptor.capture());
        assertEquals(2, certUpdatesCaptor.getValue().size());
    }
}
