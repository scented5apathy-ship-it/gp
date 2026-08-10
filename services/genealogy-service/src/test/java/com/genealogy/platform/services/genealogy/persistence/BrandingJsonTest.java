package com.genealogy.platform.services.genealogy.persistence;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrandingJsonTest {

    @Test
    void roundTripPreservesKeys() {
        Map<String, String> branding = new LinkedHashMap<>();
        branding.put("primaryColor", "#ff0000");
        branding.put("logoUrl", "https://cdn.example/logo.png");
        String json = BrandingJson.encode(branding);
        assertEquals(branding, BrandingJson.decode(json));
    }

    @Test
    void decodeEmptyObjectIsEmptyMap() {
        assertEquals(Map.of(), BrandingJson.decode("{}"));
        assertEquals(Map.of(), BrandingJson.decode(null));
        assertEquals(Map.of(), BrandingJson.decode(""));
    }

    @Test
    void decodeEscapesSpecialCharacters() {
        Map<String, String> branding = new LinkedHashMap<>();
        branding.put("tagline", "Hello \"world\"\nNew line");
        String json = BrandingJson.encode(branding);
        assertEquals(branding, BrandingJson.decode(json));
    }
}
