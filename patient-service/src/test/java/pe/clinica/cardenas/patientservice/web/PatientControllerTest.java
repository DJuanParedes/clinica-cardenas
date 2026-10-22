package pe.clinica.cardenas.patientservice.web;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import pe.clinica.cardenas.patientservice.domain.*;
import pe.clinica.cardenas.patientservice.repository.PatientRepository;
import pe.clinica.cardenas.patientservice.service.AuditClient;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatientControllerTest {
    PatientRepository repo=mock(PatientRepository.class);AuditClient audit=mock(AuditClient.class);
    PatientController controller=new PatientController(repo,audit);
    Patient patient=Patient.builder().id(1L).fullName("María Prueba").dni("90000001").phone("900000001").build();
    @Test void searchesDocumentNameAndPhone(){when(repo.findAll()).thenReturn(List.of(patient));for(String term:List.of("90000001","maria","900000001"))assertEquals(List.of(patient),controller.search(term));assertTrue(controller.search("no-existe").isEmpty());}
    @Test void duplicateDocumentIsAConflictAndDoesNotSave(){when(repo.existsByDni("90000001")).thenReturn(true);var ex=assertThrows(ResponseStatusException.class,()->controller.create(patient,null,new MockHttpServletRequest()));assertEquals(409,ex.getStatusCode().value());verify(repo,never()).saveAndFlush(any());}
    @Test void concurrentDuplicateIsAlsoAConflict(){when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));var ex=assertThrows(ResponseStatusException.class,()->controller.create(patient,null,new MockHttpServletRequest()));assertEquals(409,ex.getStatusCode().value());verifyNoInteractions(audit);}
    @Test void newPatientIsPersistedAndAudited(){when(repo.saveAndFlush(any())).thenAnswer(i->{Patient p=i.getArgument(0);p.setId(2L);return p;});var saved=controller.create(patient,null,new MockHttpServletRequest());assertEquals(Status.ACTIVO,saved.getStatus());assertEquals(2L,saved.getId());verify(audit).log(eq("2"),eq("CREAR"),isNull(),eq(saved),anyString(),isNull());}
    @Test void documentCannotBeMissing(){try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){patient.setDni(null);assertFalse(factory.getValidator().validate(patient).isEmpty());}}
}
