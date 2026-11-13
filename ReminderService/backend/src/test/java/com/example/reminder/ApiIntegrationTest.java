package com.example.reminder;

import com.example.reminder.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    private static final String PASSWORD="Test-only-password-42!";
    private String email() { return UUID.randomUUID()+"@example.test"; }
    private ResponseEntity<JsonNode> request(HttpMethod method,String path,Object body,String token) {
        HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(token!=null)h.setBearerAuth(token);
        return http.exchange(path,method,new HttpEntity<>(body,h),JsonNode.class);
    }
    private String account(String email) {
        var registration=request(HttpMethod.POST,"/api/auth/register",Map.of("email",email,"password",PASSWORD,"displayName","Learner","timeZone","Asia/Kolkata"),null);
        assertEquals(201,registration.getStatusCode().value());assertFalse(registration.getBody().has("passwordHash"));
        assertTrue(users.findByEmail(email).orElseThrow().getPasswordHash().startsWith("$2"));
        var login=request(HttpMethod.POST,"/api/auth/login",Map.of("email",email,"password",PASSWORD),null);
        assertEquals(200,login.getStatusCode().value());return login.getBody().path("accessToken").asText();
    }
    private Map<String,Object> reminder(String title,long version) {
        return Map.of("title",title,"recipientEmail","recipient@example.test","recipientName","Learner","message","A test reminder",
                "scheduledAt",Instant.now().plusSeconds(3600).toString(),"timeZone","Asia/Kolkata","version",version);
    }
    @Test void fullCrudOwnershipHistoryDashboardProfileAndRevocation() {
        String email=email(), token=account(email), other=account(email());
        assertEquals(401,request(HttpMethod.GET,"/api/reminders",null,null).getStatusCode().value());
        assertEquals(401,request(HttpMethod.GET,"/api/reminders",null,token+"broken").getStatusCode().value());
        var created=request(HttpMethod.POST,"/api/reminders",reminder("Initial",0),token);
        assertEquals(201,created.getStatusCode().value());long id=created.getBody().path("id").asLong();
        String url="/api/reminders/"+id;
        assertEquals(200,request(HttpMethod.GET,url,null,token).getStatusCode().value());
        assertEquals(404,request(HttpMethod.GET,url,null,other).getStatusCode().value());
        assertEquals(404,request(HttpMethod.PUT,url,reminder("Stolen",0),other).getStatusCode().value());
        assertEquals(404,request(HttpMethod.DELETE,url,null,other).getStatusCode().value());
        assertEquals(1,request(HttpMethod.GET,"/api/reminders",null,token).getBody().path("totalElements").asInt());
        assertEquals(409,request(HttpMethod.DELETE,url,null,token).getStatusCode().value());
        var updated=request(HttpMethod.PUT,url,reminder("Changed",0),token);
        assertEquals(200,updated.getStatusCode().value());assertEquals("Changed",updated.getBody().path("title").asText());
        assertEquals(409,request(HttpMethod.PUT,url,reminder("Stale",0),token).getStatusCode().value());
        assertEquals(0,request(HttpMethod.GET,"/api/email-logs/"+id,null,token).getBody().path("totalElements").asInt());
        assertEquals(404,request(HttpMethod.GET,"/api/email-logs/"+id,null,other).getStatusCode().value());
        assertEquals(200,request(HttpMethod.GET,"/api/reminders/history",null,token).getStatusCode().value());
        assertEquals(1,request(HttpMethod.GET,"/api/dashboard",null,token).getBody().path("pending").asInt());
        assertEquals(200,request(HttpMethod.POST,url+"/cancel",null,token).getStatusCode().value());
        assertEquals(204,request(HttpMethod.DELETE,url,null,token).getStatusCode().value());
        assertEquals(404,request(HttpMethod.GET,url,null,token).getStatusCode().value());
        var profile=request(HttpMethod.PUT,"/api/profile",Map.of("displayName","Updated learner","timeZone","Europe/London"),token);
        assertEquals(200,profile.getStatusCode().value());assertEquals("Updated learner",profile.getBody().path("displayName").asText());
        assertEquals("Europe/London",request(HttpMethod.GET,"/api/profile",null,token).getBody().path("timeZone").asText());
        assertEquals(204,request(HttpMethod.POST,"/api/auth/logout",null,token).getStatusCode().value());
        assertEquals(401,request(HttpMethod.GET,"/api/profile",null,token).getStatusCode().value());
        assertEquals(200,request(HttpMethod.POST,"/api/auth/login",Map.of("email",email,"password",PASSWORD),null).getStatusCode().value());
    }
    @Test void rejectsBadRegistrationLoginAndSchedulingInputs() {
        assertEquals(400,request(HttpMethod.POST,"/api/auth/register",Map.of("email","bad","password","tiny","displayName","","timeZone","bad"),null).getStatusCode().value());
        String email=email(),token=account(email);
        assertEquals(409,request(HttpMethod.POST,"/api/auth/register",Map.of("email",email,"password",PASSWORD,"displayName","Again","timeZone","UTC"),null).getStatusCode().value());
        assertEquals(401,request(HttpMethod.POST,"/api/auth/login",Map.of("email",email,"password","wrong-password"),null).getStatusCode().value());
        var past=new HashMap<>(reminder("Past",0));past.put("scheduledAt",Instant.now().minusSeconds(60).toString());
        assertEquals(400,request(HttpMethod.POST,"/api/reminders",past,token).getStatusCode().value());
        var invalid=new HashMap<>(reminder("Invalid",0));invalid.put("recipientEmail","not-email");
        assertEquals(400,request(HttpMethod.POST,"/api/reminders",invalid,token).getStatusCode().value());
        assertEquals(400,request(HttpMethod.GET,"/api/reminders?size=1000",null,token).getStatusCode().value());
    }
    @Test void rejectsExpiredTokensAndUnexpectedIssuers() {
        String email=email();account(email);long id=users.findByEmail(email).orElseThrow().getId();
        for(boolean expired:List.of(true,false)) {
            Instant now=Instant.now();
            var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                    .issuer(expired?"smart-reminder":"untrusted-issuer").subject(Long.toString(id))
                    .issuedAt(now.minusSeconds(7200)).expiresAt(expired?now.minusSeconds(3600):now.plusSeconds(900)).claim("v",0).build();
            var header=org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
            String token=encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header,claims)).getTokenValue();
            assertEquals(401,request(HttpMethod.GET,"/api/profile",null,token).getStatusCode().value());
        }
    }
    @Test void permitsLoopbackFrontendPreflightWithoutAuthentication() {
        HttpHeaders h=new HttpHeaders();h.setOrigin("http://127.0.0.1:5173");h.setAccessControlRequestMethod(HttpMethod.POST);
        var response=http.exchange("/api/reminders",HttpMethod.OPTIONS,new HttpEntity<>(h),String.class);
        assertEquals(200,response.getStatusCode().value());assertEquals("http://127.0.0.1:5173",response.getHeaders().getAccessControlAllowOrigin());
    }
}
