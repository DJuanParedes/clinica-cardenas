package pe.clinicacardenas.assistant;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Página Mensajes: conversaciones de WhatsApp con los pacientes y configuración del asistente virtual. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAuthority('MENSAJES')")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping("/conversations")
    public Map<String, Object> threads(@RequestParam(required = false) String q) {
        return service.threads(q);
    }

    @GetMapping("/conversations/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @GetMapping("/conversations/{phone}")
    public Map<String, Object> conversation(@PathVariable String phone) {
        return service.conversation(phone);
    }

    @PostMapping("/conversations/{phone}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> send(@PathVariable String phone, @RequestBody ConversationService.SendRequest req) {
        return service.send(phone, req);
    }

    @PatchMapping("/conversations/{phone}")
    public Map<String, Object> update(@PathVariable String phone, @RequestBody ConversationService.ThreadUpdate req) {
        return service.updateThread(phone, req);
    }

    @GetMapping("/assistant/settings")
    public Map<String, Object> settings() {
        return service.settings();
    }

    @PutMapping("/assistant/settings")
    public Map<String, Object> updateSettings(@RequestBody ConversationService.SettingsRequest req) {
        return service.updateSettings(req);
    }

    @GetMapping("/assistant/faq")
    public List<Map<String, Object>> faq() {
        return service.faq();
    }

    @PostMapping("/assistant/faq")
    public List<Map<String, Object>> addFaq(@RequestBody ConversationService.FaqRequest req) {
        return service.addFaq(req);
    }

    @PutMapping("/assistant/faq/{id}")
    public List<Map<String, Object>> updateFaq(@PathVariable Long id, @RequestBody ConversationService.FaqRequest req) {
        return service.updateFaq(id, req);
    }

    @DeleteMapping("/assistant/faq/{id}")
    @PreAuthorize("hasAuthority('ELIMINAR_REGISTROS')")
    public List<Map<String, Object>> deleteFaq(@PathVariable Long id) {
        return service.deleteFaq(id);
    }

    @PostMapping("/assistant/test")
    public Map<String, Object> test(@RequestBody ConversationService.TestRequest req) {
        return service.test(req);
    }
}
