package com.ptmind.ptone.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.*;
import com.ptmind.ptone.model.EventTriggerCallback;
import com.ptmind.ptone.model.OAuthCallback;
import com.sendpulse.restapi.Sendpulse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class SendPulseIntegration {

    private Logger logger = LoggerFactory.getLogger(SendPulseIntegration.class);

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Value("${sendpulse.client_id}")
    String clientId;
    @Value("${sendpulse.client_secret}")
    String clientSecret;

    @Value("${sendpulse.sender_email}")
    String senderEmail;
    @Value("${sendpulse.sender_name}")
    String senderName;

    private Map<String, Object> getSendPulseTemplates() {
        Sendpulse sendpulse = new Sendpulse(clientId, clientSecret);

        Map<String, Object> result = null;
        try {
            result = sendpulse.sendRequest("/templates", "GET", null, true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private Map<String, Object> getSingleSendPulseTemplate(String templateId) {
        Sendpulse sendpulse = new Sendpulse(clientId, clientSecret);

        Map<String, Object> result = null;

        try {
            result = sendpulse.sendRequest("/template/" + templateId, "GET", null, true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    private static Map<String, Properties> mailTemplateMap = new HashMap<>();

    public static Properties getTemplate(String templateName) {
        Properties template = null;

        if (mailTemplateMap.containsKey(templateName)) {
            template = mailTemplateMap.get(templateName);
        } else {
            template = new Properties();
            template = loadPropertiesContent("emails/" + templateName);
            mailTemplateMap.put(templateName, template);
        }

        return template;
    }

    public static synchronized Properties loadPropertiesContent(String configFile) {
        Properties prop = new Properties();
        try {
            InputStreamReader input =
                    new InputStreamReader(
                            SendPulseIntegration.class.getClassLoader().getResourceAsStream(configFile), "UTF-8");
            if (input != null) {
                prop.load(input);

                System.out.println(">>> Load " + configFile + " ... ");
                for (Map.Entry<Object, Object> entry : prop.entrySet()) {
                    System.out.println("\t" + entry.getKey() + ":" + entry.getValue());
                }
            } else {
                System.out.println("Config.properties does not exist");
            }
        } catch (Exception e) {
            System.out.println("Config.properties loading fails");
            e.printStackTrace();
        }

        return prop;
    }

    private void triggerSPEvent() {
        String token = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        logger.info(headers.toString());

        Map<String, String> postBody = new HashMap<>();
        postBody.put("email", "ken.yap@ptmind.com");
        postBody.put("phone", "+60108954294");
        postBody.put("user_id", "01234567890");
        postBody.put("event_date", "2018.10.12");
        postBody.put("inviter", "Kenny Yao");
        postBody.put("something", "Something here");
        postBody.put("testing", "Test Var");
        postBody.put("testing_one", "Test Var 1");
        postBody.put("testing_three", "Test Var 3");
        postBody.put("user_name", "Ken Yap!");

        HttpEntity<Map<String, String>> requestBody = new HttpEntity<>(postBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        // parse the output
        EventTriggerCallback result = restTemplate.postForObject("https://events.sendpulse.com/events/name/ken_testing_event", requestBody, EventTriggerCallback.class);

        if (result.getResult()) {
            logger.info("Event Trigger Successful!");
        } else {
            logger.info("Event Trigger Unsuccessful!");
        }
    }

    private String getAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> postBody = new HashMap<>();
        postBody.put("grant_type", "client_credentials");
        postBody.put("client_id", clientId);
        postBody.put("client_secret", clientSecret);

        HttpEntity<Map<String, String>> requestBody = new HttpEntity<>(postBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        OAuthCallback result = restTemplate.postForObject("https://api.sendpulse.com/oauth/access_token", requestBody, OAuthCallback.class);

        String token = result.getAccessToken();

        return token;
    }

    @RequestMapping(value = "/sendemail", method = RequestMethod.GET)
    public String setSendPulseCreds() {
        Sendpulse sendpulse = new Sendpulse(clientId, clientSecret);

        System.out.println("Client ID: " + clientId);
        System.out.println("Client Secret: " + clientSecret);

        Map<String, Object> attachments = new HashMap<>();
        // unsure about properties
        Properties properties = getTemplate("test-email.properties");
        // remove properties for entire HTML only
        properties.remove("en_US.title");

        String html = "<h1>Something here</h1>" +
                "<p>Something else here</p>" + UUID.randomUUID() + " : " + UUID.randomUUID();

        StringWriter writer = new StringWriter();

        try {
            properties.store(writer, "");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // convert entire properties into a string, replacing backslashes(\) with empty string for HTML parsing
        String testing = writer.getBuffer().toString();
        String finalTesting = testing.substring(testing.indexOf("<html>"), testing.length());
        finalTesting = finalTesting.replaceAll("\\\\", "");
        // replace variables declared with own variables

        String templateId = "";
        try {
            templateId = getSendpulseTemplateIdByName("Ken - Test Variables");
        } catch (Exception e) {
            e.printStackTrace();
        }

        String encodedBody = getTemplateBody(templateId);

        List<String> variables = getVariablesFromTemplate(encodedBody);

//        triggerSPEvent();

//        smtpSendMail(sendpulse, senderName, senderEmail, "Kenny Yap", "kyapwc@gmail.com", finalTesting, "Some Stuff Here", "Testing Java SMTP", attachments);

        return "Something";
    }

    private List<String> getVariablesFromTemplate(String encodedBody) {
        Map<String, String> resultMap = new HashMap<>();
        List<String> variablesList = new ArrayList<String>();

        byte[] decodedBody = Base64.getDecoder().decode(encodedBody);

        CharSequence templateChars = new String(decodedBody, StandardCharsets.UTF_8);

        // catch all {{varName}} and then change it later.
//        String regexCamelCase = "\\{\\{([a-z]*)\\}\\}";
        String regexSnakeCase = "\\{\\{([a-z_]*)\\}\\}";
        Pattern pattern = Pattern.compile(regexSnakeCase);
        Matcher matcher = pattern.matcher(templateChars);

        while (matcher.find()) {
            String matchedVar = matcher.group();

            int firstParentMatch = matchedVar.indexOf("{", matchedVar.indexOf("{") +1);
            int thirdParenthesis = matchedVar.indexOf("}");
            String variableName = matchedVar.substring(firstParentMatch + 1, thirdParenthesis);

            resultMap.put(variableName, matchedVar);

            variablesList.add(variableName);
        }

        return variablesList;
    }

    private String getTemplateBody(String templateId) {
        Map<String, Object> template = getSingleSendPulseTemplate(templateId);
        Object data = template.get("data");
        JsonElement tempElement = gson.toJsonTree(data);

        JsonObject convertedTemp = (JsonObject) tempElement;

        JsonObject tempMap = convertedTemp.get("map").getAsJsonObject();
        String templateBody = tempMap.get("body").getAsString();

        return templateBody;
    }

    private String getSendpulseTemplateIdByName(String templateName) throws Exception {

        Map<String, Object> result = getSendPulseTemplates();

        Object data = result.get("data");

        // convert Object to JSONObject
        JsonElement dataElement = gson.toJsonTree(data);

        JsonObject convertedData = (JsonObject) dataElement;

        JsonArray templateArray = convertedData.getAsJsonArray("myArrayList");

        String templateId = "";
        for (JsonElement template : templateArray) {
            JsonObject templateObj = template.getAsJsonObject();
            JsonObject templateMap = templateObj.getAsJsonObject("map");

            if (templateMap.get("name").getAsString().equals(templateName)) {
                templateId = templateMap.get("id").getAsString();
            }
        }

        if (templateId == "" || templateId == null) {
            logger.error("Failed to get specified template from sendpulse");
            throw new Exception("Failed to get specified template with name of: " + templateName);
        }

        return templateId;
    }

    /**
     * @param sendpulse
     * @param from_name
     * @param from_email
     * @param name_to
     * @param email_to
     * @param html
     * @param text
     * @param subject
     * @param attachments
     */
    // TODO: Load different templates and set diff functions for diff things, along with name, or any other variables that are necessary;
    public static void smtpSendMail(Sendpulse sendpulse, String from_name, String from_email, String name_to, String email_to, String html, String text, String subject, Map<String, Object> attachments) {
        Map<String, Object> fromDetails = new HashMap<>();
        fromDetails.put("name", from_name);
        fromDetails.put("email", from_email);

        ArrayList<Map> toDetails = new ArrayList<>();
        Map<String, Object> elementTo = new HashMap<>();
        elementTo.put("name", name_to);
        elementTo.put("email", email_to);

        toDetails.add(elementTo);

        Map<String, Object> emailData = new HashMap<>();
        emailData.put("html", html);
        emailData.put("text", text);
        emailData.put("subject", subject);
        emailData.put("from", fromDetails);
        emailData.put("to", toDetails);

        if (!attachments.equals(null) && attachments.size() > 0) {
            emailData.put("attachments_binary", attachments);
        }

        Map<String, Object> result = (Map<String, Object>) sendpulse.smtpSendMail(emailData);
        System.out.println("Results: " + result);
    }
}
