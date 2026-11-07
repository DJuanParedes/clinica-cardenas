package pe.clinicacardenas.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.GrantedAuthority;
import pe.clinicacardenas.portal.PatientAccount;
import pe.clinicacardenas.portal.PatientAccountRepository;
import pe.clinicacardenas.user.User;
import pe.clinicacardenas.user.UserRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;
    private final PatientAccountRepository patientAccounts;
    private final PermissionService permissions;

    public JwtAuthFilter(JwtService jwtService, UserRepository users, PatientAccountRepository patientAccounts,
                         PermissionService permissions) {
        this.jwtService = jwtService;
        this.users = users;
        this.patientAccounts = patientAccounts;
        this.permissions = permissions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtService.parse(token);
            if (claims != null && claims.getSubject() != null) {
                String subject = claims.getSubject();
                if (subject.startsWith(PatientAccount.SUBJECT_PREFIX)) {
                    // HU-22: token de la aplicación del paciente. Solo accede a /api/portal/**.
                    Long patientId = parseId(subject.substring(PatientAccount.SUBJECT_PREFIX.length()));
                    PatientAccount acc = patientId == null ? null : patientAccounts.findById(patientId).orElse(null);
                    if (acc != null && acc.active && acc.passwordHash != null) {
                        var auth = new UsernamePasswordAuthenticationToken(subject, token,
                                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE")));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } else {
                    // Se consulta el usuario en cada petición: si fue desactivado o cambió de rol/permisos, aplica al instante.
                    User u = users.findByUsernameIgnoreCase(subject).orElse(null);
                    if (u != null && u.active) {
                        List<GrantedAuthority> authorities = new ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + u.role.name()));
                        authorities.add(new SimpleGrantedAuthority("ROLE_PERSONAL"));
                        for (String p : permissions.forRole(u.role.name())) authorities.add(new SimpleGrantedAuthority(p));
                        var auth = new UsernamePasswordAuthenticationToken(u.username, token, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static Long parseId(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
