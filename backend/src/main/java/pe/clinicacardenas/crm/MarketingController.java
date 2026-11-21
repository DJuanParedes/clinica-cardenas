package pe.clinicacardenas.crm;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.clinicacardenas.common.Dates;
import pe.clinicacardenas.patient.Patient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MarketingController {

    private final MarketingService marketing;
    private final CampaignService campaigns;

    public MarketingController(MarketingService marketing, CampaignService campaigns) {
        this.marketing = marketing;
        this.campaigns = campaigns;
    }

    // segmentos
    @PostMapping("/marketing/segments/preview")
    @PreAuthorize("hasAnyAuthority('MARKETING', 'TAREAS')")
    public Map<String, Object> preview(@RequestBody SegmentFilter f) { return marketing.preview(f); }

    @GetMapping("/marketing/segments")
    @PreAuthorize("hasAuthority('MARKETING')")
    public List<Map<String, Object>> segments() { return marketing.segments(); }

    @PostMapping("/marketing/segments")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> create(@RequestBody MarketingService.SegmentRequest r) { return marketing.saveSegment(null, r); }

    @PutMapping("/marketing/segments/{id}")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody MarketingService.SegmentRequest r) { return marketing.saveSegment(id, r); }

    @DeleteMapping("/marketing/segments/{id}")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> delete(@PathVariable Long id) { marketing.deleteSegment(id); return Map.of("ok", true); }

    @PostMapping("/marketing/segments/tasks")
    @PreAuthorize("hasAuthority('MARKETING') and hasAuthority('TAREAS')")
    public Map<String, Object> bulkTasks(@RequestBody MarketingService.BulkTaskRequest r) { return marketing.bulkTasks(r); }

    // cumpleaños
    @GetMapping("/marketing/birthdays")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> birthdays(@RequestParam(required = false) Integer month) {
        int m = month == null ? Dates.today().getMonthValue() : month;
        return Map.of("month", m, "patients", marketing.birthdays(m), "settings", marketing.birthdaySettings());
    }

    @PutMapping("/marketing/birthdays/settings")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> birthdaySettings(@RequestBody MarketingService.BirthdaySettings s) { return marketing.updateBirthdaySettings(s); }

    @PostMapping("/marketing/birthdays/run")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> runBirthdays() { return marketing.runBirthdays(true); }

    // campañas
    @GetMapping("/marketing/campaigns")
    @PreAuthorize("hasAuthority('MARKETING')")
    public List<Map<String, Object>> campaigns() { return campaigns.list(); }

    @GetMapping("/marketing/campaigns/{id}")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> campaign(@PathVariable Long id) { return campaigns.get(id); }

    @PostMapping("/marketing/campaigns")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> createCampaign(@RequestBody CampaignService.CampaignRequest r) { return campaigns.create(r); }

    @PutMapping("/marketing/campaigns/{id}")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> updateCampaign(@PathVariable Long id, @RequestBody CampaignService.CampaignRequest r) { return campaigns.update(id, r); }

    @PostMapping("/marketing/campaigns/{id}/schedule")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> schedule(@PathVariable Long id, @RequestBody(required = false) CampaignService.ScheduleRequest r) { return campaigns.schedule(id, r); }

    @PostMapping("/marketing/campaigns/{id}/cancel")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> cancel(@PathVariable Long id) { return campaigns.cancel(id); }

    @PostMapping("/marketing/campaigns/preview")
    @PreAuthorize("hasAuthority('MARKETING')")
    public Map<String, Object> previewCampaign(@RequestBody CampaignService.PreviewRequest r) { return campaigns.preview(r); }

    // consentimiento de publicidad del paciente
    @PutMapping("/patients/{id}/marketing-consent")
    @PreAuthorize("hasAuthority('PACIENTES_EDITAR')")
    public Patient consent(@PathVariable Long id, @RequestBody MarketingService.ConsentRequest r) { return marketing.setConsent(id, r); }
}
