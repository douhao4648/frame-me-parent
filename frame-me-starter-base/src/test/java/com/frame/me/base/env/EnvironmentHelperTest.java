package com.frame.me.base.env;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EnvironmentHelper 单元测试.
 */
class EnvironmentHelperTest {

    @Test
    void shouldReturnDefaultProfileWhenNoneActive() {
        MockEnvironment environment = new MockEnvironment();
        EnvironmentHelper helper = new EnvironmentHelper(environment);

        assertEquals("default", helper.getActiveProfile());
        assertEquals(0, helper.getActiveProfiles().length);
        assertFalse(helper.isDev());
        assertFalse(helper.isTest());
        assertFalse(helper.isProd());
        assertFalse(helper.isDaily());
        assertFalse(helper.isPre());
    }

    @Test
    void shouldReturnEmptyApplicationNameWhenNotConfigured() {
        MockEnvironment environment = new MockEnvironment();
        EnvironmentHelper helper = new EnvironmentHelper(environment);

        assertEquals("", helper.getApplicationName());
    }

    @Test
    void shouldReturnApplicationName() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "frame-me-tester");
        EnvironmentHelper helper = new EnvironmentHelper(environment);

        assertEquals("frame-me-tester", helper.getApplicationName());
    }

    @Test
    void shouldDetectDevProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        EnvironmentHelper helper = new EnvironmentHelper(environment);

        assertEquals("dev", helper.getActiveProfile());
        assertArrayEquals(new String[]{"dev"}, helper.getActiveProfiles());
        assertTrue(helper.isDev());
        assertFalse(helper.isTest());
        assertFalse(helper.isProd());
        assertFalse(helper.isDaily());
        assertFalse(helper.isPre());
        assertTrue(helper.isProfileActive("dev"));
        assertFalse(helper.isProfileActive("prod"));
    }

    @Test
    void shouldDetectDailyProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("daily");
        EnvironmentHelper helper = new EnvironmentHelper(environment);

        assertEquals("daily", helper.getActiveProfile());
        assertTrue(helper.isDaily());
        assertFalse(helper.isDev());
        assertFalse(helper.isTest());
        assertFalse(helper.isPre());
        assertFalse(helper.isProd());
    }

    @Test
    void shouldDetectPreProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("pre");
        EnvironmentHelper helper = new EnvironmentHelper(environment);

        assertEquals("pre", helper.getActiveProfile());
        assertTrue(helper.isPre());
        assertFalse(helper.isDev());
        assertFalse(helper.isTest());
        assertFalse(helper.isDaily());
        assertFalse(helper.isProd());
    }

    @Test
    void shouldDetectMultipleProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test", "mysql");
        EnvironmentHelper helper = new EnvironmentHelper(environment);

        assertEquals("test", helper.getActiveProfile());
        assertArrayEquals(new String[]{"test", "mysql"}, helper.getActiveProfiles());
        assertTrue(helper.isTest());
        assertTrue(helper.isProfileActive("mysql"));
        assertFalse(helper.isDev());
        assertFalse(helper.isProd());
    }

    @Test
    void shouldIgnoreEmptyProfileName() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        EnvironmentHelper helper = new EnvironmentHelper(environment);

        assertFalse(helper.isProfileActive(""));
        assertFalse(helper.isProfileActive(null));
    }
}
