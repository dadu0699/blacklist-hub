package com.blacklisthub.entity;

/**
 * Represents the type of Indicator of Compromise (IoC).
 * This maps to the ioc_type ENUM in the database.
 */
public enum IocType {
    IP,
    HASH,
    DOMAIN,
    URL
}